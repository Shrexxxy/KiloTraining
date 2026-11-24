# Сервер продуктов и копий дневника

Для запуска нужны Docker Desktop и PowerShell 7. Выполняйте команды из папки `server`.

## Запуск и обновление

При первой настройке скопируйте шаблон, если `.env` ещё нет.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Заполните `POSTGRES_PASSWORD` и `API_KEY` в `.env`. Для нового тома используйте случайный шестнадцатеричный пароль. Для существующего тома сохраните установленный ранее пароль PostgreSQL. В `API_KEY` можно использовать уже настроенный ключ приложения или новый случайный шестнадцатеричный ключ.

```powershell
docker compose up -d --build
docker compose ps
Invoke-RestMethod http://127.0.0.1:5080/health
```

API применяет миграции при старте. Эти же команды подходят после обновления кода или `.env`. PostgreSQL публикуется только на `127.0.0.1:5433`, а данные хранятся в томе `server_products_data` при стандартном имени проекта Compose `server`. Для запросов к карточкам и копиям используйте настроенный ключ в заголовке `X-Api-Key`.

Ответ `/health` включает `status: ok`, `databaseAvailable` и `productCount`. Если PostgreSQL оценивает размер таблицы в 10 000 товаров или больше, `productCount` приблизительный. При меньшей оценке или отсутствии статистики сервер считает товары точно. Счётчик равен `null`, если PostgreSQL недоступен или таблица товаров ещё не создана.

В Docker Desktop откройте Settings → General и включите **Start Docker Desktop when you sign in to your computer**. Оба контейнера поднимаются вместе с Docker Desktop благодаря политике `unless-stopped`. После явной остановки контейнеров снова выполните `docker compose up -d`.

## Доступ с телефона по Wi‑Fi

Подключите телефон к той же доверенной локальной сети. На компьютере выполните `ipconfig` и найдите IPv4-адрес активного Wi‑Fi или Ethernet-адаптера. В приложении откройте «Настройки» → «Сервер продуктов». В поле «Адрес» укажите `http://<IPv4 компьютера>:5080`, в поле «Ключ» — значение `API_KEY` из `.env`. Нажмите «Проверить связь».

Для домашней сети выберите в Windows профиль «Частная сеть». Если брандмауэр блокирует запросы, добавьте правило из PowerShell, запущенного от имени администратора.

```powershell
New-NetFirewallRule -DisplayName 'KiloTraining API' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5080 -Profile Private -RemoteAddress LocalSubnet
```

Для проверки откройте `http://<IPv4 компьютера>:5080/health` на компьютере и на телефоне. Используйте HTTP API в доверенной локальной сети.

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

Запустите контейнеры и выполните скрипт из PowerShell 7.

```powershell
pwsh -NoProfile -File .\scripts\update-off.ps1
pwsh -NoProfile -File .\scripts\update-off.ps1 -File .\data\products.csv.gz
```

Обычный запуск скачивает официальную выгрузку во временный файл в контейнере API. `-File` передаёт локальную выгрузку. Поддерживаются архивы `.csv.gz` и распакованные `.csv` с разделителем-табуляцией. Итоговые счётчики и время работы сохраняются в `server/logs/`. При ошибке скрипт возвращает ненулевой код.

Для еженедельного обновления зарегистрируйте задание в Планировщике Windows от своего имени. Пример ниже запускает импорт по воскресеньям в 03:00 по местному времени. На момент запуска пользователь должен быть в системе, а Docker Desktop и API должны работать.

```powershell
$script = (Resolve-Path .\scripts\update-off.ps1).Path
$action = New-ScheduledTaskAction -Execute (Get-Command pwsh).Source `
    -Argument ('-NoProfile -NonInteractive -File "{0}"' -f $script)
$trigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek Sunday -At '03:00'
Register-ScheduledTask -TaskName 'KiloTraining Open Food Facts' -Action $action -Trigger $trigger
```

Для ручного импорта только товаров с тегом `en:russia` выполните:

```powershell
docker compose exec -T api dotnet /app/CalorieTraining.Server.dll import-off --only-russia
```

По умолчанию импорт обрабатывает товары всех стран. Опция `--only-russia` оставляет только товары с тегом `en:russia`. Признак `ru` выставляется по этому тегу или по префиксу 460–469 у 13-значного штрихкода. В поиске по названию сначала идут карточки телефона, затем товары с `ru`, затем остальные. При повторном запуске импорт обновляет изменившиеся карточки Open Food Facts и сохраняет карточки телефона. В конце команда выводит количество обработанных строк, добавления, обновления, пропуски, причины отбраковки и время.

База Open Food Facts распространяется по [лицензии ODbL](https://openfoodfacts.github.io/openfoodfacts-server/api/tutorials/license-be-on-the-legal-side/). При показе импортированной карточки пользователю укажите «Данные: Open Food Facts» со ссылкой на [Open Food Facts](https://openfoodfacts.org/).

## Тесты

При запущенном Docker команда `dotnet test CalorieTraining.Server.sln` поднимет отдельный PostgreSQL, применит миграции и выполнит проверки API. Для интеграционных тестов сервис из Compose запускать не требуется.

## Отправка карточек в Open Food Facts

Отправка выключена по умолчанию. Для настройки создайте учётную запись на нужной площадке
Open Food Facts. В локальном файле окружения сервера укажите имя пользователя в `OFF_USER`,
пароль в `OFF_PASSWORD` и идентификатор приложения с контактами в `OFF_USER_AGENT`.
Храните пароль в окружении сервера. Когда настройки готовы, задайте `OFF_ENABLED=true`.

По умолчанию используется тестовый адрес `https://world.openfoodfacts.net`. После слияния
на тестовой площадке проверьте создание карточки, заполнение пропусков, отказ при наличии
калорий и видимость сообщения о тестовой площадке в диалоге. Для рабочего адреса задайте
`OFF_BASE_URL=https://world.openfoodfacts.org` и используйте учётную запись рабочей площадки.

Запись выполняется по явному согласию пользователя. Перед отправкой сервер читает карточку
в Open Food Facts. Для записи используются только штрихкод, название, марка и пищевая
ценность на 100 г. Карточка с пищевой ценностью на порцию требует ручной проверки.

[Площадки API](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/) ·
[Поля записи](https://openfoodfacts.github.io/documentation/docs/Product-Opener/v2/products/post-cgi-product_jqm2.pl/) ·
[Авторизация и ответ записи](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorial-off-api/)
