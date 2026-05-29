# OTP Protection Service

Backend-сервис для защиты операций одноразовыми кодами подтверждения (OTP).
Пользователь инициирует операцию, сервис генерирует временный код, рассылает его по
выбранному каналу (файл, e-mail, SMS через SMPP-эмулятор, Telegram) и затем проверяет код
при подтверждении операции.

## Стек

- **Java 17+**, система сборки **Maven**
- **PostgreSQL 17**, доступ через **JDBC** (без ORM)
- **com.sun.net.httpserver** — слой API
- **SLF4J + Logback** — логирование
- **Jakarta Mail (Angus)** — рассылка по e-mail
- **jSMPP** — рассылка по SMS через SMPP-эмулятор
- **Telegram Bot API** через `java.net.http.HttpClient`
- **Jackson** — сериализация JSON

## Архитектура (три слоя)

```
api/        — обработчики HTTP-запросов (com.sun.net.httpserver)
  handler/  — AuthHandler, AdminHandler, OtpHandler
service/    — бизнес-логика (UserService, OtpService, OtpConfigService, TokenService)
  notification/ — каналы рассылки (File/Email/Sms/Telegram + Dispatcher)
dao/        — доступ к БД через JDBC (UserDao, OtpConfigDao, OtpCodeDao)
db/         — подключение и инициализация схемы
model/      — доменные сущности (User, Role, OtpCode, OtpStatus, OtpConfig)
scheduler/  — фоновая пометка просроченных кодов
config/     — загрузка настроек
util/       — хеширование паролей, JSON
```

### Таблицы БД

| Таблица      | Назначение                                                                 |
|--------------|----------------------------------------------------------------------------|
| `users`      | логин, хеш пароля (PBKDF2 + соль), роль (`ADMIN` / `USER`)                  |
| `otp_config` | единственная строка (id всегда = 1, `CHECK`): длина кода и время жизни (с)  |
| `otp_codes`  | код, статус, привязка к операции и пользователю, время создания/истечения  |

Статусы OTP-кода: **ACTIVE**, **EXPIRED**, **USED**.
При удалении пользователя его OTP-коды удаляются каскадно (`ON DELETE CASCADE`).

## Настройка

Все настройки — в `src/main/resources`:

- `application.properties` — порт, подключение к БД, секрет/срок токена, дефолты OTP,
  интервал фоновой проверки, канал по умолчанию, путь к файлу кодов.
- `email.properties` — SMTP-параметры (логин, пароль, хост, порт).
- `sms.properties` — параметры SMPP-эмулятора (host/port/system_id/password).
- `telegram.properties` — токен бота, URL API, chatId.


## Сборка и запуск

```bash
mvn package                 
java -jar target/otp-service.jar
```

После старта сервис слушает `http://localhost:8080`.

## API

Все ответы — в формате JSON. Защищённые эндпоинты требуют заголовок
`Authorization: Bearer <token>`, полученный при логине.

### Аутентификация (публично)

| Метод | Путь                 | Тело запроса                                  | Описание |
|-------|----------------------|-----------------------------------------------|----------|
| POST  | `/api/auth/register` | `{"login","password","role":"USER\|ADMIN"}` | Регистрация. `role` по умолчанию `USER`. Второй `ADMIN` зарегистрировать нельзя. |
| POST  | `/api/auth/login`    | `{"login","password"}`                        | Возвращает `{"token": "..."}` с ограниченным сроком действия. |

### Администратор (роль `ADMIN`)

| Метод  | Путь                      | Тело / параметры                  | Описание |
|--------|---------------------------|-----------------------------------|----------|
| GET    | `/api/admin/config`       | —                                 | Текущая конфигурация OTP. |
| PUT    | `/api/admin/config`       | `{"codeLength":6,"ttlSeconds":300}` | Изменить длину кода и время жизни. |
| GET    | `/api/admin/users`        | —                                 | Список всех пользователей, кроме администраторов. |
| DELETE | `/api/admin/users/{id}`   | —                                 | Удалить пользователя и его OTP-коды. |

### Пользователь (любой аутентифицированный)

| Метод | Путь                 | Тело запроса | Описание |
|-------|----------------------|--------------|----------|
| POST  | `/api/otp/generate`  | `{"operationId","channel":"FILE\|EMAIL\|SMS\|TELEGRAM","recipient":"..."}` | Сгенерировать код для операции и отправить по каналу. `channel` опционален (по умолчанию из настроек). |
| POST  | `/api/otp/validate`  | `{"code","operationId":"(опционально)"}` | Проверить код. При успехе код переходит в `USED`. |

### Разграничение ролей

- Эндпоинты `/api/admin/*` доступны **только** при роли `ADMIN` (иначе `403`).
- Эндпоинты `/api/otp/*` требуют валидный токен (иначе `401`).

## Примеры (curl)

```bash
# 1. Регистрация администратора и пользователя
curl -X POST localhost:8080/api/auth/register -d '{"login":"admin","password":"admin123","role":"ADMIN"}'
curl -X POST localhost:8080/api/auth/register -d '{"login":"alice","password":"secret"}'

# 2. Логин -> получить токен
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -d '{"login":"alice","password":"secret"}')

# 3. Админ меняет конфигурацию 
curl -X PUT localhost:8080/api/admin/config \
     -H "Authorization: Bearer <ADMIN_TOKEN>" \
     -d '{"codeLength":6,"ttlSeconds":300}'

# 4. Пользователь генерирует код в файл
curl -X POST localhost:8080/api/otp/generate \
     -H "Authorization: Bearer <USER_TOKEN>" \
     -d '{"operationId":"transfer-42","channel":"FILE"}'

# 5. Пользователь подтверждает операцию кодом из файла otp-codes.txt
curl -X POST localhost:8080/api/otp/validate \
     -H "Authorization: Bearer <USER_TOKEN>" \
     -d '{"operationId":"transfer-42","code":"123456"}'
```

## Тестирование

В корне проекта лежат три PowerShell-скрипта. Все они автоматические: сами регистрируют
пользователя, получают токен, выполняют запросы и печатают `PASS/FAIL`.

| Скрипт | Что проверяет | Что нужно заранее |
|--------|---------------|-------------------|
| `verify.ps1`     | весь HTTP-сценарий и канал **FILE** | запущенный сервис |
| `test-email.ps1` | канал **EMAIL** сквозь эмулятор     | запущенный сервис |
| `test-sms.ps1`   | канал **SMS** сквозь эмулятор       | запущенный сервис + `mvn package` |

### Шаг 0. Подготовка (один раз)

```powershell
mvn package                      
```

Запустите сервис в **отдельном** окне и не закрывайте его:

```powershell
java -jar target/otp-service.jar
```

PostgreSQL должен быть доступен (`localhost:5432`, пользователь/пароль из
`application.properties`).

### Шаг 1. Основной прогон + канал FILE

```powershell
powershell -ExecutionPolicy Bypass -File .\verify.ps1
```

Проверяет: регистрацию и роли, запрет второго админа и дубликатов, логин/токены,
разграничение доступа (401/403), смену конфигурации, генерацию кода в файл `otp-codes.txt`,
валидацию, запрет повторного использования, истечение кода, список и каскадное удаление
пользователей. В конце — итог `N passed, M failed`.

### Шаг 2. Канал EMAIL (без реальной почты)

```powershell
powershell -ExecutionPolicy Bypass -File .\test-email.ps1
```

Скрипт сам компилирует и поднимает локальный фейковый SMTP-сервер
(`emulators/FakeSmtpServer.java`, порт `2525`, без авторизации) с захватом его вывода,
генерирует код по каналу EMAIL, убеждается, что письмо реально дошло до эмулятора,
вытаскивает из письма код и подтверждает им операцию. По завершении эмулятор гасится.
Печатает `PASS`/`FAIL`. 

### Шаг 3. Канал SMS (без оператора)

```powershell
powershell -ExecutionPolicy Bypass -File .\test-sms.ps1
```

Поднимает локальный фейковый SMPP-сервер (SMSC)
(`emulators/FakeSmppServer.java`, порт `2775`, принимает любой bind, библиотека jSMPP
берётся из `target/otp-service.jar`), отправляет код по каналу SMS, проверяет, что SMS
дошла до эмулятора, и валидирует код. `sms.properties` уже настроен на этот эмулятор.

>  Если есть необходимость **вручную** посмотреть приходящие сообщения,
> запустите эмулятор в отдельном окне: `.\emulators\start-smtp.ps1` или `.\emulators\start-smpp.ps1`
> (тогда письма/SMS печатаются в его консоли), и шлите запросы `POST /api/otp/generate` руками.

### Шаг 4. Канал TELEGRAM (нужен реальный бот)


Впишите в `telegram.properties` свои переменные среды для тестирования телеграмм бота
   ```properties
   telegram.botToken=<ТОКЕН>
   telegram.apiUrl=https://api.telegram.org/bot<ТОКЕН>/sendMessage
   telegram.chatId=<CHAT_ID>
   ```
Перезапустите сервис и отправьте код:
   ```powershell
   $base = "http://localhost:8080"
   $u = "tg_$(Get-Random)"; $pwd = "secret"
   Invoke-RestMethod -Uri "$base/api/auth/register" -Method Post -ContentType 'application/json' `
       -Body (@{ login=$u; password=$pwd } | ConvertTo-Json) | Out-Null
   $login = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json' `
       -Body (@{ login=$u; password=$pwd } | ConvertTo-Json)
   $headers = @{ Authorization = "Bearer $($login.token)" }
   Invoke-RestMethod -Uri "$base/api/otp/generate" -Method Post -Headers $headers -ContentType 'application/json' `
       -Body (@{ operationId = "op-tg-$(Get-Random)"; channel = "TELEGRAM" } | ConvertTo-Json)
   ```

Быстрая проверка токена и chatId **без сервиса** 
```powershell
$token = "<ТОКЕН>"; $chat = "<CHAT_ID>"
Invoke-RestMethod -Uri "https://api.telegram.org/bot$token/sendMessage" -Method Post `
    -Body @{ chat_id = $chat; text = "test 123456" }
```

### Конфиги без пересборки

Файлы `application.properties`, `email.properties`, `sms.properties`, `telegram.properties`
загружаются **сначала из рабочей директории** (корень проекта), и только потом из jar.
Значит, для смены SMTP-логина, токена бота или пароля БД достаточно отредактировать файл
рядом с jar и перезапустить сервис — **пересобирать необязательно**.

## Каналы рассылки

Канал задаётся полем `channel` в `POST /api/otp/generate`. Поле `recipient` — адрес для
канала (e-mail, телефон, chatId); для FILE не нужно, для остальных — обязательно
(кроме Telegram, где при пустом `recipient` берётся `chatId` из настроек).

- **FILE** — код дописывается в `otp-codes.txt` в корне проекта.

- **EMAIL** — SMTP через Jakarta Mail. **По умолчанию всё уже настроено на локальный
  эмулятор**

  Эмулятор в отдельном окне запустите
     ```powershell
     powershell -ExecutionPolicy Bypass -File .\emulators\start-smtp.ps1
     ```
     Он слушает `localhost:2525`; `email.properties` уже указывает на него.


- **SMS** — SMPP. По умолчанию всё настроено на локальный эмулятор из комплекта
  (`emulators/FakeSmppServer.java`)

    В отдельном окне:

     ```powershell
     powershell -ExecutionPolicy Bypass -File .\emulators\start-smpp.ps1
     ```
     Он слушает `localhost:2775` и принимает любой bind; `sms.properties` уже указывает на него.


- **TELEGRAM** — через бота Telegram Bot API:
  1. Создайте бота у `@BotFather`, получите токен.
  2. Напишите боту любое сообщение, затем откройте
     `https://api.telegram.org/bot<TOKEN>/getUpdates` и найдите `chat.id`.
  3. Впишите в `telegram.properties`: `telegram.botToken`, 
     `telegram.apiUrl=https://api.telegram.org/bot<TOKEN>/sendMessage`, `telegram.chatId`.
  4. Запрос: `{"operationId":"op1","channel":"TELEGRAM"}` (или с `recipient` = другой chatId) →
     сообщение придёт в Telegram.

## Фоновая пометка просроченных кодов

`OtpExpiryScheduler` раз в `otp.expiryScanIntervalSeconds` секунд (по умолчанию 30)
переводит все `ACTIVE`-коды с истёкшим сроком в статус `EXPIRED`.

## Логирование

Все запросы к API логируются (метод, путь, источник, код ответа, длительность),
а также ключевые доменные события (регистрация, логин, генерация/валидация кода,
изменение конфигурации, удаление пользователя). Вывод — в консоль и в `logs/otp-service.log`

## Безопасность

- Пароли хранятся в виде `PBKDF2WithHmacSHA256` с индивидуальной солью.
- Токен — компактный подписанный (HMAC-SHA256) JWT-подобный токен с полем истечения `exp`.
- Подпись и сравнение паролей/токенов выполняются в постоянное время.
