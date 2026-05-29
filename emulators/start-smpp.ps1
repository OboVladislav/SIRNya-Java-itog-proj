# Запуск локального фейкового SMPP-сервера (SMSC) для канала SMS (порт 2775, принимает любой bind).
# SMS печатаются прямо в этой консоли. Остановка — Ctrl+C.
# Требует собранный jar (target/otp-service.jar) — из него берётся библиотека jSMPP.
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $here
$jar  = Join-Path $root 'target/otp-service.jar'
if (-not (Test-Path $jar)) { throw "Не найден $jar - сначала выполните: mvn package" }
$out = Join-Path $here 'out'
New-Item -ItemType Directory -Force -Path $out | Out-Null
javac -cp $jar -d $out (Join-Path $here 'FakeSmppServer.java')
Write-Host "SMPP-эмулятор: localhost:2775 (sms.properties уже настроен на него)`n"
java -cp "$out;$jar" FakeSmppServer 2775