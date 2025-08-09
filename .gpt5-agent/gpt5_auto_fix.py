import os, sys, json, time, glob, shlex, tempfile, subprocess
from pathlib import Path
from datetime import datetime
from unidiff import PatchSet
from openai import OpenAI

# ===== Load config =====
ROOT = Path(__file__).resolve().parent.parent
CFG = json.loads(Path(__file__).with_name("config.json").read_text(encoding="utf-8"))

WATCH_DIR = Path(CFG["watch_dir"])
MODEL = CFG.get("model", "gpt-5-mini")
FALLBACK_MODEL = CFG.get("fallback_model", "gpt-5")
ALLOWLIST = set(CFG.get("allowlist_extensions", [".java"]))
TEST_CMD = CFG.get("test_cmd", ["gradlew.bat", "build"])
MAX_CONTEXT_BYTES = int(CFG.get("max_context_bytes", 350000))
CONTEXT_MODE = CFG.get("context_mode", "diff")
MAX_FIX_ATTEMPTS = int(CFG.get("max_fix_attempts", 3))
INITIAL_BUILD = bool(CFG.get("initial_build", True))

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
    """
    Executes a command with good Windows support:
    - Strings use shell=True so PATH/built-ins work.
    - Lists: on Windows, .bat/.cmd and bare names are run via `cmd /c`.
    Provides clear diagnostics on FileNotFoundError.
    """
    try:
        if isinstance(cmd, str):
            return subprocess.run(
                cmd,
                cwd=str(cwd),
                capture_output=True,
                text=True,
                shell=True
            )

        # List form
        if os.name == "nt":
            exe = cmd[0]
            exe_lower = exe.lower()
            needs_cmd = False

            # If it's a batch/cmd script
            if exe_lower.endswith(".bat") or exe_lower.endswith(".cmd"):
                needs_cmd = True
            # If it's a bare name (no path, no extension), let cmd resolve (e.g., "gradle", "mvn")
            elif (not os.path.isabs(exe)) and (("." not in os.path.basename(exe))):
                needs_cmd = True

            if needs_cmd:
                return subprocess.run(
                    ["cmd", "/c"] + cmd,
                    cwd=str(cwd),
                    capture_output=True,
                    text=True,
                    shell=False
                )

        # POSIX or direct executable path
        return subprocess.run(
            cmd,
            cwd=str(cwd),
            capture_output=True,
            text=True,
            shell=False
        )

    except FileNotFoundError as e:
        msg = (
            f"\n[run] FileNotFoundError launching command.\n"
            f"  CWD: {cwd}\n"
            f"  CMD: {_format_cmd_for_print(cmd)}\n"
            f"  Hint: If you use Gradle wrapper, ensure gradlew.bat exists.\n"
            f"        Otherwise set test_cmd to ['cmd','/c','gradle','build'] in config.json.\n"
        )
        raise FileNotFoundError(msg) from e

def git(args):
    return run(["git"] + args, cwd=WATCH_DIR)

def ensure_repo_clean_enough():
    if git(["rev-parse","--is-inside-work-tree"]).returncode != 0:
        print("ERROR: Not a git repository. Run `git init` in the project root first.")
        sys.exit(1)
    # Stage anything untracked so the model sees a stable diff context
    git(["add","."])
    # Commit if there are changes
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

def run_tests():
    print("Running build/tests...")
    # If TEST_CMD is a list and the first element is "gradlew.bat" or "gradlew", prefer running via cmd /c on Windows
    # but run() already handles that; just call run(TEST_CMD).
    res = run(TEST_CMD)
    return res

def newest_jar():
    # Accept either absolute glob or relative to project root
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
        # 'start' returns quickly; that's fine
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

def sanitize_patch_text(text):
    t = text.strip()
    if t.startswith("```"):
        # Strip code fences if present
        t = t.strip("`")
        t = t.split("\n", 1)[1] if "\n" in t else ""
    return t

def apply_patch(patch_text):
    text = sanitize_patch_text(patch_text)
    # Validate looks like a diff
    if ("--- " not in text) or ("+++ " not in text):
        return False, "Model did not return a unified diff."
    try:
        PatchSet(text)
    except Exception as e:
        return False, f"Invalid patch format: {e}"
    with tempfile.NamedTemporaryFile("w", delete=False, suffix=".patch", encoding="utf-8") as f:
        f.write(text)
        patch_path = f.name
    ap = git(["apply","--index",patch_path])
    if ap.returncode != 0:
        return False, ap.stderr
    cm = git(["commit","-m", f"AI patch {datetime.now().isoformat(timespec='seconds')}"])
    if cm.returncode != 0:
        return False, cm.stderr
    return True, "applied"

# ===== Model prompts =====
def project_context_blob():
    return collect_full_context() if CONTEXT_MODE == "full" else collect_diff_context()

def ask_for_change_patch(model_name, change_instructions):
    allow = ", ".join(sorted(ALLOWLIST))
    system = (
        "You are a senior Java engineer working on a Bukkit/Paper/Velocity plugin. "
        "You will receive high-level change instructions. "
        "Return ONLY a valid git unified diff (no backticks, no commentary). "
        "Make minimal, compilable edits across any necessary files, but only within the allowed extensions."
    )
    user = f"""Repository root: {WATCH_DIR}
Allowed file extensions: {allow}

High-level change request:
---
{change_instructions}
---

Project context (truncated by byte cap):
{project_context_blob()}

Output format requirements:
- Strict unified diff with ---/+++ headers and @@ hunks.
- No prose explanations.
"""
    resp = client.responses.create(
        model=model_name,
        input=[{"role":"system","content":system},
               {"role":"user","content":user}],
        reasoning={"effort":"high"},
        temperature=0.1
    )
    return resp.output_text, getattr(resp, "model", model_name)

def ask_for_fix_patch(model_name, failure_text):
    allow = ", ".join(sorted(ALLOWLIST))
    system = (
        "You are a senior Java engineer. "
        "Given the current build/test failure, return ONLY a git unified diff that fixes the issue. "
        "No explanations. Keep changes minimal and compilable. Respect the allowlist."
    )
    user = f"""Repository root: {WATCH_DIR}
Allowed file extensions: {allow}

Latest build/test failure:
---
{failure_text}
---

Project context (truncated by byte cap):
{project_context_blob()}

Output: unified diff with ---/+++ and @@ hunks; no commentary.
"""
    resp = client.responses.create(
        model=model_name,
        input=[{"role":"system","content":system},
               {"role":"user","content":user}],
        reasoning={"effort":"medium"},
        temperature=0.1
    )
    return resp.output_text, getattr(resp, "model", model_name)

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

def main():
    ensure_repo_clean_enough()

    change_instructions = read_multiline_prompt()
    if not change_instructions:
        print("No change instructions provided. Exiting.")
        return

    # Initial optional build to capture baseline failures
    if INITIAL_BUILD:
        base = run_tests()
        if base.returncode != 0:
            print("Baseline build failed. The agent may address baseline failures during fix loop.")
            print("---- build stdout ----")
            print(base.stdout)
            print("---- build stderr ----")
            print(base.stderr)

    # Step 1: Implement the requested change
    print(f"\nRequesting change patch from model: {MODEL}")
    patch, used_model = ask_for_change_patch(MODEL, change_instructions)
    print(f"Model used: {used_model}")
    ok, msg = apply_patch(patch)
    if not ok and MODEL != FALLBACK_MODEL:
        print("Change patch failed to apply with mini model. Retrying with fallback model...")
        patch2, used_model2 = ask_for_change_patch(FALLBACK_MODEL, change_instructions)
        print(f"Model used: {used_model2}")
        ok, msg = apply_patch(patch2)

    if not ok:
        print("Failed to apply initial change patch:", msg)
        sys.exit(2)

    # Step 2: Build and auto-fix loop until green or attempts exhausted
    attempt = 0
    current_model = MODEL
    while True:
        result = run_tests()
        if result.returncode == 0:
            print("✅ Build/tests green.")
            deploy_and_restart_if_enabled()
            break

        attempt += 1
        print(f"\n❌ Build/tests failing (attempt {attempt}/{MAX_FIX_ATTEMPTS}). Requesting fix patch...")
        print("---- build stdout ----")
        print(result.stdout)
        print("---- build stderr ----")
        print(result.stderr)

        patch, used_model = ask_for_fix_patch(current_model, result.stdout + "\n" + result.stderr)
        print(f"Model used: {used_model}")
        ok, msg = apply_patch(patch)
        if not ok:
            if current_model != FALLBACK_MODEL:
                print("Patch apply failed with mini model. Escalating to fallback model and retrying...")
                current_model = FALLBACK_MODEL
                patch2, used_model2 = ask_for_fix_patch(current_model, result.stdout + "\n" + result.stderr)
                print(f"Model used: {used_model2}")
                ok, msg = apply_patch(patch2)
                if not ok:
                    print("Fallback patch also failed to apply:", msg)
                    break
            else:
                print("Patch apply failed with fallback model:", msg)
                break

        if attempt >= MAX_FIX_ATTEMPTS:
            print("Reached maximum auto-fix attempts. Leaving last commit in place for review.")
            break

if __name__ == "__main__":
    main()
