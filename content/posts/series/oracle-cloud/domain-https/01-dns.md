---
title: "도메인을 서버에 연결하기 (DNS A 레코드)"
slug: "oracle-cloud-domain-https-dns"
category: "development"
publishedAt: "2026-10-07"
summary: "도메인 이름이 IP 주소로 바뀌는 DNS의 원리와, 도메인 업체에서 A 레코드를 등록해 example.com을 서버에 연결하는 과정을 설명합니다."
series: "oracle-cloud"
chapter: "domain-https"
seriesOrder: 1
---
## 이 글에서 할 일

공인 IP 주소는 외우기 어렵습니다. 그래서 `example.com` 같은 **도메인 이름**을 사서 서버의 IP에 연결합니다. 이 글에서는 연결의 원리인 DNS를 이해하고 실제로 레코드를 등록합니다.

## DNS: 인터넷의 전화번호부

브라우저에 `example.com`을 입력하면 다음 일이 일어납니다.

```text
① 브라우저: "example.com의 IP가 뭐죠?"  → DNS 서버에 질문
② DNS 서버: "○○○.○○○.○○○.○○○입니다"
③ 브라우저: 그 IP로 접속
```

이름을 IP로 바꿔 주는 시스템이 **DNS**(Domain Name System)입니다. 도메인을 산 곳(도메인 등록 업체)이나 DNS 서비스에 "이 이름은 이 IP"라는 기록을 남기는데, 이 기록을 **레코드**라고 합니다.

## 레코드 종류

| 종류 | 뜻 | 예 |
|---|---|---|
| A | 이름 → IPv4 주소 | `example.com` → 서버 공인 IP |
| AAAA | 이름 → IPv6 주소 | |
| CNAME | 이름 → 다른 이름 (별명) | `www` → `example.com` |
| NS | 이 도메인의 레코드를 관리하는 DNS 서버 | |

## 레코드를 어디서 등록하나

레코드는 그 도메인의 **네임서버**(NS)를 관리하는 곳에서 등록합니다. 보통은 도메인을 산 업체이지만, Cloudflare 같은 DNS 서비스로 옮겼다면 그곳입니다. 누구나 조회할 수 있는 공개 정보이므로 명령으로 확인할 수 있습니다.

```bash
dig NS example.com +short
```

결과로 나오는 이름을 보면 어느 업체인지 알 수 있습니다. 예를 들어 `ns.gabia.co.kr.`이 나오면 가비아, `....ns.cloudflare.com.`이 나오면 Cloudflare에서 레코드를 관리합니다.

## 등록된 레코드 보기

```bash
dig example.com +noall +answer
```

A 레코드가 등록되어 있다면 다음과 같은 형태로 나옵니다.

```text
example.com.      600  IN  A  <서버 공인 IP>
```

- **A 레코드**로 서버 공인 IP를 가리킵니다.
- `600`은 **TTL**(Time To Live)입니다. 다른 DNS 서버가 이 답을 600초(10분) 동안 기억해 둔다는 뜻입니다.

## 따라 하기: A 레코드 등록

1. 네임서버를 관리하는 곳(보통 도메인을 산 업체)의 **DNS 관리** 화면으로 이동합니다.
2. 레코드를 두 개 추가합니다.

| 호스트(이름) | 타입 | 값 | TTL |
|---|---|---|---|
| `@` | A | 서버 공인 IP | 600 |
| `www` | A | 서버 공인 IP | 600 |

- `@`는 도메인 자기 자신(`example.com`)을 뜻합니다.
- `www`는 `www.example.com`입니다.
- 공인 IP는 Oracle Cloud 콘솔의 인스턴스 상세 화면에서 복사합니다. IP를 고정하고 싶다면 등록 **전에** [예약 IP](/posts/oracle-cloud-network-public-ip)로 바꿔 둡니다.

<!-- 📷 스크린샷: DNS 레코드 등록 화면 -->

## 확인하기

레코드를 등록해도 바로 반영되지 않을 수 있습니다. 기존에 다른 값이 있었다면 TTL만큼 기다려야 합니다. 내 PC나 서버에서 확인합니다.

```bash
dig example.com +short          # macOS, Linux
nslookup example.com            # Windows도 가능
```

| 결과 | 의미 |
|---|---|
| 서버 공인 IP가 나옴 | 연결 완료 |
| 아무것도 안 나옴 | 아직 반영 전이거나 레코드 이름(`@`)을 잘못 넣었습니다 |
| 다른 IP가 나옴 | 예전 값이 캐시되어 있습니다. TTL만큼 기다립니다 |

이 단계가 끝나면 `http://example.com`으로 접속했을 때 요청이 서버에 도착합니다. 아직 nginx가 없으면 응답이 없습니다. 다음 글에서 nginx를 설치합니다.

## 정리

- DNS는 도메인 이름을 IP로 바꾸는 전화번호부입니다.
- **A 레코드**로 `@`(루트 도메인)과 `www`를 서버 공인 IP에 연결합니다.
- 레코드는 그 도메인의 **NS 레코드가 가리키는 곳**에서 등록합니다.
- 반영 확인은 `dig 도메인 +short`로 합니다.
