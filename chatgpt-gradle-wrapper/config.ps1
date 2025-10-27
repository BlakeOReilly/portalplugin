# --- config.ps1 ---

# Path to your Gradle project root (folder that contains build.gradle)
$ProjectDir = "D:\Multiplayer Network\portalplugin"

# Optional: explicit Gradle command. If empty, auto-detects gradlew.bat then falls back to gradle.bat
$GradleCommand = ""

# Where to store logs, message, and runtime title cache
$WorkDir       = Join-Path $ProjectDir ".chatgpt"
$BuildLogPath  = Join-Path $WorkDir "gradle_build.log"
$MessagePath   = Join-Path $WorkDir "message_to_chatgpt.txt"

# Optional direct prompt path (otherwise discovered by run_build.ps1)
$PromptPath = ""

# ===== Desktop attach-only mode (no web/EXE launch) =====
$ChatGptExe = ""                                     # keep empty to avoid launching
$ChatGptInitialTitle = "ChatGPT - Minecraft Plugins" # fixed initial title to try first
$ChatGptActivateTimeoutMs = 15000

# No AutoHotkey / no web / no EXE launch
$UseAutoHotkey = $false
$OpenWebIfNoExe = $false
$LaunchDesktopIfExeSet = $false

# Do NOT persist detected titles into config; use a runtime cache file instead
$PersistDetectedTitle = $false

# Mirror ChatGPT window title into the console title for N seconds after send (0 = off)
$MirrorWindowTitleSeconds = 20

# ===== Start a watcher EXE after sending, to capture JSON reply to tools\fixes.json =====
$EnableWatcherExe       = $true

# Prefer published single-file EXE; else fallback to the normal build EXE:
$WatcherExePath         = "D:\Multiplayer Network\portalplugin\chatgpt-gradle-wrapper\tools\ChatGptResponseWatcher\bin\Release\net5.0-windows\win-x64\publish\ChatGptResponseWatcher.exe"
$WatcherExePathFallback = "D:\Multiplayer Network\portalplugin\chatgpt-gradle-wrapper\tools\ChatGptResponseWatcher\bin\Release\net5.0-windows\ChatGptResponseWatcher.exe"

# Where the captured JSON will be written (will be overwritten on success)
$FixesJsonPath          = Join-Path $ProjectDir "tools\fixes.json"

# Make the watcher console VISIBLE so you can monitor it
$WatcherWindowStyle     = "Normal"   # or "Hidden"
$WatcherTimeoutSeconds  = 600

# Optional: watcher log file (rotates per run)
$WatcherLogPath         = Join-Path $WorkDir "watcher.log"
