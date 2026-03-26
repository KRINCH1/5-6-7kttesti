# КТ-4. Нагрузочное тестирование производительности запросов

В этом задании для контрольной точки `КТ-4` проводится оценка производительности базы данных на разных объёмах данных: `1000`, `10000` и `100000` записей.

## Используемые запросы

### 1. Простой запрос на выборку

Выборка всех операций за условный последний период:

```sql
SELECT id, account_id, amount, created_at
FROM orders_bank
WHERE created_at >= ?
ORDER BY created_at DESC;
```

Проверяется скорость обычной выборки с фильтрацией по дате.

### 2. Сложный запрос с объединением таблиц

```sql
SELECT o.id, c.full_name, a.account_number, o.amount
FROM orders_bank o
JOIN accounts a ON o.account_id = a.id
JOIN customers c ON a.customer_id = c.id
WHERE o.status = ?
ORDER BY o.id DESC;
```

Проверяется производительность запроса с `JOIN` нескольких таблиц.

### 3. Запрос с агрегатными функциями

```sql
SELECT a.id, COUNT(o.id) AS order_count, SUM(o.amount) AS total_amount
FROM accounts a
JOIN orders_bank o ON o.account_id = a.id
GROUP BY a.id
HAVING SUM(o.amount) > ?;
```

Проверяется производительность агрегирования данных.

## Что оценивается

- время выполнения простого запроса;
- время выполнения сложного запроса с объединением таблиц;
- время выполнения агрегатного запроса;
- изменение времени выполнения при росте объёма данных.

## Практическая реализация

Для практической проверки используется тест:

- `src/test/java/com/bank/kt/QueryPerformanceTest.java`

Он:

- создаёт схему базы данных;
- генерирует тестовые данные;
- выполняет запросы для объёмов `1000`, `10000`, `100000`;
- выводит время выполнения каждого типа запроса в миллисекундах.

## Запуск

```powershell
mvn -s maven-settings.xml -Dtest=QueryPerformanceTest test
```

## Ожидаемый результат

После запуска в консоли появятся строки вида:

```text
volume=1000 | simple=... ms | join=... ms | aggregate=... ms
volume=10000 | simple=... ms | join=... ms | aggregate=... ms
volume=100000 | simple=... ms | join=... ms | aggregate=... ms
```

На основании этих значений можно сделать вывод, как рост объёма данных влияет на скорость выполнения разных типов запросов.
