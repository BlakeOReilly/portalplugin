# --- scripts/run_build.ps1 ---
[CmdletBinding()]
param(
  [string] $KickoffCommand = "",
  [string] $ConfigPath,
  [int] $BuildTimeoutSeconds = 0
)

# Resolve absolute paths for wrapper & project
$ThisScriptPath = $MyInvocation.MyCommand.Path
if (-not $ThisScriptPath) { throw "Cannot resolve current script path via `$MyInvocation.MyCommand.Path`." }
$ThisScriptDir  = Split-Path -Parent $ThisScriptPath
$WrapperRoot    = (Resolve-Path (Join-Path $ThisScriptDir "..")).Path                # chatgpt-gradle-wrapper/
$ProjectRoot    = (Resolve-Path (Join-Path $WrapperRoot "..")).Path                  # project root

# Load config
if (-not $ConfigPath) { $ConfigPath = Join-Path $WrapperRoot "config.ps1" }
if (-not (Test-Path -LiteralPath $ConfigPath)) { throw "Config file not found: $ConfigPath" }
$ConfigPath = (Resolve-Path $ConfigPath).Path
. $ConfigPath

# Defaults/fallbacks (absolute)
if (-not $ProjectDir)   { $ProjectDir   = $ProjectRoot }
if (-not $WorkDir)      { $WorkDir      = Join-Path $ProjectDir ".chatgpt" }
if (-not (Test-Path -LiteralPath $WorkDir)) { New-Item -ItemType Directory -Path $WorkDir | Out-Null }
$ProjectDir   = (Resolve-Path $ProjectDir).Path
$WorkDir      = (Resolve-Path $WorkDir).Path

if (-not $BuildLogPath) { $BuildLogPath = Join-Path $WorkDir "gradle_build.log" }
if (-not $MessagePath)  { $MessagePath  = Join-Path $WorkDir "message_to_chatgpt.txt" }
# Ensure these live under $WorkDir regardless of what config had
$BuildLogPath = Join-Path $WorkDir (Split-Path $BuildLogPath -Leaf)
$MessagePath  = Join-Path $WorkDir (Split-Path $MessagePath  -Leaf)

# --- Robust prompt discovery & load ---
$ResolvedPromptPath = $null
$PromptText = $null

# Candidate list (in priority order)
$candidates = @()
if ($PromptPath) { $candidates += $PromptPath }
$candidates += @(
  (Join-Path $WrapperRoot "chatgpt_prompt.txt"),
  (Join-Path $ProjectRoot "chatgpt_prompt.txt"),
  (Join-Path $WrapperRoot "prompt.txt"),
  (Join-Path $ProjectRoot "prompt.txt")
)

foreach ($p in $candidates) {
  if ($p -and (Test-Path -LiteralPath $p)) {
    $ResolvedPromptPath = (Resolve-Path -LiteralPath $p).Path
    break
  }
}

if ($ResolvedPromptPath) {
  try {
    # Read as UTF-8; ensure trailing newline
    $PromptText = Get-Content -Raw -LiteralPath $ResolvedPromptPath -Encoding UTF8
    if ($PromptText -and ($PromptText[-1] -ne "`n")) { $PromptText += "`r`n" }
    Write-Host "Using prompt file: ${ResolvedPromptPath}"
  } catch {
    Write-Warning "Failed to read prompt file at ${ResolvedPromptPath}. Error: $($_.Exception.Message)"
    $PromptText = $null
  }
} else {
  Write-Warning "No prompt file found (looked for chatgpt_prompt.txt / prompt.txt in wrapper & project roots, and `$PromptPath). Proceeding without a prompt."
}

# 1) Kickoff command (optional)
if ($KickoffCommand.Trim()) {
  Write-Host "Running kickoff command: $KickoffCommand"
  try {
    cmd.exe /c $KickoffCommand
    if ($LASTEXITCODE -ne 0) { Write-Warning "Kickoff command exited with code $LASTEXITCODE (continuing)" }
  } catch { Write-Warning "Kickoff command threw: $($_.Exception.Message) (continuing)" }
}

# 2) Gradle build (no deadlocks, optional timeout)
Push-Location $ProjectDir
try {
  $cmd =
    if ($GradleCommand -and $GradleCommand.Trim()) { $GradleCommand }
    elseif (Test-Path -LiteralPath (Join-Path $ProjectDir "gradlew.bat")) { ".\gradlew.bat build --stacktrace --console=plain" }
    else { "gradle.bat build --stacktrace --console=plain" }

  Write-Host "Executing: $cmd"
  if (Test-Path -LiteralPath $BuildLogPath) { Remove-Item -LiteralPath $BuildLogPath -Force }

  $redirectCmd = "$cmd 2>&1"
  $p = Start-Process -FilePath "cmd.exe" `
                     -ArgumentList @("/c", $redirectCmd) `
                     -WorkingDirectory $ProjectDir `
                     -NoNewWindow `
                     -RedirectStandardOutput $BuildLogPath `
                     -PassThru

  if ($BuildTimeoutSeconds -gt 0) {
    $elapsed = 0; $interval = 500
    while (-not $p.HasExited -and $elapsed -lt ($BuildTimeoutSeconds*1000)) {
      Start-Sleep -Milliseconds $interval; $elapsed += $interval
    }
    if (-not $p.HasExited) {
      try { $p.Kill() } catch {}
      Add-Content -LiteralPath $BuildLogPath -Value "`r`n---`r`nBuild timed out after $BuildTimeoutSeconds s."
    }
  } else {
    $p.WaitForExit()
  }

  $exitCode = if ($p.HasExited) { $p.ExitCode } else { 9999 }
  $combined = if (Test-Path -LiteralPath $BuildLogPath) { Get-Content -Raw -LiteralPath $BuildLogPath } else { "" }

  # Treat nonzero exit or error keywords as failure
  $hadError = ($exitCode -ne 0) -or ($combined -match "(?i)\b(error|exception|failure|build failed)\b")

  if ($hadError) {
    # Compose message: PROMPT (if any) + LOG
    $header =
      if ($PromptText) {
        "===== PROMPT =====`r`n$PromptText===== BUILD LOG =====`r`n"
      } else {
        "===== BUILD LOG =====`r`n"
      }

    # Write as UTF-8 to avoid loss/garbling
    [System.IO.File]::WriteAllText($MessagePath, $header + $combined, [System.Text.Encoding]::UTF8)

    Write-Host "Wrote composed message (prompt + log) to: $MessagePath"
    [pscustomobject]@{
      HadError    = $true
      ExitCode    = $exitCode
      LogPath     = $BuildLogPath
      MessagePath = (Resolve-Path $MessagePath).Path
    }
  } else {
    Write-Host "Gradle build succeeded (exit $exitCode)."
    [pscustomobject]@{
      HadError    = $false
      ExitCode    = $exitCode
      LogPath     = $BuildLogPath
      MessagePath = $null
    }
  }
}
finally { Pop-Location }
