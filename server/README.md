# Сервер продуктов и копий дневника

Для запуска нужны .NET SDK 10, Docker с Compose и PowerShell 7. Команды выполняются из папки `server`.

## PostgreSQL

```powershell
Copy-Item .env.example .env
```

Откройте `.env` и замените заглушку в `POSTGRES_PASSWORD` случайным шестнадцатеричным паролем. Используйте тот же пароль при последующих запусках с существующим томом. PostgreSQL задаёт пароль пользователя при первой инициализации тома.

```powershell
docker compose up -d --wait
```

PostgreSQL доступен на компьютере по адресу `127.0.0.1:5433`. Данные сохраняются в именованном томе `products_data`.

## Миграции и API

В текущем сеансе PowerShell задайте строку подключения и сгенерируйте ключ для телефона. Передайте ключ на телефон по доверенному каналу.

```powershell
$dbPassword = (Get-Content .env | Where-Object { $_ -match '^POSTGRES_PASSWORD=' }) -replace '^POSTGRES_PASSWORD=', ''
$env:ConnectionStrings__Products = "Host=127.0.0.1;Port=5433;Database=products;Username=products;Password=$dbPassword"
$env:Api__Key = [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Установите `dotnet-ef` командой `dotnet tool install --global dotnet-ef --version 10.0.15`. Если у вас уже установлена более ранняя версия, используйте `dotnet tool update --global dotnet-ef --version 10.0.15`.

```powershell
dotnet ef database update --project src/CalorieTraining.Server
dotnet run --project src/CalorieTraining.Server
```

Ключ можно также сохранить в user-secrets проекта под именем `Api:Key`. Для их загрузки запустите сервер с `DOTNET_ENVIRONMENT=Development`. Переменная `Api__Key` имеет приоритет.

По умолчанию API слушает HTTP на всех интерфейсах на порту `5080`. Порт можно изменить, задав `ASPNETCORE_URLS` или передав `-- --urls http://0.0.0.0:5081` при запуске. Проверка с этого компьютера в другом окне PowerShell:

```powershell
Invoke-RestMethod http://127.0.0.1:5080/health
```

Ожидаемый ответ — `{"status":"ok","databaseAvailable":true}`. Для запросов к карточкам передайте `X-Api-Key` с настроенным ключом.

## Доступ с телефона

Выполните `ipconfig` и найдите IPv4-адрес активного Wi-Fi или Ethernet-адаптера. Подключите телефон к той же сети и откройте `http://<IPv4 компьютера>:5080/health`. Если брандмауэр Windows блокирует запрос, разрешите входящие подключения к порту API для профиля «Частная сеть». Используйте HTTP API в доверенной локальной сети.

## Резервные копии дневника

После применения миграций сервер хранит копии в таблице `diary_backups` в PostgreSQL. Все запросы к копиям используют тот же заголовок `X-Api-Key`, что и запросы к продуктам.

| Запрос | Результат |
| --- | --- |
| `POST /backups` | Принимает исходный JSON в UTF-8 с `Content-Type: application/json`. Возвращает `201` и поля `id`, `createdAt` (UTC), `sizeBytes`, `format`. |
| `GET /backups` | Возвращает массив метаданных с теми же полями. Сначала идут новые копии. |
| `GET /backups/{id}` | Возвращает исходный JSON с типом `application/json` или `404`, если копии нет. |

При загрузке сервер проверяет, что тело является JSON-объектом с полем `format` вида `calorietraining/14`, где после косой черты идут цифры. Некорректное тело даёт `400`, размер свыше 20 МиБ — `413`, отсутствующий или неверный ключ — `401`. После каждой загрузки сервер оставляет 30 самых новых копий.

В приложении откройте «История» → «Резервная копия» → «На сервере». При запуске приложение пробует сделать фоновую копию, если с последней успешной автокопии прошло больше суток или автокопии ещё не было. При восстановлении приложение проверяет скачанный файл и просит подтвердить замену дневника.

Копии содержат личные данные о здоровье и хранятся в PostgreSQL в исходном виде. Держите HTTP API в доверенной домашней сети и ограничьте доступ к серверу и его тому PostgreSQL.

## Импорт Open Food Facts

Поднимите PostgreSQL, задайте `ConnectionStrings__Products` и примените миграции, как описано выше. Запускать API для импорта не требуется.

```powershell
dotnet run --project src/CalorieTraining.Server -- import-off
dotnet run --project src/CalorieTraining.Server -- import-off --file data/products.csv.gz
dotnet run --project src/CalorieTraining.Server -- import-off --url https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz
```

Команда без параметров читает официальную выгрузку по сети. Для повторного использования можно вручную сохранить архив в `server/data/` и передать его через `--file`. Поддерживаются архивы `.csv.gz` и распакованные `.csv` с разделителем-табуляцией. Скачивание и разбор идут потоком. Полный прогон архива объёмом около 1 ГБ занял примерно 18 минут на локальной базе Compose. Время зависит от сети и компьютера.

Импорт берёт товары с тегом `en:russia`. При повторном запуске он обновляет изменившиеся карточки Open Food Facts и сохраняет карточки телефона. В конце команда выводит количество обработанных строк, добавления, обновления, пропуски, причины отбраковки и время.

База Open Food Facts распространяется по [лицензии ODbL](https://openfoodfacts.github.io/openfoodfacts-server/api/tutorials/license-be-on-the-legal-side/). При показе импортированной карточки пользователю укажите «Данные: Open Food Facts» со ссылкой на [Open Food Facts](https://openfoodfacts.org/).

## Тесты

При запущенном Docker команда `dotnet test CalorieTraining.Server.sln` поднимет отдельный PostgreSQL, применит миграции и выполнит проверки API. Для интеграционных тестов сервис из Compose запускать не требуется.
