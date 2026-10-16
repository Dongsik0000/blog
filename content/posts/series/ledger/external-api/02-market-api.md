---
title: "환율과 주식 시세 API (캐시와 실패 처리)"
slug: "ledger-external-api-market-api"
category: "development"
publishedAt: "2026-10-08"
summary: "달러 결제 고정 항목과 미국 주식 자산 평가에 쓰는 Alpha Vantage API 클라이언트를 읽으며, 하루 요청 한도를 지키는 캐시와 요청 간격, 실패를 기억해 반복 호출을 막는 방법, BigDecimal로 원화를 계산하는 방법을 설명합니다."
series: "ledger"
chapter: "external-api"
seriesOrder: 2
---
## 이 글에서 할 일

Ledger에는 외화와 주식 기능이 있습니다.

- **달러 결제 고정 항목**: 매달 USD 20인 구독료를 결제일 환율로 원화 거래로 기록
- **미국 주식 자산**: 보유 수량을 적으면 `주가 × 수량 × 환율`로 원화 평가액을 자동 계산

시세는 **Alpha Vantage** API에서 받습니다. 무료 키는 하루 요청 수가 적어서(README 기준 하루 25회), **아껴 쓰는 것**이 이 클라이언트의 핵심입니다.

## 쓰는 기능

| 메서드 | API 기능 | 용도 |
|---|---|---|
| `usdKrw(fresh)` | `CURRENCY_EXCHANGE_RATE` | 현재 USD/KRW 환율 |
| `usdKrwAt(결제일, 오늘)` | `FX_DAILY` | 지난 결제일의 환율 (서버가 꺼져 있었을 때) |
| `stock(종목)` | `GLOBAL_QUOTE` | 주가 |
| `searchUsStocks(검색어)` | `SYMBOL_SEARCH` | 종목 검색 |

## 1. 캐시: 같은 질문은 다시 묻지 않기

`ledger/market/MarketDataClient.java`

```java
private static final Duration CACHE_TTL = Duration.ofHours(4);
private static final Duration FAILURE_TTL = Duration.ofMinutes(15);
private static final Duration REQUEST_INTERVAL = Duration.ofSeconds(2);
private final Map<String, Cached> cache = new HashMap<>();
private final Map<String, Instant> failures = new HashMap<>();
private Instant nextRequestAt = Instant.EPOCH;
```

```java
private JsonNode fetch(String key, String query, boolean fresh) {
    if (!isConfigured()) throw new MarketDataException();
    Instant now = Instant.now();
    Cached cached = cache.get(key);
    if (!fresh && cached != null && now.isBefore(cached.expiresAt())) return cached.value();
    ...
```

같은 키(`FX`, `STOCK:TSLA`, `SEARCH:apple`)로 4시간 안에 다시 물으면 저장해 둔 답을 돌려줍니다. 대시보드를 열 때마다 주식 평가를 갱신하는데, 캐시가 없으면 하루 한도를 금방 다 씁니다.

| 상황 | 캐시 유지 |
|---|---|
| 무료 키 (최근 거래일 종가) | 4시간 |
| 실시간 이용권 키 (`LEDGER_MARKET_REALTIME=true`) | 1분 (과거 환율, 검색은 4시간) |

`fresh=true`는 캐시를 건너뜁니다. 결제일에 **그날 환율**로 거래를 기록할 때 씁니다.

## 2. 실패도 기억하기

```java
Instant retryAt = failures.get(key);
if (retryAt != null && now.isBefore(retryAt)) throw new MarketDataException();
...
} catch (Exception e) {
    failures.put(key, now.plus(FAILURE_TTL));
    throw new MarketDataException();
}
```

API가 실패하면(한도 초과, 서버 오류) 그 키는 **15분 동안 다시 부르지 않습니다.** 실패한 요청을 화면이 열릴 때마다 다시 보내면 한도만 더 써서 상황이 나빠집니다.

## 3. 요청 간격

```java
long pauseMillis = Duration.between(Instant.now(), nextRequestAt).toMillis();
if (pauseMillis > 0) Thread.sleep(pauseMillis);
nextRequestAt = Instant.now().plus(REQUEST_INTERVAL);
```

주식을 여러 종목 가지고 있으면 연달아 요청하게 됩니다. 요청 사이에 최소 2초를 두어 짧은 시간에 몰아 보내지 않습니다.

## 4. 동시 접근: synchronized

```java
public synchronized FxQuote usdKrw(boolean fresh) { ... }
public synchronized StockQuote stock(String symbol) { ... }
```

캐시와 실패 기록은 일반 `HashMap`이고, 이 클라이언트는 빈 하나를 모든 요청이 함께 씁니다. 공개 메서드를 `synchronized`로 만들어 **한 번에 한 요청만** 들어오게 했습니다. 동시에 두 요청이 같은 시세를 물어도 API는 한 번만 부르고 둘째는 캐시를 씁니다. 시세 요청은 들어온 순서대로 하나씩 처리됩니다.

## 5. 응답을 믿지 않기

```java
if (response.statusCode() != 200) throw new MarketDataException();
JsonNode body = json.readTree(response.body());
if (body.has("Note") || body.has("Information") || body.has("Error Message")) throw new MarketDataException();
```

Alpha Vantage는 한도를 넘거나 키가 틀려도 **HTTP 200**에 안내 문장(`Note`, `Information`)을 담아 돌려줍니다. 이 경우를 실패로 처리하고, 가격이 양수인지, 날짜 형식이 맞는지까지 확인한 뒤에야 캐시에 넣습니다. 잘못된 응답을 캐시하면 4시간 동안 틀린 값을 씁니다.

종목 코드도 정해진 형식만 받습니다.

```java
if (symbol == null || !symbol.matches("[A-Z.]{1,12}")) throw new IllegalArgumentException("Invalid symbol");
```

검색 결과는 **미국 상장, USD, 주식이나 ETF**만 남깁니다(`parseUsStockMatches`). 다른 나라 주식은 USD 환율 계산이 맞지 않기 때문입니다. 이 걸러내기는 `MarketDataClientTest`로 검사합니다.

## 6. 원화 계산: BigDecimal

`ledger/market/MarketValue.java`

```java
public static long krw(BigDecimal usd, BigDecimal rate) {
    if (usd == null || rate == null || usd.signum() <= 0 || rate.signum() <= 0) {
        throw new IllegalArgumentException("Positive USD amount and rate required");
    }
    return usd.multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact();
}
```

| 부분 | 이유 |
|---|---|
| `BigDecimal` | `double`은 `0.1 + 0.2`처럼 오차가 생김. 돈 계산은 정확한 소수 타입 |
| `setScale(0, HALF_UP)` | 원 단위로 반올림 |
| `longValueExact()` | `long` 범위를 넘으면 조용히 잘리지 않고 예외 |

DB에도 달러 금액과 환율은 `NUMERIC`으로 저장합니다([테이블 설계](/posts/ledger-database-schema)).

## 7. 실패해도 화면은 동작하게

`AssetServiceImpl.refreshStocks` (일부)

```java
try {
    BigDecimal rate = marketData.usdKrw(false).rate();
    for (Map<String, Object> stock : stocks) {
        ...
    }
    return true;
} catch (MarketDataClient.MarketDataException | ArithmeticException e) {
    return false;
}
```

시세를 못 받으면 예외를 화면까지 올리지 않고 `false`를 돌려줍니다. **마지막으로 평가한 금액을 그대로 두고**, 대시보드는 "시세 갱신 실패"만 표시합니다. 외부 API 하나가 멈춰도 가계부 전체가 멈추면 안 되기 때문입니다.

### 지난 결제일의 환율

서버가 꺼져 있다가 결제일 뒤에 [따라잡기](/posts/ledger-domain-scheduler)로 기록할 때는, 오늘 환율이 아니라 **결제일 기준** 환율을 씁니다.

```java
// 앱이 멈춰 결제일을 놓친 경우에는 과거 결제일의 가장 최근 영업일 종가를 쓴다.
public synchronized FxQuote usdKrwAt(LocalDate dueDate, LocalDate today) {
    if (!dueDate.isBefore(today)) return usdKrw(true);
    // FX_DAILY에서 결제일 이전 7일 안의 가장 최근 종가
}
```

적용한 환율과 기준 시각은 처리 기록(`recurring_run.fx_rate`, `fx_as_of`)에 남겨, 나중에 금액이 왜 그렇게 나왔는지 확인할 수 있습니다.

## 정리

- 하루 요청 한도를 지키려고 결과를 4시간 캐시하고, 실패는 15분 기억하고, 요청 사이에 2초를 둡니다.
- 공유 캐시는 `synchronized`로 한 번에 한 요청만 다룹니다.
- HTTP 200 안의 오류 안내까지 확인하고, 검사를 통과한 응답만 캐시합니다.
- 원화 계산은 `BigDecimal`과 반올림 규칙으로 정확하게 합니다.
- 시세를 못 받아도 마지막 값을 유지하고 화면은 계속 동작합니다.
