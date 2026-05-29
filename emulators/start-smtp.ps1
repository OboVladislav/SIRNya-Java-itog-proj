# Запуск локального фейкового SMTP-сервера для канала EMAIL (порт 2525, без авторизации).
# Письма печатаются прямо в этой консоли. Остановка — Ctrl+C.
# Требует только JDK (никаких зависимостей).
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$out  = Join-Path $here 'out'
New-Item -ItemType Directory -Force -Path $out | Out-Null
javac -d $out (Join-Path $here 'FakeSmtpServer.java')
Write-Host "SMTP-эмулятор: localhost:2525 (email.properties уже настроен на него)`n"
java -cp $out FakeSmtpServer 2525