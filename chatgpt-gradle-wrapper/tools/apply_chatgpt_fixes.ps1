<#  tools\apply_chatgpt_fixes.ps1
    Applies full-file updates from a JSON array:
    [
      { "path": "relative/or/absolute/file.ext", "content": "FULL UPDATED FILE CONTENTS" },
      ...
    ]
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)]
  [string] $JsonPath,

  # Project root used to resolve relative paths & enforce safety.
  [string] $ProjectRoot = (Split-Path -Parent $PSCommandPath),

  # Preview without writing files.
  [switch] $DryRun,

  # Skip making .bak_YYYYMMDD_HHMMSS backups.
  [switch] $NoBackup,

  # Permit writes outside $ProjectRoot (default: blocked).
  [switch] $AllowOutsideProject
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Err($msg){ Write-Host "[ERROR] $msg" -ForegroundColor Red }
function Write-Warn($msg){ Write-Host "[WARN]  $msg" -ForegroundColor Yellow }
function Write-Info($msg){ Write-Host "[INFO]  $msg" -ForegroundColor Cyan }

# --- Load & validate JSON ----------------------------------------------------
if (-not (Test-Path -LiteralPath $JsonPath)) { throw "JSON not found: $JsonPath" }
$jsonRaw = Get-Content -Raw -LiteralPath $JsonPath -Encoding UTF8

try {
  $updates = $jsonRaw | ConvertFrom-Json -ErrorAction Stop
} catch {
  throw "Invalid JSON: $($_.Exception.Message)"
}

if ($null -eq $updates) { throw "JSON parsed to null. Expected a non-empty JSON array." }
if (-not ($updates -is [System.Collections.IEnumerable])) {
  throw "Top-level JSON must be an array of { path, content } objects."
}

# Normalize and resolve project root
$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path

# --- Helpers -----------------------------------------------------------------
function Resolve-Target([string] $p) {
  if ([string]::IsNullOrWhiteSpace($p)) { throw "Empty 'path' value." }
  $isRooted = [System.IO.Path]::IsPathRooted($p)
  $candidate = if ($isRooted) { $p } else { Join-Path -Path $ProjectRoot -ChildPath $p }

  # Canonical full path
  $full = [System.IO.Path]::GetFullPath($candidate)

  if (-not $AllowOutsideProject) {
    # Enforce inside project root
    $projNorm = $ProjectRoot.TrimEnd('\') + '\'
    $fullNorm = $full.TrimEnd('\') + '\'
    if (-not $fullNorm.StartsWith($projNorm, [System.StringComparison]::OrdinalIgnoreCase)) {
      throw "Blocked write outside project: '$full' (from path='$p'). Use -AllowOutsideProject to override."
    }
  }
  return $full
}

function Ensure-ParentDir([string] $path) {
  $dir = Split-Path -Parent $path
  if (-not (Test-Path -LiteralPath $dir)) {
    if ($DryRun) { Write-Info "[DRY-RUN] MKDIR $dir" }
    else { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
  }
}

# --- Schema validation pass ---------------------------------------------------
$idx = 0
foreach ($u in $updates) {
  $idx++
  if ($null -eq $u) { throw "Item #$idx is null; expected object." }
  if (-not ($u.PSObject.Properties.Name -contains 'path'))    { throw "Item #$idx missing 'path'." }
  if (-not ($u.PSObject.Properties.Name -contains 'content')) { throw "Item #$idx missing 'content'." }

  if (-not ($u.path -is [string]) -or [string]::IsNullOrWhiteSpace($u.path)) {
    throw "Item #$idx 'path' must be a non-empty string."
  }
  if (-not ($u.content -is [string])) {
    throw "Item #$idx 'content' must be a string (full updated file contents)."
  }

  # Try resolving early to surface any safety errors fast.
  [void](Resolve-Target $u.path)
}

# --- Apply -------------------------------------------------------------------
$changed = New-Object System.Collections.Generic.List[string]

foreach ($u in $updates) {
  $target = Resolve-Target $u.path
  Ensure-ParentDir $target

  $exists = Test-Path -LiteralPath $target
  $backup = if ($exists -and -not $NoBackup) { "$target.bak_$(Get-Date -Format 'yyyyMMdd_HHmmss')" } else { $null }

  if ($DryRun) {
    $bkMsg = if ($backup) { " (backup -> $backup)" } else { "" }
    Write-Info "[DRY-RUN] WRITE $target$bkMsg"
    continue
  }

  try {
    if ($backup) { Copy-Item -LiteralPath $target -Destination $backup -Force }
  } catch {
    Write-Warn "Could not create backup for '$target': $($_.Exception.Message)"
  }

  # Write UTF-8 (no BOM); preserve exact provided content
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($target, [string]$u.content, $utf8NoBom)

  $changed.Add($target) | Out-Null
  Write-Host "Updated: $target" -ForegroundColor Green
}

# --- Summary -----------------------------------------------------------------
Write-Host ""
if ($DryRun) {
  Write-Host "DRY-RUN complete. Files that WOULD change: $($updates.Count)" -ForegroundColor Yellow
} else {
  Write-Host "Done. Files changed: $($changed.Count)" -ForegroundColor Green
  if ($changed.Count -gt 0) {
    $changed | ForEach-Object { Write-Host " - $_" }
  }
}

# Optional exit code: 0 if ok; 2 if nothing to do (empty array)
if (($updates | Measure-Object).Count -eq 0) { exit 2 } else { exit 0 }
