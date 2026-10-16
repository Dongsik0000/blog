---
title: "로그인 시도 속도 제한하기 (limit_req)"
slug: "oracle-cloud-nginx-rate-limit"
category: "development"
publishedAt: "2026-10-07"
summary: "비밀번호 무작위 대입 공격을 막기 위해 IP별로 로그인 요청 속도를 제한하는 limit_req_zone과 limit_req 설정을 원리부터 설명합니다."
series: "oracle-cloud"
chapter: "nginx"
seriesOrder: 8
---
## 이 글에서 할 일

로그인 화면은 공격자가 비밀번호를 수천 번 바꿔 가며 시도하는 **무작위 대입(brute force) 공격**의 표적이 됩니다. 앱에서 "몇 회 실패하면 잠금" 같은 장치를 만들더라도, 그 전에 nginx에서 **IP별로 요청 속도를 제한**해 두면 좋습니다. 앱까지 오기 전에 막으면 Tomcat과 데이터베이스의 부담도 줄어듭니다.

## 설정은 두 군데에 나뉩니다

| 지시어 | 하는 일 | 쓸 수 있는 블록 | 둘 파일 |
|---|---|---|---|
| `limit_req_zone` | 제한 규칙(영역)을 **정의** | http | `/etc/nginx/conf.d/shop-limit.conf` |
| `limit_req` | 정의한 규칙을 **적용** | location 등 | `/etc/nginx/snippets/shop.conf` |

### 정의: limit_req_zone

`/etc/nginx/conf.d/shop-limit.conf`

```nginx
limit_req_zone $binary_remote_addr zone=shop_login:1m rate=10r/m;
```

| 부분 | 뜻 |
|---|---|
| `$binary_remote_addr` | 방문자 IP를 기준으로 셉니다. IP마다 따로 제한합니다 (`binary`는 메모리를 덜 쓰는 형식) |
| `zone=shop_login:1m` | 기록을 담을 공유 메모리 이름과 크기. 1MB면 IP 수천 개를 기억할 수 있습니다 |
| `rate=10r/m` | **분당 10개** 요청. 즉 6초에 1개꼴 |

### 적용: limit_req

[리버스 프록시 글](/posts/oracle-cloud-nginx-reverse-proxy)의 `location /shop/` 안에 로그인 주소용 location을 하나 더 넣습니다.

`/etc/nginx/snippets/shop.conf` (일부)

```nginx
location /shop/ {
    proxy_pass http://127.0.0.1:8080;
    # (헤더 설정 생략)

    # 로그인, 가입 주소만 IP별 속도 제한
    location ~ ^/shop/(login|signup)$ {
        limit_req        zone=shop_login burst=5 nodelay;
        limit_req_status 429;
        proxy_pass         http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
    }
}
```

- `location ~ ^/shop/(login|signup)$`: 정규식으로 로그인과 가입 주소**만** 고릅니다. 다른 화면에는 제한이 걸리지 않습니다. 주소는 앱에 맞게 바꿉니다.
- `burst=5`: 순간적으로 5개까지는 초과를 허용합니다. 사람이 비밀번호를 잘못 쳐서 연달아 몇 번 누르는 정도는 통과시키기 위해서입니다.
- `nodelay`: 허용된 범위의 요청은 기다리게 하지 않고 바로 처리합니다.
- `limit_req_status 429;`: 제한에 걸리면 **429 Too Many Requests**로 응답합니다. 기본값은 503인데, 429가 "요청이 너무 많다"는 뜻을 정확히 전달합니다.

## 동작을 그림으로 보기

`rate=10r/m, burst=5`는 "**양동이**"로 생각하면 쉽습니다.

```text
양동이 크기: 5 (burst)
6초마다 1칸씩 비워짐 (분당 10개)

요청이 오면 양동이에 한 칸 채움
  ├─ 빈 칸이 있으면 → 통과
  └─ 가득 차 있으면 → 429 거부
```

한 IP가 1초에 20번 로그인을 시도하면, 처음 몇 번은 통과하고 나머지는 429로 거부됩니다. 이후로는 6초에 1번꼴로만 통과합니다.

## 안쪽 location에 proxy_pass를 다시 쓰는 이유

이 location은 `location /shop/` **안에** 중첩되어 있습니다. 그런데도 `proxy_pass`와 헤더 설정을 다시 적었습니다. `proxy_pass`는 바깥 location에서 **상속되지 않기 때문**입니다. 안쪽 location이 선택되면 그 안의 설정만으로 요청을 처리하므로, `proxy_pass`가 없으면 Tomcat으로 넘어가지 않습니다.

## 확인하기

서버 안에서 로그인 주소로 요청을 빠르게 여러 번 보내 봅니다. (실제 로그인 정보는 보내지 않고 응답 코드만 봅니다.)

```bash
for i in $(seq 1 10); do curl -s -o /dev/null -w "%{http_code} " -X POST https://example.com/shop/login; done; echo
```

처음 몇 개 뒤에 `429`가 보이면 제한이 동작하는 것입니다. 이 시험도 실제 제한 횟수에 포함되므로, 시험 직후 1~2분은 그 IP로 로그인이 거부될 수 있습니다.

## 정리

- `limit_req_zone`은 http 블록(conf.d)에서 **정의**하고, `limit_req`는 location에서 **적용**합니다.
- `$binary_remote_addr`로 IP마다, `rate`로 속도를, `burst`로 순간 허용량을 정합니다.
- 제한 대상은 로그인과 가입처럼 공격받는 주소로 좁힙니다.
- 중첩 location에는 `proxy_pass`를 다시 적어야 합니다.
