# --- scripts/run_and_send.ps1 (attach-by-title; SendInput paste/send; title cache; watcher EXE) ---
[CmdletBinding()]
param(
  [string] $Command = "",
  [string] $ConfigPath
)

# ========== Helpers ==========
function Get-ActiveWindowTitle {
  $sig = @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class Win32 {
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);
}
"@
  Add-Type -TypeDefinition $sig -ErrorAction SilentlyContinue | Out-Null
  $h = [Win32]::GetForegroundWindow()
  if ($h -eq [IntPtr]::Zero) { return $null }
  $sb = New-Object System.Text.StringBuilder 512
  [void][Win32]::GetWindowText($h, $sb, $sb.Capacity)
  $t = $sb.ToString()
  if ([string]::IsNullOrWhiteSpace($t)) { return $null }
  return $t
}

function Find-And-ActivateChatGptWindow {
  param(
    [string[]] $PreferredTitles,
    [int] $TimeoutMs = 10000
  )
  $sig = @"
using System;
using System.Collections.Generic;
using System.Text;
using System.Runtime.InteropServices;
public static class Win32Enum {
  public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern int GetWindowTextLength(IntPtr hWnd);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
  public static System.Collections.Generic.List<Tuple<IntPtr,string>> GetWindows(){
    var list = new System.Collections.Generic.List<Tuple<IntPtr,string>>();
    EnumWindows((h, l) => {
      if (!IsWindowVisible(h)) return true;
      int len = GetWindowTextLength(h);
      if (len <= 0) return true;
      var sb = new StringBuilder(len + 4);
      GetWindowText(h, sb, sb.Capacity);
      var title = sb.ToString();
      if (!string.IsNullOrWhiteSpace(title)) list.Add(Tuple.Create(h, title));
      return true;
    }, IntPtr.Zero);
    return list;
  }
  public static bool Activate(IntPtr h){
    ShowWindowAsync(h, 9); // SW_RESTORE
    return SetForegroundWindow(h);
  }
}
"@
  Add-Type -TypeDefinition $sig -ErrorAction SilentlyContinue | Out-Null

  $deadline = (Get-Date).AddMilliseconds($TimeoutMs)
  $lastMatches = @()

  while ((Get-Date) -lt $deadline) {
    $wins = [Win32Enum]::GetWindows()
    foreach ($pt in $PreferredTitles) {
      if (-not $pt) { continue }
      $hit = $wins | Where-Object { $_.Item2 -eq $pt -or $_.Item2.StartsWith($pt) } | Select-Object -First 1
      if ($hit) { if ([Win32Enum]::Activate($hit.Item1)) { return @{ ok=$true; title=$hit.Item2 } } }
    }
    $hit2 = $wins | Where-Object { $_.Item2 -like "*ChatGPT*" } | Select-Object -First 1
    if ($hit2) { if ([Win32Enum]::Activate($hit2.Item1)) { return @{ ok=$true; title=$hit2.Item2 } } }

    $lastMatches = ($wins | Where-Object { $_.Item2 -like "*Chat*" -or $_.Item2 -like "*GPT*" } | Select-Object -First 6).Item2
    Start-Sleep -Milliseconds 250
  }
  return @{ ok=$false; sample=$lastMatches }
}

# ========== Reliable SendInput (no stuck modifiers) ==========
$sendInputSrc = @"
using System;
using System.Runtime.InteropServices;
public static class Kbd {
  [StructLayout(LayoutKind.Sequential)]
  struct INPUT { public uint type; public INPUTUNION U; }
  [StructLayout(LayoutKind.Explicit)]
  struct INPUTUNION { [FieldOffset(0)] public KEYBDINPUT ki; }
  [StructLayout(LayoutKind.Sequential)]
  struct KEYBDINPUT {
    public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo;
  }
  const uint INPUT_KEYBOARD = 1;
  const uint KEYEVENTF_KEYUP = 0x0002;

  [DllImport("user32.dll", SetLastError=true)]
  static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

  static void Key(ushort vk, bool down) {
    var inp = new INPUT { type = INPUT_KEYBOARD };
    inp.U.ki = new KEYBDINPUT { wVk = vk, wScan = 0, dwFlags = down ? 0u : KEYEVENTF_KEYUP, time = 0, dwExtraInfo = IntPtr.Zero };
    var sent = SendInput(1, new INPUT[]{ inp }, Marshal.SizeOf(typeof(INPUT)));
    if (sent != 1) { /* ignore */ }
  }

  public static void CtrlV() {
    Key(0x11, true);   // CTRL down (VK_CONTROL)
    Key(0x56, true);   // 'V' down (VK_V)
    Key(0x56, false);  // 'V' up
    Key(0x11, false);  // CTRL up
  }
  public static void CtrlN() {
    Key(0x11, true);   // CTRL
    Key(0x4E, true);   // 'N'
    Key(0x4E, false);
    Key(0x11, false);
  }
  public static void Enter() {
    Key(0x0D, true);   // ENTER
    Key(0x0D, false);
  }
  public static void Tab() {
    Key(0x09, true);   // TAB
    Key(0x09, false);
  }
}
"@
Add-Type -TypeDefinition $sendInputSrc -ErrorAction SilentlyContinue | Out-Null

# ========== Path resolution ==========
$ThisScriptPath = $MyInvocation.MyCommand.Path
if (-not $ThisScriptPath) { throw "Cannot resolve current script path via `$MyInvocation.MyCommand.Path`." }
$ThisScriptDir  = Split-Path -Parent $ThisScriptPath
$WrapperRoot    = (Resolve-Path (Join-Path $ThisScriptDir "..")).Path

$ConfigDefault  = Join-Path $WrapperRoot "config.ps1"
$RunBuildPath   = Join-Path $ThisScriptDir "run_build.ps1"
if (Test-Path -LiteralPath $ConfigDefault) { $ConfigDefault = (Resolve-Path $ConfigDefault).Path }
if (Test-Path -LiteralPath $RunBuildPath)  { $RunBuildPath  = (Resolve-Path $RunBuildPath).Path }

# ========== Load config ==========
if (-not $ConfigPath) { $ConfigPath = $ConfigDefault }
if (-not (Test-Path -LiteralPath $ConfigPath)) { throw "Config file not found: $ConfigPath" }
$ConfigPath = (Resolve-Path $ConfigPath).Path
. $ConfigPath

# Defaults / guards
if (-not $ChatGptInitialTitle -or -not $ChatGptInitialTitle.Trim()) { $ChatGptInitialTitle = "ChatGPT" }
if (-not $ChatGptActivateTimeoutMs) { $ChatGptActivateTimeoutMs = 15000 }
if ($null -eq $MirrorWindowTitleSeconds) { $MirrorWindowTitleSeconds = 20 }
$UseAutoHotkey = $false; $OpenWebIfNoExe = $false; $LaunchDesktopIfExeSet = $false
$PersistDetectedTitle = $false

# Ensure WorkDir exists and set title cache path
if (-not (Test-Path -LiteralPath $WorkDir)) { New-Item -ItemType Directory -Path $WorkDir | Out-Null }
$TitleCachePath = Join-Path $WorkDir "last_title.txt"

# Load runtime cached title (if any)
$RuntimeCachedTitle = $null
if (Test-Path -LiteralPath $TitleCachePath) {
  try {
    $RuntimeCachedTitle = (Get-Content -Raw -LiteralPath $TitleCachePath).Trim()
    if (-not $RuntimeCachedTitle) { $RuntimeCachedTitle = $null }
  } catch { $RuntimeCachedTitle = $null }
}

# ========== Run build / compose message ==========
if (-not (Test-Path -LiteralPath $RunBuildPath)) { throw "Cannot find run_build.ps1 at: $RunBuildPath" }
$result = & $RunBuildPath -KickoffCommand $Command -ConfigPath $ConfigPath
if (-not $result) { throw "run_build.ps1 returned no result object." }

if (-not $result.HadError) {
  Write-Host "Gradle build succeeded (exit $($result.ExitCode)). Nothing to send."
  Write-Host "Log: $($result.LogPath)"
  exit 0
}

Write-Warning "Gradle build failed (exit $($result.ExitCode)). Preparing to send to ChatGPT..."

# Validate message file
if (-not $result.MessagePath -or -not (Test-Path -LiteralPath $result.MessagePath)) {
  throw "Message file not found. Expected at: $($result.MessagePath)"
}
$MessageAbs = (Resolve-Path $result.MessagePath).Path
if ($MessageAbs -match '^[A-Za-z]:\\$') { throw "Refusing to send a drive root as message file: $MessageAbs" }
if ((Get-Item $MessageAbs).Length -lt 10) { Write-Warning "Message file is very small; check prompt/log generation: $MessageAbs" }

Write-Host "Attach-by-title mode: fixed initial title + runtime title cache (no web, no EXE launch)."

# Clipboard: place composed message
$msg = Get-Content -Raw -LiteralPath $MessageAbs
try { Set-Clipboard -Value $msg } catch {
  Add-Type -AssemblyName System.Windows.Forms
  [System.Windows.Forms.Clipboard]::SetText($msg)
}

# ========== Focus window ==========
$titles = @()
if ($RuntimeCachedTitle) { $titles += $RuntimeCachedTitle }
$titles += @(
  $ChatGptInitialTitle,
  "New chat - ChatGPT",
  "ChatGPT Desktop",
  "ChatGPT"
)

$wshell = New-Object -ComObject WScript.Shell
$activated = $false
foreach ($t in $titles) { if ($wshell.AppActivate($t)) { $activated = $true; break } }

if (-not $activated) {
  $res = Find-And-ActivateChatGptWindow -PreferredTitles @($RuntimeCachedTitle,$ChatGptInitialTitle) -TimeoutMs $ChatGptActivateTimeoutMs
  if ($res.ok) {
    $activated = $true
  } else {
    $sample = ($res.sample -join " | ")
    throw "Could not focus ChatGPT. Tried cached & initial titles; scanned windows with 'ChatGPT'. Sample titles seen: $sample"
  }
}

# Record the focused title BEFORE sending; update cache
$focusedBefore = Get-ActiveWindowTitle
if ($focusedBefore) {
  Write-Host "Focused ChatGPT window: $focusedBefore"
  try { Set-Content -LiteralPath $TitleCachePath -Value $focusedBefore -Encoding UTF8 } catch {}
}

# ========== Keystrokes: New Chat -> Paste -> Enter (robust SendInput) ==========
# Give the app a moment to settle
Start-Sleep -Milliseconds 250

# New chat
[void][Kbd]::CtrlN()
Start-Sleep -Milliseconds 700

# Nudge focus into the composer
[void][Kbd]::Tab()
Start-Sleep -Milliseconds 200
[void][Kbd]::Tab()   # extra nudge in case focus lands on sidebar
Start-Sleep -Milliseconds 150

# Paste clipboard (explicit Ctrl down/up + V down/up)
[void][Kbd]::CtrlV()
Start-Sleep -Milliseconds 250

# Send (Enter)
[void][Kbd]::Enter()
Start-Sleep -Milliseconds 250

# Record title AFTER sending; update cache again
$focusedAfter = Get-ActiveWindowTitle
if ($focusedAfter) {
  Write-Host "Post-send ChatGPT window title: $focusedAfter"
  try { Set-Content -LiteralPath $TitleCachePath -Value $focusedAfter -Encoding UTF8 } catch {}
}

# --- Live console-title mirror (optional) ---
if ([int]$MirrorWindowTitleSeconds -gt 0) {
  $origConsoleTitle = $Host.UI.RawUI.WindowTitle
  try {
    $deadline = (Get-Date).AddSeconds([int]$MirrorWindowTitleSeconds)
    $last = $null
    while ((Get-Date) -lt $deadline) {
      Start-Sleep -Milliseconds 300
      $t = Get-ActiveWindowTitle
      if ($t -and ($t -ne $last) -and ($t -like "*ChatGPT*")) {
        $Host.UI.RawUI.WindowTitle = "ChatGPT · " + $t
        $last = $t
        try { Set-Content -LiteralPath $TitleCachePath -Value $t -Encoding UTF8 } catch {}
      }
    }
  } catch { } finally {
    # $Host.UI.RawUI.WindowTitle = $origConsoleTitle
  }
}

Write-Host "Sent to ChatGPT (attach-by-title)."

# ===== Start ChatGPT response watcher EXE to capture JSON -> tools\fixes.json =====
try {
  if ($EnableWatcherExe) {
    $exe = $WatcherExePath
    if (-not ($exe -and (Test-Path -LiteralPath $exe)) -and $WatcherExePathFallback -and (Test-Path -LiteralPath $WatcherExePathFallback)) {
      $exe = $WatcherExePathFallback
    }
    if (-not ($exe -and (Test-Path -LiteralPath $exe))) {
      Write-Warning "Watcher EXE not found. Checked:`n  $WatcherExePath`n  $WatcherExePathFallback"
    } else {
      $outPath = if ($FixesJsonPath) { $FixesJsonPath } else { (Join-Path $ProjectDir "tools\fixes.json") }
      $timeout = if ($WatcherTimeoutSeconds) { [int]$WatcherTimeoutSeconds } else { 600 }
      $logPath = $WatcherLogPath

      # Ensure output directory exists so overwrite can succeed
      $outDir = Split-Path -Parent $outPath
      if ($outDir -and -not (Test-Path -LiteralPath $outDir)) {
        New-Item -ItemType Directory -Force -Path $outDir | Out-Null
      }

      Write-Host "Starting ChatGPT response watcher -> $outPath (timeout ${timeout}s)"
      $args = @(
        "--out", $outPath,
        "--timeout", "$timeout",
        "--clear-clipboard"
      )
      if ($logPath) {
        $logDir = Split-Path -Parent $logPath
        if ($logDir -and -not (Test-Path -LiteralPath $logDir)) { New-Item -ItemType Directory -Force -Path $logDir | Out-Null }
        $args += @("--log", $logPath)
      }
      Start-Process -WindowStyle $WatcherWindowStyle -FilePath $exe -ArgumentList $args | Out-Null
    }
  }
} catch {
  Write-Warning "Failed to start watcher EXE: $($_.Exception.Message)"
}

exit 0
