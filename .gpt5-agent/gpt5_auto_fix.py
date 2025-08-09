import os, sys, json, time, glob, shlex, tempfile, subprocess, re, textwrap
from pathlib import Path
from datetime import datetime
from openai import OpenAI

# ===== Load config =====
ROOT = Path(__file__).resolve().parent.parent
CFG = json.loads(Path(__file__).with_name("config.json").read_text(encoding="utf-8"))

WATCH_DIR = Path(CFG["watch_dir"])
MODEL = CFG.get("model", "gpt-5-mini")
FALLBACK_MODEL = CFG.get("fallback_model", "gpt-5")
ALLOWLIST = set(CFG.get("allowlist_extensions", [".java"]))
TEST_CMD = CFG.get("test_cmd", ["cmd", "/c", ".\\gradlew.bat", "build"])  # override in config.json if using system Gradle
MAX_CONTEXT_BYTES = int(CFG.get("max_context_bytes", 350000))
CONTEXT_MODE = CFG.get("context_mode", "diff")
MAX_FIX_ATTEMPTS = int(CFG.get("max_fix_attempts", 3))
INITIAL_BUILD = bool(CFG.get("initial_build", True))
MAX_PATCH_RETRIES = int(CFG.get("max_patch_retries", 2))
EXTRA_CONTEXT_FILES = CFG.get("extra_context_files", [])

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

# ===== Patch sanitation & diagnostics =====
FENCE_RE = re.compile(r"^```(?:diff|patch)?\s*$", re.IGNORECASE)

def sanitize_patch_text(text: str) -> str:
    t = text.strip()
    lines = t.splitlines()
    if lines and FENCE_RE.match(lines[0]):
        for i in range(1, len(lines)):
            if lines[i].strip() == "```":
                t = "\n".join(lines[1:i])
                break
    begin = t.find("<BEGIN_PATCH>")
    end = t.find("<END_PATCH>")
    if begin != -1 and end != -1 and end > begin:
        t = t[begin + len("<BEGIN_PATCH>"):end].strip()
    idx_diff = t.find("diff --git ")
    idx_unified = t.find("\n--- ")
    idx_unified_start = t.find("--- ")
    starts = [i for i in [idx_diff, idx_unified if idx_unified != -1 else None, idx_unified_start] if i is not None and i != -1]
    if starts:
        start_at = min(starts)
        t = t[start_at:].lstrip()
    return t

def _normalize_line_endings(s: str) -> str:
    return s.replace("\r\n", "\n").replace("\r", "\n")

def _clean_patch_whitespace(raw_text: str) -> str:
    clean_lines = [line.rstrip() for line in raw_text.splitlines()]
    return "\n".join(clean_lines) + "\n"

def _looks_like_diff(text: str) -> bool:
    return ("diff --git " in text) or (("--- " in text) and ("+++ " in text))

def _print_patch_failure(patch_path: str, stderr: str, note: str):
    print("❌ Patch apply failed.")
    print(f"[patch] Saved to: {patch_path}")
    if stderr:
        print("[git apply stderr]")
        print(stderr.strip()[:2000])
    if note:
        print("[note]")
        print(textwrap.shorten(note, width=1000, placeholder=" …"))
    rej_files = list(WATCH_DIR.rglob("*.rej"))
    if rej_files:
        print("[rejects] The following .rej files were created:")
        for rf in rej_files[:10]:
            print(" -", rf)
        try:
            sample = rej_files[0].read_text(encoding="utf-8", errors="replace")
            print("[reject sample]")
            print(sample[:1000])
        except Exception:
            pass

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

def apply_patch(patch_text):
    """Returns (ok: bool, msg: str, patch_path: str|None) and prints changed files on success."""
    text = sanitize_patch_text(patch_text)
    if not _looks_like_diff(text):
        return False, "No diff headers detected after sanitization.", None
    text = _normalize_line_endings(text)
    cleaned_text = _clean_patch_whitespace(text)
    patch_path = _write_temp_patch(cleaned_text)

    ok, err = _try_git_apply_variants(patch_path)
    if not ok:
        _print_patch_failure(patch_path, err, "")
        return False, err or "git apply failed", patch_path

    cm = git(["commit","-m", f"AI patch {datetime.now().isoformat(timespec='seconds')}"])
    if cm.returncode != 0:
        _print_patch_failure(patch_path, cm.stderr, "")
        return False, cm.stderr, patch_path

    changed = git(["diff", "--name-only", "HEAD~1", "HEAD"]).stdout.strip()
    print("✅ Patch applied and committed. Files changed:")
    print(changed if changed else "(no files?)")
    return True, "applied", patch_path

# ===== Context builder (UPDATED) =====
def project_context_blob():
    """Builds the context sent to the model.
    - Uses full workspace or just diff, depending on CONTEXT_MODE
    - Always appends any EXTRA_CONTEXT_FILES (e.g., plugin.yml) to avoid hunk mismatches
    - Respects MAX_CONTEXT_BYTES cap
    """
    base = collect_full_context() if CONTEXT_MODE == "full" else collect_diff_context()
    parts = [base]

    for rel in EXTRA_CONTEXT_FILES:
        p = (WATCH_DIR / rel)
        if p.exists() and p.is_file():
            try:
                content = p.read_text(encoding="utf-8", errors="replace")
                parts.append(f"\n\n===== FILE (extra): {rel} =====\n{content}")
            except Exception:
                # ignore unreadable files silently
                pass

    blob = "".join(parts)
    return blob[:MAX_CONTEXT_BYTES]

# ===== Prompt builders & calls =====
def _change_prompt(strict: bool, allow: str, change_instructions: str) -> tuple[str, str]:
    system = (
        "You are a senior Java engineer working on a Bukkit/Paper/Velocity plugin. "
        "Return ONLY a valid git patch. Avoid trailing whitespace. Use LF line endings. "
        "Ensure `git apply` succeeds on Windows with CRLF working trees."
    )
    fmt_help = (
        "Output ONLY the patch, starting with 'diff --git a/... b/...' and correct unified hunks '@@ -x,y +x,y @@'."
        if strict else
        "Output ONLY a unified diff patch (---/+++ with @@ hunks) or 'diff --git' format."
    )
    markers = "\n<BEGIN_PATCH>\n{patch}\n<END_PATCH>\n" if strict else "\n{patch}\n"
    user = f"""Repository root: {WATCH_DIR}
Allowed file extensions: {allow}

High-level change request:
---
{change_instructions}
---

Project context (truncated by byte cap):
{project_context_blob()}

Format:
- {fmt_help}
- Do not include yaml, markdown, or explanations.
- Use paths relative to the repo root.
{markers}""".replace("{patch}", "(patch here)")
    return system, user

def _fix_prompt(strict: bool, allow: str, failure_text: str) -> tuple[str, str]:
    system = (
        "You are a senior Java engineer. "
        "Return ONLY a valid git patch that fixes the build/test failure. "
        "Avoid trailing whitespace. Use LF line endings. Ensure `git apply` succeeds."
    )
    fmt_help = (
        "Output ONLY the patch, starting with 'diff --git a/... b/...' and correct unified hunks."
        if strict else
        "Output ONLY a unified diff (---/+++ with @@ hunks) or 'diff --git' format."
    )
    markers = "\n<BEGIN_PATCH>\n{patch}\n<END_PATCH>\n" if strict else "\n{patch}\n"
    user = f"""Repository root: {WATCH_DIR}
Allowed file extensions: {allow}

Latest build/test failure:
---
{failure_text}
---

Project context (truncated by byte cap):
{project_context_blob()}

Format:
- {fmt_help}
- Do not include yaml, markdown, or explanations.
- Use paths relative to the repo root.
{markers}""".replace("{patch}", "(patch here)")
    return system, user

def ask_for_change_patch(model_name, change_instructions, strict=False):
    allow = ", ".join(sorted(ALLOWLIST))
    system, user = _change_prompt(strict, allow, change_instructions)
    resp = client.responses.create(
        model=model_name,
        input=[{"role":"system","content":system},
               {"role":"user","content":user}],
        reasoning={"effort":"high"}
    )
    return resp.output_text, getattr(resp, "model", model_name)

def ask_for_fix_patch(model_name, failure_text, strict=False):
    allow = ", ".join(sorted(ALLOWLIST))
    system, user = _fix_prompt(strict, allow, failure_text)
    resp = client.responses.create(
        model=model_name,
        input=[{"role":"system","content":system},
               {"role":"user","content":user}],
        reasoning={"effort":"medium"}
    )
    return resp.output_text, getattr(resp, "model", model_name)

def request_with_retries(request_fn, model_names, *args):
    text, used = request_fn(model_names[0], *args, False)
    if text and _looks_like_diff(sanitize_patch_text(text)):
        return text, used
    text, used = request_fn(model_names[0], *args, True)
    if text and _looks_like_diff(sanitize_patch_text(text)):
        return text, used
    if len(model_names) > 1 and model_names[1]:
        text, used = request_fn(model_names[1], *args, True)
        if text and _looks_like_diff(sanitize_patch_text(text)):
            return text, used
    return None, None

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

def obtain_and_apply_patch(request_fn, model_order, *args):
    for cycle in range(1, MAX_PATCH_RETRIES + 2):  # attempts
        patch, used_model = request_with_retries(request_fn, model_order, *args)
        if not patch:
            print(f"[patch] Could not obtain a valid patch on attempt {cycle}.")
            continue
        print(f"Model used: {used_model}")
        ok, msg, _ = apply_patch(patch)
        if ok:
            return True
        print(f"[patch] Apply failed on attempt {cycle}: {msg}")
    return False

def main():
    ensure_repo_clean_enough()

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

    print(f"\nRequesting change patch from model: {MODEL}")
    if not obtain_and_apply_patch(ask_for_change_patch, [MODEL, FALLBACK_MODEL], change_instructions):
        print("Failed to apply the initial change after retries.")
        sys.exit(2)

    attempt = 0
    current_order = [MODEL, FALLBACK_MODEL]
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

        print(f"\n❌ Build/tests failing (attempt {attempt}/{MAX_FIX_ATTEMPTS}). Requesting fix patch...")
        print("---- build stdout ----")
        print(result.stdout)
        print("---- build stderr ----")
        print(result.stderr)

        if obtain_and_apply_patch(ask_for_fix_patch, current_order, result.stdout + "\n" + result.stderr):
            continue
        current_order = [current_order[1], current_order[0]]

if __name__ == "__main__":
    main()
