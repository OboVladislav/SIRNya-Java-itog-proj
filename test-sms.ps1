param(
    [string]$Base = "http://localhost:8080",
    [int]$Port = 2775
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$emu  = Join-Path $root "emulators"
$out  = Join-Path $emu "out"
$jar  = Join-Path $root "target/otp-service.jar"

function Fail($m) { throw "FAIL: $m" }
function Wait-Port([int]$p, [int]$sec) {
    $end = (Get-Date).AddSeconds($sec)
    while ((Get-Date) -lt $end) {
        try { $c = New-Object Net.Sockets.TcpClient; $c.Connect("localhost", $p); $c.Close(); return $true }
        catch { Start-Sleep -Milliseconds 300 }
    }
    return $false
}

if (-not (Test-Path $jar)) { Write-Host "FAIL: не найден $jar - сначала выполните: mvn package" -ForegroundColor Red; exit 1 }

# 0. Сервис доступен?
try {
    Invoke-WebRequest -Uri "$Base/api/auth/login" -Method Post -Body "{}" -ContentType "application/json" -UseBasicParsing | Out-Null
} catch {
    if ($null -eq $_.Exception.Response) { Write-Host "FAIL: сервис не запущен на $Base (java -jar target/otp-service.jar)" -ForegroundColor Red; exit 1 }
}

# 1. Компиляция и запуск SMPP-эмулятора с захватом вывода
New-Item -ItemType Directory -Force -Path $out | Out-Null
javac -cp $jar -d $out (Join-Path $emu "FakeSmppServer.java")
if ($LASTEXITCODE -ne 0) { Write-Host "FAIL: не скомпилировался FakeSmppServer.java" -ForegroundColor Red; exit 1 }
$log = Join-Path $out "smpp-test.out"
$errLog = Join-Path $out "smpp-test.err"
Remove-Item $log, $errLog -ErrorAction SilentlyContinue
$proc = Start-Process java -ArgumentList "-cp `"$out;$jar`" FakeSmppServer $Port" `
    -RedirectStandardOutput $log -RedirectStandardError $errLog -NoNewWindow -PassThru

$failed = $false
try {
    if (-not (Wait-Port $Port 10)) { Fail "SMPP-эмулятор не поднялся на порту $Port" }

    # 2. Регистрация + логин (уникальный логин)
    $u = "sms_$(Get-Random)"; $pwd = "secret"
    Invoke-RestMethod -Uri "$Base/api/auth/register" -Method Post -ContentType "application/json" `
        -Body (@{ login = $u; password = $pwd } | ConvertTo-Json) | Out-Null
    $login = Invoke-RestMethod -Uri "$Base/api/auth/login" -Method Post -ContentType "application/json" `
        -Body (@{ login = $u; password = $pwd } | ConvertTo-Json)
    $headers = @{ Authorization = "Bearer $($login.token)" }

    # 3. Генерация кода по каналу SMS
    $op = "op-sms-$(Get-Random)"
    Invoke-RestMethod -Uri "$Base/api/otp/generate" -Method Post -Headers $headers -ContentType "application/json" `
        -Body (@{ operationId = $op; channel = "SMS"; recipient = "79991234567" } | ConvertTo-Json) | Out-Null

    # 4. Ждём, пока код появится в SMS у эмулятора
    $code = $null; $end = (Get-Date).AddSeconds(10)
    while ((Get-Date) -lt $end -and -not $code) {
        Start-Sleep -Milliseconds 400
        if (Test-Path $log) {
            $m = [regex]::Match((Get-Content $log -Raw), "Your code:\s*(\d+)")
            if ($m.Success) { $code = $m.Groups[1].Value }
        }
    }
    if (-not $code) { Fail "код не дошёл до SMPP-эмулятора (SMS не получена)" }
    Write-Host "OK: эмулятор получил SMS, код = $code" -ForegroundColor Green

    # 5. Подтверждаем операцию этим кодом
    Invoke-RestMethod -Uri "$Base/api/otp/validate" -Method Post -Headers $headers -ContentType "application/json" `
        -Body (@{ operationId = $op; code = $code } | ConvertTo-Json) | Out-Null
    Write-Host "OK: код прошёл валидацию" -ForegroundColor Green
    Write-Host "`nPASS: канал SMS работает (генерация -> доставка -> валидация)" -ForegroundColor Green
}
catch { $failed = $true; Write-Host $_.Exception.Message -ForegroundColor Red }
finally { if ($proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force } }
if ($failed) { exit 1 }