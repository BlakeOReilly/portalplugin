import os, sys, json, time, shlex, tempfile, subprocess, re, textwrap, difflib
from pathlib import Path
from datetime import datetime
from openai import OpenAI

# ===== Load config =====
ROOT = Path(__file__).resolve().parent.parent
CFG = json.loads(Path(__file__).with_name("config.json").read_text(encoding="utf-8"))

WATCH_DIR = Path(CFG["watch_dir"])
MODEL = CFG.get("model", "gpt-5-mini")
FALLBACK_MODEL = CFG.get("fallback_model", "gpt-5")
ALLOWLIST = set(ext.lower() for ext in CFG.get("allowlist_extensions", [".java"]))
TEST_CMD = CFG.get("test_cmd", ["cmd", "/c", ".\\gradlew.bat", "build"])
MAX_CONTEXT_BYTES = int(CFG.get("max_context_bytes", 350000))
CONTEXT_MODE = CFG.get("context_mode", "diff")
MAX_FIX_ATTEMPTS = int(CFG.get("max_fix_attempts", 3))
MAX_PATCH_RETRIES = int(CFG.get("max_patch_retries", 1))
INITIAL_BUILD = bool(CFG.get("initial_build", True))
EXTRA_CONTEXT_FILES = CFG.get("extra_context_files", [])
EDIT_MODE = CFG.get("edit_mode", "ops")  # "ops" recommended; "patch" optional
AUTO_CLEAN = bool(CFG.get("auto_cleanup_conflicts", True))
MAX_MODEL_CALLS = int(CFG.get("max_model_calls_per_run", 6))

AUTO_DEPLOY = CFG.get("auto_deploy", {})
DEPLOY_ENABLED = bool(AUTO_DEPLOY.get("enabled", False))
PLUGINS_DIR = Path(AUTO_DEPLOY.get("plugins_dir", ""))
JAR_GLOB = AUTO_DEPLOY.get("jar_glob", "build\\libs\\*.jar")
RESTART_MODE = AUTO_DEPLOY.get("restart_mode", "none")
SERVER_DIR = Path(AUTO_DEPLOY.get("server_dir", ""))
START_CMD = AUTO_DEPLOY.get("start_cmd", [])

API_KEY = os.environ.get("OPENAI_API_KEY")
if not API_KEY:
    print("ERROR: OPENAI_API_KEY not set.")
    sys.exit(1)

client = OpenAI(api_key=API_KEY)
_model_calls = 0  # guardrail

# ===== Utilities =====
def _format_cmd_for_print(cmd):
    if isinstance(cmd, str):
        return cmd
    return " ".join(shlex.quote(str(x)) for x in cmd)

def run(cmd, cwd=WATCH_DIR):
    """Windows-friendly runner:
    - Strings: shell=True
    - Lists: .bat/.cmd and bare names via cmd /c
    """
    try:
        if isinstance(cmd, str):
            return subprocess.run(cmd, cwd=str(cwd), capture_output=True, text=True, shell=True)
        if os.name == "nt":
            exe = cmd[0]
            exe_lower = exe.lower()
            needs_cmd = False
            if exe_lower.endswith(".bat") or exe_lower.endswith(".cmd"):
                needs_cmd = True
            elif (not os.path.isabs(exe)) and (("." not in os.path.basename(exe))):
                needs_cmd = True
            if needs_cmd:
                return subprocess.run(["cmd", "/c"] + cmd, cwd=str(cwd), capture_output=True, text=True, shell=False)
        return subprocess.run(cmd, cwd=str(cwd), capture_output=True, text=True, shell=False)
    except FileNotFoundError as e:
        msg = (
            f"\n[run] FileNotFoundError launching command.\n"
            f"  CWD: {cwd}\n"
            f"  CMD: {_format_cmd_for_print(cmd)}\n"
            f"  Hint: ensure gradlew.bat exists or use system Gradle in config.json.\n"
        )
        raise FileNotFoundError(msg) from e

def git(args):
    return run(["git"] + args, cwd=WATCH_DIR)

def ensure_repo_clean_enough():
    if git(["rev-parse","--is-inside-work-tree"]).returncode != 0:
        print("ERROR: Not a git repository. Run `git init` in the project root first.")
        sys.exit(1)
    git(["add","."])
    if git(["status","--porcelain"]).stdout.strip():
        git(["commit","-m","checkpoint before AI change"])

def list_repo_files():
    out = git(["ls-files"]).stdout.splitlines()
    return [WATCH_DIR / p for p in out]

def collect_diff_context():
    status = git(["status","--porcelain"]).stdout
    diff = git(["diff","HEAD"]).stdout
    text = f"STATUS:\n{status}\n\nDIFF_vs_HEAD:\n{diff}\n"
    return text[:MAX_CONTEXT_BYTES]

def collect_full_context():
    files = list_repo_files()
    buf, total = [], 0
    for f in files:
        if f.suffix.lower() in ALLOWLIST and f.is_file():
            try:
                s = f.read_text(encoding="utf-8", errors="replace")
            except Exception:
                continue
            header = f"\n\n===== FILE: {f.relative_to(WATCH_DIR)} =====\n"
            chunk = header + s
            if total + len(chunk) > MAX_CONTEXT_BYTES:
                break
            buf.append(chunk)
            total += len(chunk)
    return "".join(buf)

def resolve_test_cmd():
    if isinstance(TEST_CMD, list) and TEST_CMD:
        first = TEST_CMD[0].lower()
        if "gradlew" in first:
            gradlew = WATCH_DIR / "gradlew.bat"
            gradlew_sh = WATCH_DIR / "gradlew"
            if not gradlew.exists() and not gradlew_sh.exists():
                print("gradlew wrapper not found; falling back to system Gradle on PATH.")
                return ["cmd", "/c", "gradle", "build"] if os.name == "nt" else ["gradle", "build"]
        return TEST_CMD
    gradlew = WATCH_DIR / "gradlew.bat"
    gradlew_sh = WATCH_DIR / "gradlew"
    if gradlew.exists() or gradlew_sh.exists():
        return ["cmd", "/c", ".\\gradlew.bat", "build"] if os.name == "nt" else ["./gradlew", "build"]
    return ["cmd", "/c", "gradle", "build"] if os.name == "nt" else ["gradle", "build"]

def run_tests():
    cmd = resolve_test_cmd()
    print("Running build/tests...")
    print(f"[build] CWD: {WATCH_DIR}")
    print(f"[build] CMD: {_format_cmd_for_print(cmd)}")
    return run(cmd)

def newest_jar():
    pattern_path = (WATCH_DIR / JAR_GLOB) if not os.path.isabs(JAR_GLOB) else Path(JAR_GLOB)
    jars = sorted(pattern_path.parent.glob(pattern_path.name), key=os.path.getmtime, reverse=True)
    return jars[0] if jars else None

def kill_server_if_configured():
    if RESTART_MODE == "none":
        return True
    if RESTART_MODE == "kill-java":
        r = run(['taskkill','/F','/IM','java.exe'])
        return r.returncode == 0
    return False

def start_server_if_configured():
    if RESTART_MODE == "none":
        return True
    if START_CMD:
        r = run(START_CMD, cwd=SERVER_DIR)
        return r.returncode == 0
    return False

def deploy_and_restart_if_enabled():
    if not DEPLOY_ENABLED:
        return
    jar = newest_jar()
    if not jar:
        print("No built JAR found for deployment.")
        return
    PLUGINS_DIR.mkdir(parents=True, exist_ok=True)
    import shutil
    dst = PLUGINS_DIR / jar.name
    print(f"Copying {jar} -> {dst}")
    shutil.copy2(jar, dst)
    if RESTART_MODE != "none":
        print("Restarting server as configured...")
        ok1 = kill_server_if_configured()
        ok2 = start_server_if_configured()
        print(f"Server restart: kill_ok={ok1}, start_ok={ok2}")

# ===== Conflict auto-clean =====
def unmerged_files():
    out = git(["diff", "--name-only", "--diff-filter=U"]).stdout
    return [l.strip() for l in out.splitlines() if l.strip()]

def cleanup_conflicts():
    files = unmerged_files()
    if files:
        print(f"[git] Unmerged files detected: {files}")
        print("[git] Auto-cleanup: abort merge (if any), reset to HEAD, remove .rej files")
        git(["merge", "--abort"])
        git(["reset", "--hard", "HEAD"])
        try:
            for rf in WATCH_DIR.rglob("*.rej"):
                rf.unlink(missing_ok=True)
        except Exception:
            pass
        return True
    return False

# ===== Context builder (includes extra files) =====
def project_context_blob():
    """Builds context for the model, always appending EXTRA_CONTEXT_FILES."""
    base = collect_full_context() if CONTEXT_MODE == "full" else collect_diff_context()
    parts = [base]
    for rel in EXTRA_CONTEXT_FILES:
        p = (WATCH_DIR / rel)
        if p.exists() and p.is_file():
            try:
                content = p.read_text(encoding="utf-8", errors="replace")
                parts.append(f"\n\n===== FILE (extra): {rel} =====\n{content}")
            except Exception:
                pass
    blob = "".join(parts)
    return blob[:MAX_CONTEXT_BYTES]

# ===== Diff helpers for pretty output =====
def _preview_diff(path: Path, before: str, after: str, n=3) -> str:
    a = before.splitlines(keepends=False)
    b = after.splitlines(keepends=False)
    diff = difflib.unified_diff(a, b, fromfile=f"a/{path}", tofile=f"b/{path}", n=n)
    lines = list(diff)
    return "\n".join(lines[:200])

# ===== Model wrappers with guardrail =====
def _call_model(*, system: str, user: str, effort: str, model_name: str):
    global _model_calls
    if _model_calls >= MAX_MODEL_CALLS:
        raise RuntimeError(f"Model call limit reached for this run ({MAX_MODEL_CALLS}).")
    resp = client.responses.create(
        model=model_name,
        input=[{"role":"system","content":system}, {"role":"user","content":user}],
        reasoning={"effort": effort}
    )
    _model_calls += 1
    return resp.output_text, getattr(resp, "model", model_name)

# ===== OPS MODE (recommended) =====
# The model returns JSON like:
# {"edits":[ {"path":"src/main/resources/plugin.yml","action":"replace","content":"<full file content>"},
#            {"path":"src/main/java/..../Foo.java","action":"replace","content":"..."} ]}
OPS_SCHEMA_HELP = """Return ONLY valid minified JSON, no markdown, no comments.
Schema:
{"edits":[{"path":"<relative file path>","action":"replace","content":"< ENTIRE final file content >"}]}
Rules:
- Only use paths within the repository root.
- Only edit files with these extensions: %ALLOWLIST%.
- Always provide the FULL final content for each file you touch.
- For YAML/props/resources, write the complete, coherent file.
- Do not include diff formats, backticks, or explanations.
"""

def _ops_change_prompt(change_instructions: str) -> tuple[str,str]:
    allow = ", ".join(sorted(ALLOWLIST))
    system = (
        "You are a senior Java engineer working on a Bukkit/Paper/Velocity plugin.\n"
        "When asked to make a change, output a JSON list of file replacements (FULL file contents), not a diff."
    )
    user = f"""Repository root: {WATCH_DIR}
Allowed file extensions: {allow}

High-level change request:
---
{change_instructions}
---

Project context (truncated by byte cap):
{project_context_blob()}

{OPS_SCHEMA_HELP.replace("%ALLOWLIST%", allow)}
"""
    return system, user

def _ops_fix_prompt(failure_text: str) -> tuple[str,str]:
    allow = ", ".join(sorted(ALLOWLIST))
    system = (
        "You are a senior Java engineer. Fix the build/test failure by returning JSON file replacements "
        "(FULL file contents), not diffs."
    )
    user = f"""Repository root: {WATCH_DIR}
Allowed file extensions: {allow}

Latest build/test failure (stdout+stderr):
---
{failure_text}
---

Project context (truncated by byte cap):
{project_context_blob()}

{OPS_SCHEMA_HELP.replace("%ALLOWLIST%", allow)}
"""
    return system, user

def _parse_ops_json(text: str):
    # Remove code fences if any, then parse JSON
    t = text.strip()
    if t.startswith("```"):
        # crude fence strip
        t = t.strip("`")
        nl = t.find("\n")
        if nl != -1:
            t = t[nl+1:]
    # Sometimes models wrap in <BEGIN_JSON>...</END_JSON> — strip if present
    b = t.find("{")
    e = t.rfind("}")
    if b == -1 or e == -1:
        raise ValueError("No JSON object detected in response.")
    t = t[b:e+1]
    data = json.loads(t)
    if not isinstance(data, dict) or "edits" not in data or not isinstance(data["edits"], list):
        raise ValueError("JSON missing 'edits' array.")
    return data

def _is_allowed_target(path_str: str) -> bool:
    try:
        p = (WATCH_DIR / path_str).resolve()
        # ensure inside repo
        if WATCH_DIR not in p.parents and p != WATCH_DIR:
            return False
        if not p.suffix:
            return False
        return p.suffix.lower() in ALLOWLIST
    except Exception:
        return False

def _apply_ops(data: dict) -> list[Path]:
    changed_paths = []
    for edit in data["edits"]:
        path = edit.get("path","").replace("/", os.sep)
        action = edit.get("action","").lower()
        content = edit.get("content", None)
        if action != "replace" or not content or not path:
            raise ValueError(f"Invalid edit entry: {edit}")
        if not _is_allowed_target(path):
            raise ValueError(f"Disallowed or unsafe path: {path}")
        abs_path = (WATCH_DIR / path)
        abs_path.parent.mkdir(parents=True, exist_ok=True)
        before = ""
        if abs_path.exists():
            try:
                before = abs_path.read_text(encoding="utf-8", errors="replace")
            except Exception:
                before = ""
        abs_path.write_text(content, encoding="utf-8")
        changed_paths.append(abs_path)

        # Show mini preview diff to console
        preview = _preview_diff(abs_path.relative_to(WATCH_DIR), before, content)
        if preview:
            print("---- preview diff ----")
            print(preview)
    return changed_paths

def ask_for_change_ops(change_instructions: str, model_name: str):
    system, user = _ops_change_prompt(change_instructions)
    return _call_model(system=system, user=user, effort="high", model_name=model_name)

def ask_for_fix_ops(failure_text: str, model_name: str):
    system, user = _ops_fix_prompt(failure_text)
    return _call_model(system=system, user=user, effort="medium", model_name=model_name)

# ===== PATCH MODE (optional; kept for completeness) =====
FENCE_RE = re.compile(r"^```(?:diff|patch)?\s*$", re.IGNORECASE)
def sanitize_patch_text(text: str) -> str:
    t = text.strip()
    lines = t.splitlines()
    if lines and FENCE_RE.match(lines[0]):
        for i in range(1, len(lines)):
            if lines[i].strip() == "```":
                t = "\n".join(lines[1:i])
                break
    b = t.find("<BEGIN_PATCH>"); e = t.find("<END_PATCH>")
    if b != -1 and e != -1 and e > b:
        t = t[b+len("<BEGIN_PATCH>"):e].strip()
    idx = t.find("diff --git "); alt = t.find("\n--- "); alt2 = t.find("--- ")
    starts = [i for i in [idx, alt if alt != -1 else None, alt2] if i is not None and i != -1]
    if starts:
        t = t[min(starts):].lstrip()
    return t

def _normalize_line_endings(s: str) -> str:
    return s.replace("\r\n", "\n").replace("\r", "\n")

def _write_temp_patch(text: str) -> str:
    with tempfile.NamedTemporaryFile("w", delete=False, suffix=".patch", encoding="utf-8") as f:
        f.write(text)
        return f.name

def _try_git_apply_variants(patch_path: str):
    ap = git(["apply", "--ignore-space-change", "--whitespace=fix", "--index", patch_path])
    if ap.returncode == 0:
        return True, ""
    ap3 = git(["apply", "-3", "--whitespace=fix", "--index", patch_path])
    if ap3.returncode == 0:
        return True, ""
    apr = git(["apply", "--whitespace=fix", "--reject", patch_path])
    if apr.returncode == 0:
        return True, "(applied with rejects; see .rej files)"
    return False, apr.stderr or ap3.stderr or ap.stderr

def apply_patch_text(patch_text: str) -> tuple[bool,str,str|None]:
    if AUTO_CLEAN:
        cleanup_conflicts()
    text = sanitize_patch_text(patch_text)
    if ("--- " not in text or "+++ " not in text) and ("diff --git " not in text):
        return False, "No diff headers detected after sanitization.", None
    text = _normalize_line_endings(text)
    # strip trailing spaces per line
    clean = "\n".join(ln.rstrip() for ln in text.splitlines()) + "\n"
    patch_path = _write_temp_patch(clean)
    ok, err = _try_git_apply_variants(patch_path)
    if not ok:
        print("❌ Patch apply failed.")
        print(f"[patch] Saved to: {patch_path}")
        if err:
            print("[git apply stderr]")
            print(err.strip()[:2000])
        return False, err or "git apply failed", patch_path
    cm = git(["commit","-m", f"AI patch {datetime.now().isoformat(timespec='seconds')}"])
    if cm.returncode != 0:
        print("❌ Commit failed after patch apply.")
        print(cm.stderr)
        return False, cm.stderr, patch_path
    changed = git(["diff","--name-only","HEAD~1","HEAD"]).stdout.strip()
    print("✅ Patch applied and committed. Files changed:")
    print(changed if changed else "(no files?)")
    return True, "applied", patch_path

# ===== Request helpers =====
def request_ops_with_fallback(change_or_fix: str, payload: str) -> dict|None:
    """Return parsed JSON edits from primary, then fallback model. None if both fail."""
    for strict in (False, True):
        for model_name in (MODEL, FALLBACK_MODEL):
            try:
                if change_or_fix == "change":
                    text, used = ask_for_change_ops(payload, model_name)
                else:
                    text, used = ask_for_fix_ops(payload, model_name)
                try:
                    data = _parse_ops_json(text)
                    print(f"Model used: {used}")
                    return data
                except Exception as pe:
                    if strict:
                        continue
                    # second try: ask the same model again but remind schema
                    # (we simply loop to next iteration)
            except Exception as e:
                continue
    return None

# ===== Interactive flow =====
def read_multiline_prompt():
    print("\nDescribe the change you want to make to the server plugin.")
    print("Finish with a blank line (press Enter twice):")
    lines = []
    while True:
        try:
            line = input()
        except EOFError:
            break
        if line.strip() == "" and len(lines) > 0:
            break
        lines.append(line)
    return "\n".join(lines).strip()

def apply_ops_from_model(change_instructions: str) -> bool:
    data = request_ops_with_fallback("change", change_instructions)
    if not data:
        print("Failed to get valid JSON edits from the model.")
        return False
    try:
        changed_paths = _apply_ops(data)
    except Exception as e:
        print(f"❌ Failed to apply ops: {e}")
        return False
    git(["add","."])
    cm = git(["commit","-m", f"AI ops {datetime.now().isoformat(timespec='seconds')}"])
    if cm.returncode != 0:
        print("❌ Commit failed:", cm.stderr)
        return False
    print("✅ Files written and committed:")
    for p in changed_paths:
        print(" -", p.relative_to(WATCH_DIR))
    return True

def fix_with_ops(failure_text: str) -> bool:
    data = request_ops_with_fallback("fix", failure_text)
    if not data:
        print("Failed to get valid JSON edits for fix.")
        return False
    try:
        changed_paths = _apply_ops(data)
    except Exception as e:
        print(f"❌ Failed to apply fix ops: {e}")
        return False
    git(["add","."])
    cm = git(["commit","-m", f"AI fix ops {datetime.now().isoformat(timespec='seconds')}"])
    if cm.returncode != 0:
        print("❌ Commit failed:", cm.stderr)
        return False
    print("✅ Fix files written and committed:")
    for p in changed_paths:
        print(" -", p.relative_to(WATCH_DIR))
    return True

def obtain_and_apply_change(change_instructions: str) -> bool:
    if EDIT_MODE.lower() == "ops":
        return apply_ops_from_model(change_instructions)
    # patch mode fallback if explicitly requested
    print("Requesting change patch from model:", MODEL)
    for attempt in range(1, MAX_PATCH_RETRIES + 2):
        try:
            # Build a minimal patch prompt (omit here to keep response short)
            system = "Return ONLY a valid git unified diff. No explanations."
            user = f"{project_context_blob()}\nChange:\n{change_instructions}"
            text, used = _call_model(system=system, user=user, effort="high", model_name=MODEL)
            ok, msg, _ = apply_patch_text(text)
            if ok:
                return True
            print(f"[patch] Apply failed on attempt {attempt}: {msg}")
        except Exception as e:
            print(f"[patch] Attempt {attempt} error: {e}")
    return False

def main():
    ensure_repo_clean_enough()
    if AUTO_CLEAN:
        cleanup_conflicts()

    change_instructions = read_multiline_prompt()
    if not change_instructions:
        print("No change instructions provided. Exiting.")
        return

    if INITIAL_BUILD:
        base = run_tests()
        if base.returncode != 0:
            print("Baseline build failed. The agent may address baseline failures during fix loop.")
            print("---- build stdout ----")
            print(base.stdout)
            print("---- build stderr ----")
            print(base.stderr)

    print(f"\nEdit mode: {EDIT_MODE}")
    print("Applying requested change...")
    if not obtain_and_apply_change(change_instructions):
        print("Failed to apply the requested change.")
        sys.exit(2)

    attempt = 0
    while True:
        result = run_tests()
        if result.returncode == 0:
            print("✅ Build/tests green.")
            deploy_and_restart_if_enabled()
            break

        attempt += 1
        if attempt > MAX_FIX_ATTEMPTS:
            print("Reached maximum auto-fix attempts. Leaving last commit in place for review.")
            break

        print(f"\n❌ Build/tests failing (attempt {attempt}/{MAX_FIX_ATTEMPTS}). Attempting automatic fix...")
        print("---- build stdout ----")
        print(result.stdout)
        print("---- build stderr ----")
        print(result.stderr)

        ok = False
        if EDIT_MODE.lower() == "ops":
            ok = fix_with_ops(result.stdout + "\n" + result.stderr)
        else:
            # patch-mode fix (kept minimal)
            system = "Return ONLY a valid git unified diff that fixes the failure. No explanations."
            user = f"{project_context_blob()}\nFailure:\n{result.stdout}\n{result.stderr}"
            try:
                text, used = _call_model(system=system, user=user, effort="medium", model_name=MODEL)
                ok, msg, _ = apply_patch_text(text)
                if not ok:
                    print("Patch fix failed:", msg)
            except Exception as e:
                print("Patch fix error:", e)

        if not ok:
            print("Could not auto-fix on this attempt; continuing if attempts remain...")

if __name__ == "__main__":
    main()
