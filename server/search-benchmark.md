# Замеры поиска продуктов

Сравнение выполнено на одной локальной копии PostgreSQL 17.7 в Docker Desktop. Контейнер `kilo-search-test` использовал отдельный том, без публикации портов. Полная выгрузка Open Food Facts была смонтирована только для чтения. Исходный сервер собран из `f8e4885`. Новый замерен после миграции и повторного импорта той же выгрузки.

Для каждой версии измерено полное время HTTP-запроса с чтением ответа, `limit=20`, по loopback контейнера. Перед фазой выполнялся `VACUUM ANALYZE`. Перед первой попыткой каждого запроса освобождались буферы таблицы `products` и её индексов в PostgreSQL через `pg_buffercache_evict`. Кэш ОС оставался в обычном состоянии. После первой попытки шли пять тёплых без очистки. Код ответа во всех замерах `200`. Планы снимались отдельными запросами через `auto_explain` с `ANALYZE` и `BUFFERS`.

## Время HTTP-запросов, мс

| Запрос | До, первый | До, лучший из 5 | До, худший из 5 | После, первый | После, лучший из 5 | После, худший из 5 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `мо` | 644 | 141 | 179 | 688 | 29 | 61 |
| `те` | 143 | 120 | 153 | 68 | 23 | 27 |
| `te` | 772 | 669 | 705 | 48 | 19 | 21 |
| `молоко` | 9 | 6 | 7 | 16 | 8 | 8 |
| `milk` | 223 | 184 | 198 | 283 | 279 | 296 |
| `творог простоквашино` | 5 | 3 | 4 | 9 | 3 | 4 |
| `простоквашино творог` | 4 | 3 | 3 | 4 | 3 | 4 |
| `сыр 5%` | 4 | 3 | 4 | 13 | 4 | 5 |
| `алтайская буренка` | 4 | 3 | 4 | 4 | 3 | 3 |
| `а×100` | 4 | 3 | 3 | 4 | 3 | 3 |

До изменений первый прогон занимал до 772 мс, тёплый до 705 мс. После изменений первый занимал до 688 мс при цели 2 000 мс, тёплый до 296 мс при цели 500 мс. Все десять запросов уложились в оба целевых значения.

## Импорт и миграция

- Исходный импорт занял 12 минут 10,989 секунды. Из 4 928 416 строк подошли 3 426 815. Создано 3 426 782 карточки, обновлено 15.
- Миграция `ImproveProductSearch`. 455 мс на выполнение сгенерированного SQL вместе с записью истории EF. Индекс строился `CONCURRENTLY` вне транзакции. Миграция не обновляет карточки.
- Повторный импорт занял 14 минут 43,875 секунды. Из 4 928 416 строк подошли 3 426 816. Создана 1 карточка, обновлено 2 455 482.

### Размер индексов `products`, байт

| Индекс | До | После повторного импорта |
| --- | ---: | ---: |
| `IX_products_barcode` | 143032320 | 173105152 |
| `ix_products_phone_without_barcode` | 8192 | 8192 |
| `ix_products_search_key` | 210124800 | 343728128 |
| `ix_products_short_search` | — | 1400832 |
| `PK_products` | 143286272 | 159670272 |

### Повторный импорт на рабочей базе

После развёртывания новой версии API и успешного применения миграции руководитель с согласия пользователя может выполнить из каталога `server` следующее. Путь указывает на локальную полную выгрузку.

```powershell
pwsh -NoProfile -File .\scripts\update-off.ps1 -File .\data\off.csv.gz
```

## Планы PostgreSQL

Для `мо`, `те` и `te` прежний план использовал `Parallel Seq Scan`. Новый план читает кандидатов из частичного индекса `ix_products_short_search` через `Bitmap Index Scan`. Для остальных семи запросов обе версии используют триграммный индекс `ix_products_search_key`. Запрос `milk` стал самым медленным в тёплой серии после обновления. Его максимум вырос со 198 до 296 мс.

<details>
<summary>мо</summary>

До

```text
118.083 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, similarity(p.search_key, $2) DESC, p.name, p.id
	LIMIT $3
	Query Parameters: $1 = '%мо%', $2 = 'мо', $3 = '20'
	Limit  (cost=93524.15..93526.48 rows=20 width=142) (actual rows=20 loops=1)
	  Buffers: shared hit=120 read=74674
	  ->  Gather Merge  (cost=93524.15..93556.82 rows=280 width=142) (actual rows=20 loops=1)
	        Workers Planned: 2
	        Workers Launched: 2
	        Buffers: shared hit=120 read=74674
	        ->  Sort  (cost=92524.12..92524.47 rows=140 width=142) (actual rows=15 loops=3)
	              Sort Key: ((source = 'phone'::text)) DESC, ru DESC, (similarity(search_key, 'мо'::text)) DESC, name, id
	              Sort Method: top-N heapsort  Memory: 34kB
	              Buffers: shared hit=120 read=74674
	              Worker 0:  Sort Method: top-N heapsort  Memory: 35kB
	              Worker 1:  Sort Method: top-N heapsort  Memory: 35kB
	              ->  Parallel Seq Scan on products p  (cost=0.00..92520.40 rows=140 width=142) (actual rows=1218 loops=3)
	                    Filter: (search_key ~~ '%мо%'::text)
	                    Rows Removed by Filter: 1141043
	                    Buffers: shared read=74674
```

После

```text
75.064 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE (p.source = 'phone' OR p.ru) AND strpos(p.search_key, $1) - 1 >= 0
	ORDER BY p.source = 'phone' DESC, p.ru DESC, p.name ~* ('(?p)' || $2) DESC, p.search_key ~ ('(?p)' || $3) DESC, similarity(p.search_key, $4) DESC, p.name, p.id
	LIMIT $5
	Query Parameters: $1 = 'мо', $2 = '^[[:space:][:punct:]]*мо', $3 = '^(?= *(?:.* )?мо)', $4 = 'мо', $5 = '20'
	Limit  (cost=52337.83..52337.88 rows=20 width=151) (actual rows=20 loops=1)
	  Buffers: shared hit=11 read=11661
	  ->  Sort  (cost=52337.83..52355.34 rows=7004 width=151) (actual rows=20 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, ((name ~* '(?p)^[[:space:][:punct:]]*мо'::text)) DESC, ((search_key ~ '(?p)^(?= *(?:.* )?мо)'::text)) DESC, (similarity(search_key, 'мо'::text)) DESC, name, id
	        Sort Method: top-N heapsort  Memory: 37kB
	        Buffers: shared hit=11 read=11661
	        ->  Bitmap Heap Scan on products p  (cost=791.09..52151.46 rows=7004 width=151) (actual rows=3065 loops=1)
	              Recheck Cond: ((source = 'phone'::text) OR ru)
	              Filter: ((strpos(search_key, 'мо'::text) - 1) >= 0)
	              Rows Removed by Filter: 18883
	              Heap Blocks: exact=11491
	              Buffers: shared read=11661
	              ->  Bitmap Index Scan on ix_products_short_search  (cost=0.00..789.34 rows=21011 width=0) (actual rows=21948 loops=1)
	                    Buffers: shared read=170
```

</details>

<details>
<summary>те</summary>

До

```text
119.272 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, similarity(p.search_key, $2) DESC, p.name, p.id
	LIMIT $3
	Query Parameters: $1 = '%те%', $2 = 'те', $3 = '20'
	Limit  (cost=93965.86..93968.19 rows=20 width=142) (actual rows=20 loops=1)
	  Buffers: shared hit=216 read=74578
	  ->  Gather Merge  (cost=93965.86..97259.36 rows=28228 width=142) (actual rows=20 loops=1)
	        Workers Planned: 2
	        Workers Launched: 2
	        Buffers: shared hit=216 read=74578
	        ->  Sort  (cost=92965.84..93001.12 rows=14114 width=142) (actual rows=17 loops=3)
	              Sort Key: ((source = 'phone'::text)) DESC, ru DESC, (similarity(search_key, 'те'::text)) DESC, name, id
	              Sort Method: top-N heapsort  Memory: 34kB
	              Buffers: shared hit=216 read=74578
	              Worker 0:  Sort Method: top-N heapsort  Memory: 36kB
	              Worker 1:  Sort Method: top-N heapsort  Memory: 34kB
	              ->  Parallel Seq Scan on products p  (cost=0.00..92590.27 rows=14114 width=142) (actual rows=855 loops=3)
	                    Filter: (search_key ~~ '%те%'::text)
	                    Rows Removed by Filter: 1141405
	                    Buffers: shared hit=96 read=74578
```

После

```text
19.123 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE (p.source = 'phone' OR p.ru) AND strpos(p.search_key, $1) - 1 >= 0
	ORDER BY p.source = 'phone' DESC, p.ru DESC, p.name ~* ('(?p)' || $2) DESC, p.search_key ~ ('(?p)' || $3) DESC, similarity(p.search_key, $4) DESC, p.name, p.id
	LIMIT $5
	Query Parameters: $1 = 'те', $2 = '^[[:space:][:punct:]]*т[её]', $3 = '^(?= *(?:.* )?те)', $4 = 'те', $5 = '20'
	Limit  (cost=52337.83..52337.88 rows=20 width=151) (actual rows=20 loops=1)
	  Buffers: shared hit=11661
	  ->  Sort  (cost=52337.83..52355.34 rows=7004 width=151) (actual rows=20 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, ((name ~* '(?p)^[[:space:][:punct:]]*т[её]'::text)) DESC, ((search_key ~ '(?p)^(?= *(?:.* )?те)'::text)) DESC, (similarity(search_key, 'те'::text)) DESC, name, id
	        Sort Method: top-N heapsort  Memory: 35kB
	        Buffers: shared hit=11661
	        ->  Bitmap Heap Scan on products p  (cost=791.09..52151.46 rows=7004 width=151) (actual rows=1599 loops=1)
	              Recheck Cond: ((source = 'phone'::text) OR ru)
	              Filter: ((strpos(search_key, 'те'::text) - 1) >= 0)
	              Rows Removed by Filter: 20349
	              Heap Blocks: exact=11491
	              Buffers: shared hit=11661
	              ->  Bitmap Index Scan on ix_products_short_search  (cost=0.00..789.34 rows=21011 width=0) (actual rows=21948 loops=1)
	                    Buffers: shared hit=170
```

</details>

<details>
<summary>te</summary>

До

```text
659.631 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, similarity(p.search_key, $2) DESC, p.name, p.id
	LIMIT $3
	Query Parameters: $1 = '%te%', $2 = 'te', $3 = '20'
	Limit  (cost=104848.87..104851.20 rows=20 width=142) (actual rows=20 loops=1)
	  Buffers: shared hit=312 read=74482
	  ->  Gather Merge  (cost=104848.87..188483.24 rows=716816 width=142) (actual rows=20 loops=1)
	        Workers Planned: 2
	        Workers Launched: 2
	        Buffers: shared hit=312 read=74482
	        ->  Sort  (cost=103848.85..104744.87 rows=358408 width=142) (actual rows=16 loops=3)
	              Sort Key: ((source = 'phone'::text)) DESC, ru DESC, (similarity(search_key, 'te'::text)) DESC, name, id
	              Sort Method: top-N heapsort  Memory: 33kB
	              Buffers: shared hit=312 read=74482
	              Worker 0:  Sort Method: top-N heapsort  Memory: 33kB
	              Worker 1:  Sort Method: top-N heapsort  Memory: 34kB
	              ->  Parallel Seq Scan on products p  (cost=0.00..94311.74 rows=358408 width=142) (actual rows=252757 loops=3)
	                    Filter: (search_key ~~ '%te%'::text)
	                    Rows Removed by Filter: 889503
	                    Buffers: shared hit=192 read=74482
	JIT:
	  Functions: 13
	  Options: Inlining false, Optimization false, Expressions true, Deforming true
```

После

```text
15.936 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE (p.source = 'phone' OR p.ru) AND strpos(p.search_key, $1) - 1 >= 0
	ORDER BY p.source = 'phone' DESC, p.ru DESC, p.name ~* ('(?p)' || $2) DESC, p.search_key ~ ('(?p)' || $3) DESC, similarity(p.search_key, $4) DESC, p.name, p.id
	LIMIT $5
	Query Parameters: $1 = 'te', $2 = '^[[:space:][:punct:]]*te', $3 = '^(?= *(?:.* )?te)', $4 = 'te', $5 = '20'
	Limit  (cost=52337.83..52337.88 rows=20 width=151) (actual rows=20 loops=1)
	  Buffers: shared hit=11661
	  ->  Sort  (cost=52337.83..52355.34 rows=7004 width=151) (actual rows=20 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, ((name ~* '(?p)^[[:space:][:punct:]]*te'::text)) DESC, ((search_key ~ '(?p)^(?= *(?:.* )?te)'::text)) DESC, (similarity(search_key, 'te'::text)) DESC, name, id
	        Sort Method: top-N heapsort  Memory: 34kB
	        Buffers: shared hit=11661
	        ->  Bitmap Heap Scan on products p  (cost=791.09..52151.46 rows=7004 width=151) (actual rows=1438 loops=1)
	              Recheck Cond: ((source = 'phone'::text) OR ru)
	              Filter: ((strpos(search_key, 'te'::text) - 1) >= 0)
	              Rows Removed by Filter: 20510
	              Heap Blocks: exact=11491
	              Buffers: shared hit=11661
	              ->  Bitmap Index Scan on ix_products_short_search  (cost=0.00..789.34 rows=21011 width=0) (actual rows=21948 loops=1)
	                    Buffers: shared hit=170
```

</details>

<details>
<summary>молоко</summary>

До

```text
5.175 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, similarity(p.search_key, $2) DESC, p.name, p.id
	LIMIT $3
	Query Parameters: $1 = '%молоко%', $2 = 'молоко', $3 = '20'
	Limit  (cost=1345.22..1345.27 rows=20 width=142) (actual rows=20 loops=1)
	  Buffers: shared hit=7 read=508
	  ->  Sort  (cost=1345.22..1346.06 rows=335 width=142) (actual rows=20 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, (similarity(search_key, 'молоко'::text)) DESC, name, id
	        Sort Method: top-N heapsort  Memory: 36kB
	        Buffers: shared hit=7 read=508
	        ->  Bitmap Heap Scan on products p  (cost=57.76..1336.30 rows=335 width=142) (actual rows=664 loops=1)
	              Recheck Cond: (search_key ~~ '%молоко%'::text)
	              Rows Removed by Index Recheck: 19
	              Heap Blocks: exact=499
	              Buffers: shared hit=7 read=508
	              ->  Bitmap Index Scan on ix_products_search_key  (cost=0.00..57.67 rows=335 width=0) (actual rows=683 loops=1)
	                    Index Cond: (search_key ~~ '%молоко%'::text)
	                    Buffers: shared hit=7 read=9
```

После

```text
5.595 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, p.name ~* ('(?p)' || $2) DESC, p.search_key ~ ('(?p)' || $3) DESC, similarity(p.search_key, $4) DESC, p.name, p.id
	LIMIT $5
	Query Parameters: $1 = '%молоко%', $2 = '^[[:space:][:punct:]]*молоко', $3 = '^(?= *(?:.* )?молоко)', $4 = 'молоко', $5 = '20'
	Limit  (cost=1378.21..1378.26 rows=20 width=151) (actual rows=20 loops=1)
	  Buffers: shared hit=516 read=177
	  ->  Sort  (cost=1378.21..1379.07 rows=341 width=151) (actual rows=20 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, ((name ~* '(?p)^[[:space:][:punct:]]*молоко'::text)) DESC, ((search_key ~ '(?p)^(?= *(?:.* )?молоко)'::text)) DESC, (similarity(search_key, 'молоко'::text)) DESC, name, id
	        Sort Method: top-N heapsort  Memory: 36kB
	        Buffers: shared hit=516 read=177
	        ->  Bitmap Heap Scan on products p  (cost=57.79..1369.14 rows=341 width=151) (actual rows=727 loops=1)
	              Recheck Cond: (search_key ~~ '%молоко%'::text)
	              Rows Removed by Index Recheck: 22
	              Heap Blocks: exact=674
	              Buffers: shared hit=516 read=177
	              ->  Bitmap Index Scan on ix_products_search_key  (cost=0.00..57.70 rows=341 width=0) (actual rows=749 loops=1)
	                    Index Cond: (search_key ~~ '%молоко%'::text)
	                    Buffers: shared hit=7 read=12
```

</details>

<details>
<summary>milk</summary>

До

```text
212.970 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, similarity(p.search_key, $2) DESC, p.name, p.id
	LIMIT $3
	Query Parameters: $1 = '%milk%', $2 = 'milk', $3 = '20'
	Limit  (cost=64521.92..64521.97 rows=20 width=142) (actual rows=20 loops=1)
	  Buffers: shared hit=138 read=25764
	  ->  Sort  (cost=64521.92..64614.59 rows=37071 width=142) (actual rows=20 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, (similarity(search_key, 'milk'::text)) DESC, name, id
	        Sort Method: top-N heapsort  Memory: 35kB
	        Buffers: shared hit=138 read=25764
	        ->  Bitmap Heap Scan on products p  (cost=278.31..63535.47 rows=37071 width=142) (actual rows=46667 loops=1)
	              Recheck Cond: (search_key ~~ '%milk%'::text)
	              Rows Removed by Index Recheck: 1
	              Heap Blocks: exact=25844
	              Buffers: shared hit=138 read=25764
	              ->  Bitmap Index Scan on ix_products_search_key  (cost=0.00..269.04 rows=37071 width=0) (actual rows=46668 loops=1)
	                    Index Cond: (search_key ~~ '%milk%'::text)
	                    Buffers: shared hit=20 read=38
```

После

```text
278.728 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, p.name ~* ('(?p)' || $2) DESC, p.search_key ~ ('(?p)' || $3) DESC, similarity(p.search_key, $4) DESC, p.name, p.id
	LIMIT $5
	Query Parameters: $1 = '%milk%', $2 = '^[[:space:][:punct:]]*milk', $3 = '^(?= *(?:.* )?milk)', $4 = 'milk', $5 = '20'
	Limit  (cost=71168.89..71168.94 rows=20 width=151) (actual rows=20 loops=1)
	  Buffers: shared hit=1973 read=29699 written=1
	  ->  Sort  (cost=71168.89..71255.13 rows=34495 width=151) (actual rows=20 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, ((name ~* '(?p)^[[:space:][:punct:]]*milk'::text)) DESC, ((search_key ~ '(?p)^(?= *(?:.* )?milk)'::text)) DESC, (similarity(search_key, 'milk'::text)) DESC, name, id
	        Sort Method: top-N heapsort  Memory: 35kB
	        Buffers: shared hit=1973 read=29699 written=1
	        ->  Bitmap Heap Scan on products p  (cost=260.66..70250.99 rows=34495 width=151) (actual rows=49651 loops=1)
	              Recheck Cond: (search_key ~~ '%milk%'::text)
	              Rows Removed by Index Recheck: 8
	              Heap Blocks: exact=31583
	              Buffers: shared hit=1973 read=29699 written=1
	              ->  Bitmap Index Scan on ix_products_search_key  (cost=0.00..252.03 rows=34495 width=0) (actual rows=49659 loops=1)
	                    Index Cond: (search_key ~~ '%milk%'::text)
	                    Buffers: shared hit=32 read=57
```

</details>

<details>
<summary>творог простоквашино</summary>

До

```text
0.324 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, similarity(p.search_key, $2) DESC, p.name, p.id
	LIMIT $3
	Query Parameters: $1 = '%творог простоквашино%', $2 = 'творог простоквашино', $3 = '20'
	Limit  (cost=1535.01..1535.06 rows=20 width=142) (actual rows=11 loops=1)
	  Buffers: shared hit=34 read=32
	  ->  Sort  (cost=1535.01..1535.85 rows=335 width=142) (actual rows=11 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, (similarity(search_key, 'творог простоквашино'::text)) DESC, name, id
	        Sort Method: quicksort  Memory: 28kB
	        Buffers: shared hit=34 read=32
	        ->  Bitmap Heap Scan on products p  (cost=247.55..1526.10 rows=335 width=142) (actual rows=11 loops=1)
	              Recheck Cond: (search_key ~~ '%творог простоквашино%'::text)
	              Rows Removed by Index Recheck: 1
	              Heap Blocks: exact=4
	              Buffers: shared hit=34 read=32
	              ->  Bitmap Index Scan on ix_products_search_key  (cost=0.00..247.47 rows=335 width=0) (actual rows=12 loops=1)
	                    Index Cond: (search_key ~~ '%творог простоквашино%'::text)
	                    Buffers: shared hit=33 read=29
```

После

```text
0.609 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\' AND p.search_key LIKE $2 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, p.name ~* ('(?p)' || $3) DESC, p.search_key ~ ('(?p)' || $4) DESC, similarity(p.search_key, $5) DESC, p.name, p.id
	LIMIT $6
	Query Parameters: $1 = '%творог%', $2 = '%простоквашино%', $3 = '^[[:space:][:punct:]]*творог[[:space:][:punct:]]+простоквашино', $4 = '^(?= *(?:.* )?творог)(?= *(?:.* )?простоквашино)', $5 = 'творог простоквашино', $6 = '20'
	Limit  (cost=210.96..210.97 rows=1 width=151) (actual rows=20 loops=1)
	  Buffers: shared hit=28 read=44
	  ->  Sort  (cost=210.96..210.97 rows=1 width=151) (actual rows=20 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, ((name ~* '(?p)^[[:space:][:punct:]]*творог[[:space:][:punct:]]+простоквашино'::text)) DESC, ((search_key ~ '(?p)^(?= *(?:.* )?творог)(?= *(?:.* )?простоквашино)'::text)) DESC, (similarity(search_key, 'творог простоквашино'::text)) DESC, name, id
	        Sort Method: quicksort  Memory: 32kB
	        Buffers: shared hit=28 read=44
	        ->  Bitmap Heap Scan on products p  (cost=206.93..210.95 rows=1 width=151) (actual rows=24 loops=1)
	              Recheck Cond: ((search_key ~~ '%творог%'::text) AND (search_key ~~ '%простоквашино%'::text))
	              Heap Blocks: exact=15
	              Buffers: shared hit=28 read=44
	              ->  Bitmap Index Scan on ix_products_search_key  (cost=0.00..206.93 rows=1 width=0) (actual rows=24 loops=1)
	                    Index Cond: ((search_key ~~ '%творог%'::text) AND (search_key ~~ '%простоквашино%'::text))
	                    Buffers: shared hit=24 read=33
```

</details>

<details>
<summary>простоквашино творог</summary>

До

```text
0.171 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, similarity(p.search_key, $2) DESC, p.name, p.id
	LIMIT $3
	Query Parameters: $1 = '%простоквашино творог%', $2 = 'простоквашино творог', $3 = '20'
	Limit  (cost=1535.01..1535.06 rows=20 width=142) (actual rows=0 loops=1)
	  Buffers: shared hit=57 read=6
	  ->  Sort  (cost=1535.01..1535.85 rows=335 width=142) (actual rows=0 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, (similarity(search_key, 'простоквашино творог'::text)) DESC, name, id
	        Sort Method: quicksort  Memory: 25kB
	        Buffers: shared hit=57 read=6
	        ->  Bitmap Heap Scan on products p  (cost=247.55..1526.10 rows=335 width=142) (actual rows=0 loops=1)
	              Recheck Cond: (search_key ~~ '%простоквашино творог%'::text)
	              Rows Removed by Index Recheck: 12
	              Heap Blocks: exact=4
	              Buffers: shared hit=57 read=6
	              ->  Bitmap Index Scan on ix_products_search_key  (cost=0.00..247.47 rows=335 width=0) (actual rows=12 loops=1)
	                    Index Cond: (search_key ~~ '%простоквашино творог%'::text)
	                    Buffers: shared hit=53 read=6
```

После

```text
0.471 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\' AND p.search_key LIKE $2 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, p.name ~* ('(?p)' || $3) DESC, p.search_key ~ ('(?p)' || $4) DESC, similarity(p.search_key, $5) DESC, p.name, p.id
	LIMIT $6
	Query Parameters: $1 = '%простоквашино%', $2 = '%творог%', $3 = '^[[:space:][:punct:]]*простоквашино[[:space:][:punct:]]+творог', $4 = '^(?= *(?:.* )?простоквашино)(?= *(?:.* )?творог)', $5 = 'простоквашино творог', $6 = '20'
	Limit  (cost=210.96..210.97 rows=1 width=151) (actual rows=20 loops=1)
	  Buffers: shared hit=72
	  ->  Sort  (cost=210.96..210.97 rows=1 width=151) (actual rows=20 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, ((name ~* '(?p)^[[:space:][:punct:]]*простоквашино[[:space:][:punct:]]+творог'::text)) DESC, ((search_key ~ '(?p)^(?= *(?:.* )?простоквашино)(?= *(?:.* )?творог)'::text)) DESC, (similarity(search_key, 'простоквашино творог'::text)) DESC, name, id
	        Sort Method: quicksort  Memory: 32kB
	        Buffers: shared hit=72
	        ->  Bitmap Heap Scan on products p  (cost=206.93..210.95 rows=1 width=151) (actual rows=24 loops=1)
	              Recheck Cond: ((search_key ~~ '%простоквашино%'::text) AND (search_key ~~ '%творог%'::text))
	              Heap Blocks: exact=15
	              Buffers: shared hit=72
	              ->  Bitmap Index Scan on ix_products_search_key  (cost=0.00..206.93 rows=1 width=0) (actual rows=24 loops=1)
	                    Index Cond: ((search_key ~~ '%простоквашино%'::text) AND (search_key ~~ '%творог%'::text))
	                    Buffers: shared hit=57
```

</details>

<details>
<summary>сыр 5%</summary>

До

```text
0.254 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, similarity(p.search_key, $2) DESC, p.name, p.id
	LIMIT $3
	Query Parameters: $1 = '%сыр 5\%%', $2 = 'сыр 5%', $3 = '20'
	Limit  (cost=1345.22..1345.27 rows=20 width=142) (actual rows=0 loops=1)
	  Buffers: shared hit=16 read=17
	  ->  Sort  (cost=1345.22..1346.06 rows=335 width=142) (actual rows=0 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, (similarity(search_key, 'сыр 5%'::text)) DESC, name, id
	        Sort Method: quicksort  Memory: 25kB
	        Buffers: shared hit=16 read=17
	        ->  Bitmap Heap Scan on products p  (cost=57.76..1336.30 rows=335 width=142) (actual rows=0 loops=1)
	              Recheck Cond: (search_key ~~ '%сыр 5\%%'::text)
	              Rows Removed by Index Recheck: 1
	              Heap Blocks: exact=1
	              Buffers: shared hit=16 read=17
	              ->  Bitmap Index Scan on ix_products_search_key  (cost=0.00..57.67 rows=335 width=0) (actual rows=1 loops=1)
	                    Index Cond: (search_key ~~ '%сыр 5\%%'::text)
	                    Buffers: shared hit=16 read=16
```

После

```text
4.313 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\' AND (strpos(p.search_key, $2) - 1 >= 0 OR strpos(p.search_key, $3) - 1 >= 0)
	ORDER BY p.source = 'phone' DESC, p.ru DESC, p.name ~* ('(?p)' || $4) DESC, p.search_key ~ ('(?p)' || $5) DESC, similarity(p.search_key, $6) DESC, p.name, p.id
	LIMIT $7
	Query Parameters: $1 = '%сыр%', $2 = '5%', $3 = '5 %', $4 = '^[[:space:][:punct:]]*сыр[[:space:][:punct:]]+5[[:space:]]*%', $5 = '^(?= *(?:.* )?сыр)(?= *(?:.* )?5[[:space:]]*%)', $6 = 'сыр 5%', $7 = '20'
	Limit  (cost=1338.86..1338.91 rows=20 width=151) (actual rows=20 loops=1)
	  Buffers: shared hit=268 read=962
	  ->  Sort  (cost=1338.86..1339.34 rows=190 width=151) (actual rows=20 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, ((name ~* '(?p)^[[:space:][:punct:]]*сыр[[:space:][:punct:]]+5[[:space:]]*%'::text)) DESC, ((search_key ~ '(?p)^(?= *(?:.* )?сыр)(?= *(?:.* )?5[[:space:]]*%)'::text)) DESC, (similarity(search_key, 'сыр 5%'::text)) DESC, name, id
	        Sort Method: quicksort  Memory: 35kB
	        Buffers: shared hit=268 read=962
	        ->  Bitmap Heap Scan on products p  (cost=18.85..1333.81 rows=190 width=151) (actual rows=29 loops=1)
	              Recheck Cond: (search_key ~~ '%сыр%'::text)
	              Filter: (((strpos(search_key, '5%'::text) - 1) >= 0) OR ((strpos(search_key, '5 %'::text) - 1) >= 0))
	              Rows Removed by Filter: 1491
	              Heap Blocks: exact=1225
	              Buffers: shared hit=268 read=962
	              ->  Bitmap Index Scan on ix_products_search_key  (cost=0.00..18.80 rows=341 width=0) (actual rows=1520 loops=1)
	                    Index Cond: (search_key ~~ '%сыр%'::text)
	                    Buffers: shared hit=3 read=2
```

</details>

<details>
<summary>алтайская буренка</summary>

До

```text
0.184 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, similarity(p.search_key, $2) DESC, p.name, p.id
	LIMIT $3
	Query Parameters: $1 = '%алтайская буренка%', $2 = 'алтайская буренка', $3 = '20'
	Limit  (cost=1496.11..1496.16 rows=20 width=142) (actual rows=1 loops=1)
	  Buffers: shared hit=31 read=22
	  ->  Sort  (cost=1496.11..1496.95 rows=335 width=142) (actual rows=1 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, (similarity(search_key, 'алтайская буренка'::text)) DESC, name, id
	        Sort Method: quicksort  Memory: 25kB
	        Buffers: shared hit=31 read=22
	        ->  Bitmap Heap Scan on products p  (cost=208.65..1487.20 rows=335 width=142) (actual rows=1 loops=1)
	              Recheck Cond: (search_key ~~ '%алтайская буренка%'::text)
	              Heap Blocks: exact=1
	              Buffers: shared hit=31 read=22
	              ->  Bitmap Index Scan on ix_products_search_key  (cost=0.00..208.56 rows=335 width=0) (actual rows=1 loops=1)
	                    Index Cond: (search_key ~~ '%алтайская буренка%'::text)
	                    Buffers: shared hit=30 read=22
```

После

```text
0.250 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\' AND p.search_key LIKE $2 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, p.name ~* ('(?p)' || $3) DESC, p.search_key ~ ('(?p)' || $4) DESC, similarity(p.search_key, $5) DESC, p.name, p.id
	LIMIT $6
	Query Parameters: $1 = '%алтайская%', $2 = '%буренка%', $3 = '^[[:space:][:punct:]]*алтайская[[:space:][:punct:]]+бур[её]нка', $4 = '^(?= *(?:.* )?алтайская)(?= *(?:.* )?буренка)', $5 = 'алтайская буренка', $6 = '20'
	Limit  (cost=172.05..172.06 rows=1 width=151) (actual rows=4 loops=1)
	  Buffers: shared hit=26 read=18
	  ->  Sort  (cost=172.05..172.06 rows=1 width=151) (actual rows=4 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, ((name ~* '(?p)^[[:space:][:punct:]]*алтайская[[:space:][:punct:]]+бур[её]нка'::text)) DESC, ((search_key ~ '(?p)^(?= *(?:.* )?алтайская)(?= *(?:.* )?буренка)'::text)) DESC, (similarity(search_key, 'алтайская буренка'::text)) DESC, name, id
	        Sort Method: quicksort  Memory: 26kB
	        Buffers: shared hit=26 read=18
	        ->  Bitmap Heap Scan on products p  (cost=168.02..172.04 rows=1 width=151) (actual rows=4 loops=1)
	              Recheck Cond: ((search_key ~~ '%алтайская%'::text) AND (search_key ~~ '%буренка%'::text))
	              Heap Blocks: exact=4
	              Buffers: shared hit=26 read=18
	              ->  Bitmap Index Scan on ix_products_search_key  (cost=0.00..168.02 rows=1 width=0) (actual rows=4 loops=1)
	                    Index Cond: ((search_key ~~ '%алтайская%'::text) AND (search_key ~~ '%буренка%'::text))
	                    Buffers: shared hit=22 read=18
```

</details>

<details>
<summary>а×100</summary>

До

```text
0.017 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, similarity(p.search_key, $2) DESC, p.name, p.id
	LIMIT $3
	Query Parameters: $1 = '%аааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааа%', $2 = 'аааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааа', $3 = '20'
	Limit  (cost=1306.32..1306.37 rows=20 width=142) (actual rows=0 loops=1)
	  Buffers: shared hit=4
	  ->  Sort  (cost=1306.32..1307.15 rows=335 width=142) (actual rows=0 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, (similarity(search_key, 'аааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааа'::text)) DESC, name, id
	        Sort Method: quicksort  Memory: 25kB
	        Buffers: shared hit=4
	        ->  Bitmap Heap Scan on products p  (cost=18.85..1297.40 rows=335 width=142) (actual rows=0 loops=1)
	              Recheck Cond: (search_key ~~ '%аааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааа%'::text)
	              Buffers: shared hit=4
	              ->  Bitmap Index Scan on ix_products_search_key  (cost=0.00..18.77 rows=335 width=0) (actual rows=0 loops=1)
	                    Index Cond: (search_key ~~ '%аааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааа%'::text)
	                    Buffers: shared hit=4
```

После

```text
0.024 ms  plan:
	Query Text: SELECT p.id, p.barcode, p.brand, p.carbs, p.created_at, p.fat, p.kcal, p.name, p.protein, p.ru, p.search_key, p.source, p.updated_at
	FROM products AS p
	WHERE p.search_key LIKE $1 ESCAPE '\'
	ORDER BY p.source = 'phone' DESC, p.ru DESC, p.name ~* ('(?p)' || $2) DESC, p.search_key ~ ('(?p)' || $3) DESC, similarity(p.search_key, $4) DESC, p.name, p.id
	LIMIT $5
	Query Parameters: $1 = '%аааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааа%', $2 = '^[[:space:][:punct:]]*аааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааа', $3 = '^(?= *(?:.* )?аааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааа)', $4 = 'аааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааа', $5 = '20'
	Limit  (cost=1339.31..1339.36 rows=20 width=151) (actual rows=0 loops=1)
	  Buffers: shared hit=3 read=1
	  ->  Sort  (cost=1339.31..1340.16 rows=341 width=151) (actual rows=0 loops=1)
	        Sort Key: ((source = 'phone'::text)) DESC, ru DESC, ((name ~* '(?p)^[[:space:][:punct:]]*аааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааа'::text)) DESC, ((search_key ~ '(?p)^(?= *(?:.* )?аааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааа)'::text)) DESC, (similarity(search_key, 'аааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааа'::text)) DESC, name, id
	        Sort Method: quicksort  Memory: 25kB
	        Buffers: shared hit=3 read=1
	        ->  Bitmap Heap Scan on products p  (cost=18.89..1330.24 rows=341 width=151) (actual rows=0 loops=1)
	              Recheck Cond: (search_key ~~ '%аааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааа%'::text)
	              Buffers: shared hit=3 read=1
	              ->  Bitmap Index Scan on ix_products_search_key  (cost=0.00..18.80 rows=341 width=0) (actual rows=0 loops=1)
	                    Index Cond: (search_key ~~ '%аааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааааа%'::text)
	                    Buffers: shared hit=3 read=1
```

</details>
