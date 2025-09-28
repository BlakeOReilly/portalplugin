# --- config.ps1 ---

# Path to your Gradle project root (folder that contains build.gradle)
$ProjectDir = "D:\Multiplayer Network\portalplugin"

# Optional: explicit Gradle command. If empty, auto-detects gradlew.bat then falls back to gradle.bat
$GradleCommand = ""

# Where to store logs, message, and runtime title cache
$WorkDir       = Join-Path $ProjectDir ".chatgpt"
$BuildLogPath  = Join-Path $WorkDir "gradle_build.log"
$MessagePath   = Join-Path $WorkDir "message_to_chatgpt.txt"

# Pre-written prompt file (optional). If not set, scripts look for:
#   chatgpt_prompt.txt or prompt.txt in wrapper root or project root.
$PromptPath = ""

# We do NOT launch EXE or web. Attach to an already-open desktop window only.
$ChatGptExe = ""

# ALWAYS start with this initial title; do not change this in code:
$ChatGptInitialTitle = "ChatGPT - Minecraft Plugins"

# Max time (ms) to try to focus the window
$ChatGptActivateTimeoutMs = 15000

# No AutoHotkey / no web / no EXE launch
$UseAutoHotkey = $false
$OpenWebIfNoExe = $false
$LaunchDesktopIfExeSet = $false

# Do NOT persist detected titles into config; use a runtime cache file instead.
$PersistDetectedTitle = $false

# Mirror ChatGPT window title into the console title for N seconds after send (0 = off)
$MirrorWindowTitleSeconds = 20
