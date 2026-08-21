param(
  [Parameter(Mandatory=$true)][string]$repo,
  [Parameter(Mandatory=$true)][string]$p12Path,
  [Parameter(Mandatory=$true)][string]$p12Pass,
  [Parameter(Mandatory=$true)][string]$provPath,
  [Parameter(Mandatory=$true)][string]$tag,
  [string]$appleId = "",
  [string]$appleSpecPass = ""
)

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
  Write-Error "gh CLI not found. Install from https://cli.github.com/"
  exit 1
}

if (-not (Test-Path $p12Path)) { Write-Error "P12 not found: $p12Path"; exit 1 }
if (-not (Test-Path $provPath)) { Write-Error "Provisioning profile not found: $provPath"; exit 1 }

if (-not $env:GH_TOKEN) { Write-Error "Set environment variable GH_TOKEN with a PAT (repo+workflow scopes)"; exit 1 }

Write-Output "Authenticating gh CLI..."
 $env:GH_TOKEN | gh auth login --with-token | Out-Null

Write-Output "Encoding files..."
$p12 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($p12Path))
$prov = [Convert]::ToBase64String([IO.File]::ReadAllBytes($provPath))

Write-Output "Setting repository secrets..."
$p12 | gh secret set P12_BASE64 -R $repo -b -
gh secret set P12_PASSWORD -R $repo -b $p12Pass | Out-Null
$prov | gh secret set PROVISION_BASE64 -R $repo -b -

if ($appleId) { gh secret set APPLE_ID -R $repo -b $appleId | Out-Null }
if ($appleSpecPass) { gh secret set APPLE_SPECIFIC_PASSWORD -R $repo -b $appleSpecPass | Out-Null }

Write-Output "Creating and pushing tag $tag..."
git fetch --tags origin
git tag -f $tag
git push origin --tags --force

Write-Output "Done. Check https://github.com/$repo/actions for the workflow run and artifact 'ios-ipa'"
