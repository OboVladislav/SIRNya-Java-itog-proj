<#
    verify.ps1 — автоматическая проверка всей логики OTP-сервиса через HTTP.
    Предполагается, что сервис уже запущен: java -jar target\otp-service.jar
    Запуск:   powershell -ExecutionPolicy Bypass -File .\verify.ps1
    Опц.:     .\verify.ps1 -Base "http://localhost:8080"
#>
param(
    [string]$Base = "http://localhost:8080",
    [string]$OtpFile = "otp-codes.txt"
)

$ErrorActionPreference = "Stop"
$script:passed = 0
$script:failed = 0

function Check($name, $cond) {
    if ($cond) { Write-Host "PASS  $name" -ForegroundColor Green; $script:passed++ }
    else       { Write-Host "FAIL  $name" -ForegroundColor Red;   $script:failed++ }
}

# Унифицированный вызов: возвращает @{ code = <int>; body = <string> }, не бросает исключений.
function Req($method, $path, $token, $json) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $args = @{
        Uri             = "$Base$path"
        Method          = $method
        Headers         = $headers
        UseBasicParsing = $true
    }
    if ($json) {
        $args["ContentType"] = "application/json"
        $args["Body"]        = $json
    }
    try {
        $r = Invoke-WebRequest @args
        return @{ code = [int]$r.StatusCode; body = $r.Content }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $code = [int]$resp.StatusCode.value__
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $body = $reader.ReadToEnd()
            return @{ code = $code; body = $body }
        }
        return @{ code = 0; body = $_.Exception.Message }
    }
}

function LastCodeFromFile {
    if (-not (Test-Path $OtpFile)) { return $null }
    $line = Get-Content $OtpFile -Tail 1
    return [regex]::Match($line, 'code=(\d+)').Groups[1].Value
}

$ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$userLogin = "alice_$ts"

Write-Host "`n=== OTP service verification against $Base ===`n" -ForegroundColor Cyan

# --- 1. Регистрация и роли ------------------------------------------------
$rAdmin = Req POST "/api/auth/register" $null '{"login":"admin","password":"admin123","role":"ADMIN"}'
Check "register admin (201, либо 409 если уже есть)" ($rAdmin.code -in 201, 409)

$rUser = Req POST "/api/auth/register" $null "{`"login`":`"$userLogin`",`"password`":`"secret`"}"
Check "register user (201)" ($rUser.code -eq 201)
$userId = ($rUser.body | ConvertFrom-Json).id

$rAdmin2 = Req POST "/api/auth/register" $null '{"login":"admin2","password":"x","role":"ADMIN"}'
Check "second admin rejected (409)" ($rAdmin2.code -eq 409)

$rDup = Req POST "/api/auth/register" $null "{`"login`":`"$userLogin`",`"password`":`"y`"}"
Check "duplicate login rejected (409)" ($rDup.code -eq 409)

# --- 2. Логин и токены ----------------------------------------------------
$adminTok = (Req POST "/api/auth/login" $null '{"login":"admin","password":"admin123"}').body | ConvertFrom-Json | Select-Object -Expand token
$userTok  = (Req POST "/api/auth/login" $null "{`"login`":`"$userLogin`",`"password`":`"secret`"}").body | ConvertFrom-Json | Select-Object -Expand token
Check "login returns tokens" ($adminTok -and $userTok)

$rBadLogin = Req POST "/api/auth/login" $null "{`"login`":`"$userLogin`",`"password`":`"nope`"}"
Check "wrong password rejected (401)" ($rBadLogin.code -eq 401)

# --- 3. Разграничение ролей ----------------------------------------------
Check "admin API без токена -> 401" ((Req GET "/api/admin/users" $null $null).code -eq 401)
Check "admin API под обычным пользователем -> 403" ((Req GET "/api/admin/users" $userTok $null).code -eq 403)

# --- 4. Конфигурация OTP (админ) -----------------------------------------
Check "config update length=4 ttl=120 (200)" ((Req PUT "/api/admin/config" $adminTok '{"codeLength":4,"ttlSeconds":120}').code -eq 200)
$cfg = (Req GET "/api/admin/config" $adminTok $null).body | ConvertFrom-Json
Check "config reads back length=4 ttl=120" ($cfg.codeLength -eq 4 -and $cfg.ttlSeconds -eq 120)
Check "invalid config rejected (400)" ((Req PUT "/api/admin/config" $adminTok '{"codeLength":99,"ttlSeconds":120}').code -eq 400)

# --- 5. Генерация и валидация OTP (канал FILE) ---------------------------
$rGen = Req POST "/api/otp/generate" $userTok '{"operationId":"transfer-42","channel":"FILE"}'
$gen = $rGen.body | ConvertFrom-Json
Check "OTP generated (201, ACTIVE)" ($rGen.code -eq 201 -and $gen.status -eq "ACTIVE")
$code = LastCodeFromFile
Check "OTP code length matches config (4)" ($code.Length -eq 4)

Check "wrong code rejected (400)" ((Req POST "/api/otp/validate" $userTok '{"operationId":"transfer-42","code":"0000"}').code -eq 400)
Check "correct code validates (200)" ((Req POST "/api/otp/validate" $userTok "{`"operationId`":`"transfer-42`",`"code`":`"$code`"}").code -eq 200)
Check "used code cannot be reused (400)" ((Req POST "/api/otp/validate" $userTok "{`"operationId`":`"transfer-42`",`"code`":`"$code`"}").code -eq 400)

# --- 6. Истечение кода ----------------------------------------------------
[void](Req PUT "/api/admin/config" $adminTok '{"codeLength":6,"ttlSeconds":10}')
[void](Req POST "/api/otp/generate" $userTok '{"operationId":"op-exp","channel":"FILE"}')
$expCode = LastCodeFromFile
Write-Host "  ...ждём 12с истечения TTL..." -ForegroundColor DarkGray
Start-Sleep -Seconds 12
Check "expired code rejected on validate (400)" ((Req POST "/api/otp/validate" $userTok "{`"operationId`":`"op-exp`",`"code`":`"$expCode`"}").code -eq 400)

# --- 7. Список и удаление пользователей (каскад) -------------------------
$users = (Req GET "/api/admin/users" $adminTok $null).body | ConvertFrom-Json
Check "list excludes admins" (@($users | Where-Object { $_.role -eq 'ADMIN' }).Count -eq 0)
Check "list contains our user" (@($users | Where-Object { $_.id -eq $userId }).Count -eq 1)

Check "delete user (200)" ((Req DELETE "/api/admin/users/$userId" $adminTok $null).code -eq 200)
$usersAfter = (Req GET "/api/admin/users" $adminTok $null).body | ConvertFrom-Json
Check "user gone after delete" (@($usersAfter | Where-Object { $_.id -eq $userId }).Count -eq 0)

# вернуть дефолтную конфигурацию
[void](Req PUT "/api/admin/config" $adminTok '{"codeLength":6,"ttlSeconds":300}')

# --- Итог -----------------------------------------------------------------
Write-Host "`n==== $script:passed passed, $script:failed failed ====`n" -ForegroundColor Cyan
if ($script:failed -gt 0) { exit 1 }
