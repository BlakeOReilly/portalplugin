# --- scripts/run_and_send.ps1 (robust attach via EnumWindows) ---
[CmdletBinding()]
param(
  [string] $Command = "",
  [string] $ConfigPath
)

# ========== Helpers ==========
function Get-ForegroundWindowTitle {
  $sig = @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class Win32 {
  public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern int GetWindowTextLength(IntPtr hWnd);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
}
"@
  Add-Type -TypeDefinition $sig -ErrorAction SilentlyContinue | Out-Null
  $h = [Win32]::GetForegroundWindow()
  if ($h -eq [IntPtr]::Zero) { return $null }
  $len = [Win32]::GetWindowTextLength($h)
  if ($len -le 0) { return $null }
  $sb = New-Object System.Text.StringBuilder ($len + 4)
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
  public static List<Tuple<IntPtr,string>> GetWindows(){
    var list = new List<Tuple<IntPtr,string>>();
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
    # exact/starts-with for preferred titles first
    foreach ($pt in $PreferredTitles) {
      if (-not $pt) { continue }
      $hit = $wins | Where-Object { $_.Item2 -eq $pt -or $_.Item2.StartsWith($pt) } | Select-Object -First 1
      if ($hit) { if ([Win32Enum]::Activate($hit.Item1)) { return @{ ok=$true; title=$hit.Item2 } } }
    }
    # generic contains "ChatGPT"
    $hit2 = $wins | Where-Object { $_.Item2 -like "*ChatGPT*" } | Select-Object -First 1
    if ($hit2) { if ([Win32Enum]::Activate($hit2.Item1)) { return @{ ok=$true; title=$hit2.Item2 } } }

    # keep a small sample for diagnostics
    $lastMatches = ($wins | Where-Object { $_.Item2 -like "*Chat*" -or $_.Item2 -like "*GPT*" } | Select-Object -First 6).Item2
    Start-Sleep -Milliseconds 250
  }
  return @{ ok=$false; sample=$lastMatches }
}

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

# Defaults/guards
if (-not $ChatGptInitialTitle -or -not $ChatGptInitialTitle.Trim()) { $ChatGptInitialTitle = "ChatGPT" }
if (-not $ChatGptActivateTimeoutMs) { $ChatGptActivateTimeoutMs = 15000 }
if (-not (Test-Path -LiteralPath $WorkDir)) { New-Item -ItemType Directory -Path $WorkDir | Out-Null }
$TitleCachePath = Join-Path $WorkDir "last_title.txt"
$RuntimeCachedTitle = $null
if (Test-Path -LiteralPath $TitleCachePath) {
  try { $RuntimeCachedTitle = (Get-Content -Raw -LiteralPath $TitleCachePath).Trim() } catch { }
  if (-not $RuntimeCachedTitle) { $RuntimeCachedTitle = $null }
}

# ========== Build / compose message ==========
if (-not (Test-Path -LiteralPath $RunBuildPath)) { throw "Cannot find run_build.ps1 at: $RunBuildPath" }
$result = & $RunBuildPath -KickoffCommand $Command -ConfigPath $ConfigPath
if (-not $result) { throw "run_build.ps1 returned no result object." }

if (-not $result.HadError) {
  Write-Host "Gradle build succeeded (exit $($result.ExitCode)). Nothing to send."
  Write-Host "Log: $($result.LogPath)"
  exit 0
}

Write-Warning "Gradle build failed (exit $($result.ExitCode)). Preparing to send to ChatGPT..."

if (-not $result.MessagePath -or -not (Test-Path -LiteralPath $result.MessagePath)) {
  throw "Message file not found. Expected at: $($result.MessagePath)"
}
$MessageAbs = (Resolve-Path $result.MessagePath).Path
if ($MessageAbs -match '^[A-Za-z]:\\$') { throw "Refusing to send a drive root as message file: $MessageAbs" }
if ((Get-Item $MessageAbs).Length -lt 10) { Write-Warning "Message file is very small; check prompt/log generation: $MessageAbs" }

# Clipboard
$msg = Get-Content -Raw -LiteralPath $MessageAbs
try { Set-Clipboard -Value $msg } catch {
  Add-Type -AssemblyName System.Windows.Forms
  [System.Windows.Forms.Clipboard]::SetText($msg)
}

Write-Host "Searching for an open ChatGPT window…"
$preferred = @($RuntimeCachedTitle, $ChatGptInitialTitle) | Where-Object { $_ -and $_.Trim() }
$res = Find-And-ActivateChatGptWindow -PreferredTitles $preferred -TimeoutMs $ChatGptActivateTimeoutMs

if (-not $res.ok) {
  $sample = ($res.sample -join " | ")
  throw "Could not focus ChatGPT. Tried cached & initial titles; also scanned all windows containing 'ChatGPT'. Sample titles seen: $sample"
}

$focusedTitle = $res.title
Write-Host "Focused: $focusedTitle"
try { Set-Content -LiteralPath $TitleCachePath -Value $focusedTitle -Encoding UTF8 } catch { }

# Type: New Chat -> Paste -> Enter (twice)
$ws = New-Object -ComObject WScript.Shell
Start-Sleep -Milliseconds 200
$ws.SendKeys("^{n}")
Start-Sleep -Milliseconds 300
$ws.SendKeys("^{v}")
Start-Sleep -Milliseconds 250
$ws.SendKeys("~")
Start-Sleep -Milliseconds 350
$ws.SendKeys("^{v}")
Start-Sleep -Milliseconds 200
$ws.SendKeys("~")

# Live mirror (optional)
if ([int]$MirrorWindowTitleSeconds -gt 0) {
  $orig = $Host.UI.RawUI.WindowTitle
  $deadline = (Get-Date).AddSeconds([int]$MirrorWindowTitleSeconds)
  $last = $null
  while ((Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 300
    $t = Get-ForegroundWindowTitle
    if ($t -and ($t -ne $last) -and ($t -like "*ChatGPT*")) {
      $Host.UI.RawUI.WindowTitle = "ChatGPT · " + $t
      $last = $t
      try { Set-Content -LiteralPath $TitleCachePath -Value $t -Encoding UTF8 } catch { }
    }
  }
  # (leave mirrored title; uncomment to restore) # $Host.UI.RawUI.WindowTitle = $orig
}

Write-Host "Sent to ChatGPT (desktop attach)."
exit 0
