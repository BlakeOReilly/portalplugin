#!/usr/bin/env python3
import argparse, json, os, re, subprocess, sys, textwrap
from pathlib import Path

# pip install openai==1.*
from openai import OpenAI

MODEL = "gpt-5-2025-05-20"  # pin a dated snapshot when available

PROMPT = """You are a build-fix assistant.
Input includes:
- build.log: The failing build log.
- file index: A listing of project files (paths).
- selected source snippets.

Task:
1) Identify the minimal, safe changes to fix compilation/build errors.
2) Output a JSON array of edits. Each item:
   {
     "path": "relative/path/File.java",
     "find": "exact substring to search (short, stable)",
     "replace": "replacement text (full new code block or line)"
   }
Rules:
- Prefer small, surgical edits. Avoid mass refactors.
- Only include files that need changing.
- Ensure 'find' appears exactly once in the current file.
- If multiple changes in one file, put multiple objects for that file with distinct 'find's.
- If uncertain, return [].

Return ONLY the JSON—no prose.
"""

def shell(cmd, **kw):
    print("+", " ".join(cmd), file=sys.stderr)
    return subprocess.check_output(cmd, text=True, **kw)

def read_paths(root):
    paths = []
    for p in Path(root).rglob("*"):
        if p.is_file():
            # keep only likely source/build files
            if any(p.suffix == ext for ext in [".java", ".kt", ".gradle", ".kts", ".xml", ".properties", ".yml", ".yaml"]):
                if ".git" not in p.parts and "build" not in p.parts and ".gradle" not in p.parts:
                    paths.append(p)
    return paths

def sample_sources(paths, limit_bytes=180_000):
    # include small files; for big files include only headers to stay under token limits
    chunks = []
    total = 0
    for p in paths:
        try:
            data = p.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        head = data[:8000]  # first 8KB per file
        entry = f"\n--- FILE: {p}\n{head}"
        if total + len(entry) > limit_bytes:
            break
        chunks.append(entry)
        total += len(entry)
    return "".join(chunks)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--log", required=True)
    ap.add_argument("--root", default=".")
    args = ap.parse_args()

    build_log = Path(args.log).read_text(encoding="utf-8", errors="ignore")
    paths = read_paths(args.root)
    file_index = "\n".join(str(p) for p in paths)
    sources = sample_sources(paths)

    client = OpenAI(api_key=os.environ.get("OPENAI_API_KEY"))

    messages = [
        {"role":"system","content":PROMPT},
        {"role":"user","content":textwrap.dedent(f"""
            === build.log ===
            {build_log}

            === file index ===
            {file_index}

            === source samples ===
            {sources}
        """)}
    ]

    resp = client.responses.create(
        model=MODEL,
        input=messages
    )

    text = resp.output_text.strip()
    # Try to load JSON; if it fails, bail with no changes
    try:
        edits = json.loads(text)
        assert isinstance(edits, list)
    except Exception:
        print("No valid JSON edits produced.", file=sys.stderr)
        sys.exit(0)

    changed = 0
    for edit in edits:
        path = Path(args.root) / edit["path"]
        if not path.exists():
            print(f"Skip (missing): {path}", file=sys.stderr); continue
        data = path.read_text(encoding="utf-8", errors="ignore")
        find = edit["find"]
        if data.count(find) != 1:
            print(f"Skip (find not unique) in {path}", file=sys.stderr); continue
        newdata = data.replace(find, edit["replace"])
        if newdata != data:
            path.write_text(newdata, encoding="utf-8")
            changed += 1
            print(f"Edited: {path}", file=sys.stderr)

    if changed:
        shell(["git", "config", "user.name", "ci-bot"])
        shell(["git", "config", "user.email", "ci-bot@users.noreply.github.com"])
        shell(["git", "add", "-A"])
        shell(["git", "commit", "-m", "auto-fix: apply edits from OpenAI based on build.log"])
    else:
        print("No edits applied.", file=sys.stderr)

if __name__ == "__main__":
    main()
