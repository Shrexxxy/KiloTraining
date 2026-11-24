param(
    [string] $File
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$server = Split-Path -Parent $PSScriptRoot
$compose = @('compose', '--project-directory', $server)
$logDirectory = Join-Path $server 'logs'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$log = Join-Path $logDirectory "off-$(Get-Date -Format 'yyyyMMdd-HHmmss-fff')-$PID.log"
Set-Content -LiteralPath $log -Value "Начало импорта: $(Get-Date -Format o)" -Encoding utf8

$remoteDirectory = $null
$exitCode = 0
try {
    $source = @('--url', 'https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz')
    if ($File) {
        $localFile = Get-Item -LiteralPath $File
        if ($localFile.PSIsContainer) { throw 'Укажите файл выгрузки.' }

        $remoteDirectory = docker @compose exec -T api mktemp -d /tmp/off.XXXXXXXX 2>$null
        if ($LASTEXITCODE -ne 0) { throw 'Не удалось создать временный каталог в API.' }

        $extension = if ($localFile.Name.EndsWith('.gz', [StringComparison]::OrdinalIgnoreCase)) { '.csv.gz' } else { '.csv' }
        $remoteFile = "$remoteDirectory/products$extension"
        docker @compose cp $localFile.FullName "api:$remoteFile" 2>$null
        if ($LASTEXITCODE -ne 0) { throw 'Не удалось скопировать выгрузку в API.' }
        $source = @('--file', $remoteFile)
    }

    $summary = docker @compose exec -T api dotnet /app/CalorieTraining.Server.dll import-off @source 2>$null
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) { throw 'Команда import-off завершилась с ошибкой.' }
    Add-Content -LiteralPath $log -Value $summary -Encoding utf8
}
catch {
    if ($exitCode -eq 0) { $exitCode = 1 }
    Add-Content -LiteralPath $log -Value "Импорт завершился с ошибкой (код $exitCode)." -Encoding utf8
}
finally {
    if ($remoteDirectory) {
        docker @compose exec -T api sh -c 'rm -f -- "$1" && rmdir -- "$2"' off-cleanup $remoteFile $remoteDirectory 2>$null
        if ($LASTEXITCODE -ne 0) {
            if ($exitCode -eq 0) { $exitCode = 1 }
            Add-Content -LiteralPath $log -Value 'Не удалось удалить временную выгрузку.' -Encoding utf8
        }
    }
}

Write-Output $log
exit $exitCode
