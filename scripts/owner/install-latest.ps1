# Installs the newest blesk build from GitHub Releases on the phone connected by cable.
#
#   powershell -ExecutionPolicy Bypass -File scripts\owner\install-latest.ps1 [-Force]
#
# -Force: when the phone has a copy that cannot be updated (signed by another key, or newer),
# remove it together with its data and install the latest build instead.
[CmdletBinding()]
param([switch]$Force)

. (Join-Path $PSScriptRoot 'lib.ps1')

$target = Select-Target
Install-Latest $target $Force.IsPresent 'powershell -ExecutionPolicy Bypass -File scripts\owner\install-latest.ps1 -Force'
