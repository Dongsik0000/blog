---
title: "로그인 시도 제한"
slug: "ledger-auth-login-limit"
category: "development"
publishedAt: "2026-10-08"
summary: "5번 틀리면 5분 차단하는 LoginAttemptLimiter를 읽으며 동시 요청으로 제한을 우회하는 문제를 compute로 막은 방법, 메모리를 채우는 공격을 막는 키 상한, 시계를 바꿔 끼워 테스트하는 방법, nginx 제한과의 역할 분담을 설명합니다."
series: "ledger"
chapter: "auth"
seriesOrder: 3
---
## 이 글에서 할 일

비밀번호를 무한히 시도할 수 있으면 언젠가는 맞힙니다. Ledger는 **같은 대상에 5번 실패하면 5분 동안 막습니다.** 단순해 보이지만, 동시에 몰려오는 요청과 메모리 공격까지 생각하면 고려할 것이 많습니다. 커밋 "보안: 로그인 시도 제한의 메모리 소모 공격·동시 요청 우회 차단"이 그 과정입니다.

## 무엇을 단위로 셀까: 키

```java
// 시도 제한 키. 검증을 통과하지 못한 아이디(빈 값, 50자 초과)는 넣지 않는다 — 긴 키로 메모리를 채우는 공격 방지
static String loginAttemptKey(String username, String ip) {
    boolean valid = !username.isEmpty() && username.length() <= USERNAME_MAX;
    return "login|" + (valid ? username.toLowerCase(Locale.ROOT) : "") + "|" + ip;
}
```

| 기능 | 키 | 뜻 |
|---|---|---|
| 로그인 | `login|아이디|IP` | 이 IP에서 이 아이디로 |
| 가입 | `signup|IP` | 이 IP에서 초대 코드 시도 |
| 비밀번호 변경 | `password|사용자id` | 로그인한 사용자가 현재 비밀번호 확인 |

로그인을 **아이디 + IP**로 묶은 이유는, 아이디만으로 막으면 공격자가 남의 아이디로 5번 틀려서 **진짜 주인을 잠글** 수 있기 때문입니다. IP가 다르면 다른 키라 주인은 계속 로그인할 수 있습니다.

IP는 `RequestUtil.clientIp`가 구합니다. 서버에서 Tomcat 앞에는 nginx가 있어서 Tomcat이 보는 접속 주소는 항상 `127.0.0.1`입니다. 그래서 nginx가 넣어 주는 `X-Real-IP` 헤더를 쓰되, **요청이 127.0.0.1에서 왔을 때만** 믿습니다. 외부에서 직접 이 헤더를 넣어 IP를 속이는 것을 막기 위해서입니다.

## 핵심 코드

`ledger/cmmn/util/LoginAttemptLimiter.java`

```java
// 로그인·가입 코드 시도 제한. 같은 키로 5번 시도하면 5분 차단(성공하면 reset). 메모리에만 둔다(단일 서버).
// 시도 확인과 증가를 한 번에 해서, 동시에 몰려온 요청도 5번까지만 통과한다.
@Component
public class LoginAttemptLimiter {

    static final int MAX_ATTEMPTS = 5;
    static final long BLOCK_MILLIS = 5 * 60 * 1000L;
    private static final int DEFAULT_MAX_KEYS = 10_000; // 키 개수 상한(메모리 소모 공격 방지)

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    ...
}
```

### 쓰는 쪽

```java
if (!loginAttemptLimiter.tryAcquire(attemptKey)) {
    return Response.of(Constants.LOGIN_BLOCKED);   // 차단 중
}
// ... 비밀번호 확인 ...
loginAttemptLimiter.reset(attemptKey);              // 성공하면 기록 지움
```

**시도하기 전에** 한 번 세고, 성공하면 지웁니다. 실패했을 때 세는 방식이 아니라 "시도 = 1회"로 셉니다.

## 문제 1. 동시 요청으로 제한 넘기

처음 떠올리기 쉬운 코드는 이렇습니다.

```java
// 위험한 방식
Attempt a = attempts.get(key);
if (a != null && a.count >= 5) return false;   // ① 확인
a.count++;                                      // ② 증가
```

공격자가 요청 100개를 **동시에** 보내면, 100개 모두 ①에서 "아직 0번"을 보고 통과한 뒤 ②로 갑니다. 확인과 증가 사이에 틈이 있기 때문입니다(**경쟁 상태**, race condition).

Ledger는 `ConcurrentHashMap.compute`로 확인과 증가를 **한 번에** 합니다.

```java
boolean[] allowed = {false};
attempts.compute(key, (k, attempt) -> {
    if (attempt != null && attempt.blockedUntil > now) {
        return attempt; // 차단 중: 세지 않고 거절
    }
    if (attempt == null || attempt.blockedUntil != 0 || attempt.lastAttempt + BLOCK_MILLIS < now) {
        attempt = new Attempt(); // 처음이거나, 차단이 끝났거나, 마지막 시도가 오래됨
    }
    attempt.count++;
    attempt.lastAttempt = now;
    if (attempt.count >= MAX_ATTEMPTS) {
        attempt.blockedUntil = now + BLOCK_MILLIS;
    }
    allowed[0] = true;
    return attempt;
});
return allowed[0];
```

`compute`는 같은 키에 대해 람다를 **한 번에 하나씩** 실행합니다. 100개가 동시에 와도 앞의 요청이 센 결과를 다음 요청이 보게 되어, 5번까지만 통과합니다.

람다 안에서는 바깥의 지역 변수를 바꿀 수 없어서(`final`이어야 함) `boolean[] allowed` 배열을 결과 상자로 씁니다.

## 문제 2. 메모리 채우기

키는 메모리의 Map에 쌓입니다. 공격자가 아이디를 매번 바꿔 수백만 번 시도하면 Map이 끝없이 커져 서버 메모리가 바닥납니다.

```java
// 키가 상한만큼 차 있고 오래된 것을 정리해도 자리가 없으면 새 키는 false(막는 쪽으로 실패).
if (!attempts.containsKey(key) && attempts.size() >= maxKeys) {
    attempts.values().removeIf(a -> isStale(a, now));
    if (attempts.size() >= maxKeys) {
        return false;
    }
}
```

- 키는 최대 10,000개까지만 둡니다.
- 꽉 차면 오래된 것(5분 넘게 시도 없고 차단도 끝난 것)을 지웁니다.
- 그래도 자리가 없으면 **새 시도를 막습니다.** 보안 장치가 부족할 때 "통과시키는 쪽"이 아니라 "막는 쪽"으로 실패하게 한 것입니다.
- 키에 넣는 아이디는 50자 이하로 검사한 것만 씁니다. 아주 긴 아이디로 메모리를 채우는 것도 막습니다.

## 테스트할 수 있게: 시계 바꿔 끼우기

```java
private final LongSupplier clock;

public LoginAttemptLimiter() {
    this(System::currentTimeMillis, DEFAULT_MAX_KEYS);
}

LoginAttemptLimiter(LongSupplier clock, int maxKeys) { ... }
```

"5분 뒤에 풀리는지"를 테스트하려고 정말 5분을 기다릴 수는 없습니다. 현재 시각을 직접 부르지 않고 **시계 함수를 받아서** 씁니다. 운영에서는 실제 시계를, 테스트에서는 원하는 시각을 돌려주는 가짜 시계를 넣습니다. 키 상한도 테스트에서는 작게 넣어 "꽉 찼을 때"를 쉽게 만듭니다. 이 테스트는 `LoginAttemptLimiterTest`에 있습니다.

## nginx 제한과의 역할 분담

| | nginx `limit_req` | LoginAttemptLimiter |
|---|---|---|
| 단위 | IP | 아이디 + IP (기능별) |
| 기준 | 분당 요청 수 | 연속 실패 횟수 |
| 막는 것 | 짧은 시간의 대량 요청 | 천천히 하는 대입 |
| 위치 | 앱 앞 | 앱 안 |

nginx 설정은 [Oracle Cloud 시리즈의 속도 제한 글](/posts/oracle-cloud-nginx-rate-limit)에 있습니다. 둘은 서로를 대신하지 않고 겹쳐서 막습니다.

## 실행 환경과 기록의 수명

현재 Ledger는 단일 서버에서 실행하며 시도 기록을 앱의 메모리에 둡니다. 같은 앱 프로세스 안의 요청이 기록을 함께 사용하고, 앱을 재시작하면 기록은 초기화됩니다. DB의 사용자·거래 데이터와 별도로 유지하는 임시 기록입니다.

## 정리

- 로그인은 아이디 + IP 단위로 5번 시도하면 5분 막고, 성공하면 기록을 지웁니다.
- `ConcurrentHashMap.compute`로 확인과 증가를 한 번에 해 동시 요청 우회를 막습니다.
- 키 개수에 상한을 두고, 넘치면 막는 쪽으로 실패합니다.
- 시계를 주입받아 시간이 걸리는 동작도 바로 테스트합니다.
