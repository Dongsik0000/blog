---
title: "포트는 두 겹으로 열어야 합니다"
slug: "oracle-cloud-firewall-two-layers"
category: "development"
publishedAt: "2026-10-07"
summary: "보안 목록에서 포트를 열었는데도 접속이 안 되는 가장 흔한 이유인 Ubuntu 이미지의 iptables를 이해합니다."
series: "oracle-cloud"
chapter: "firewall"
seriesOrder: 1
---
## 이 글에서 할 일

[보안 목록](/posts/oracle-cloud-network-security-list)에서 80, 443을 열고 nginx까지 설치했는데 브라우저에서 접속이 안 되는 경우가 많습니다. Oracle Cloud를 처음 쓰는 사람이 가장 많이 막히는 지점입니다. 원인은 **서버 안에 방화벽이 하나 더 있기 때문**입니다.

## 요청이 서버에 도착하기까지

```text
브라우저
   │
   ▼
① Oracle Cloud 보안 목록      ← 클라우드 네트워크의 방화벽 (콘솔에서 설정)
   │  통과하면
   ▼
② Ubuntu iptables             ← 서버 안의 방화벽 (서버에서 명령으로 설정)
   │  통과하면
   ▼
③ nginx (80, 443에서 대기)
```

두 방화벽은 서로 모릅니다. ①에서 열어도 ②에서 막히면 요청은 nginx에 도착하지 못합니다. 그래서 **두 곳 모두에서** 열어야 합니다.

## 왜 서버 안에도 방화벽이 있나

Oracle Cloud가 제공하는 Ubuntu 이미지(플랫폼 이미지)에는 처음부터 iptables 규칙이 들어 있습니다. 기본 규칙을 보면 SSH(22)처럼 명시적으로 허용한 요청만 통과시키고, 마지막 줄에서 나머지를 모두 거부하는 구조입니다([다음 글](/posts/oracle-cloud-firewall-iptables-rules)에서 한 줄씩 읽습니다). 클라우드 방화벽이 실수로 열려도 서버가 한 번 더 막아 주는 **이중 방어**입니다.

공식 문서 "Best Practices for Your Compute Instance"는 이 규칙에 대해 두 가지를 강조합니다.

1. 플랫폼 이미지에는 부트 볼륨과 블록 볼륨이 연결된 저장 장치(iSCSI, `169.254.x.x:3260`)에 **root만** 접속하게 하는 규칙이 들어 있습니다. 이 규칙을 지우면 일반 사용자도 디스크에 접근할 수 있게 되므로 **지우지 말라**고 권고합니다.
2. Ubuntu 이미지에서 **UFW로 방화벽을 편집하지 말라**고 권고합니다. UFW를 쓰면 인스턴스가 부팅되지 않을 수 있다고 안내합니다.

UFW(Uncomplicated Firewall)는 Ubuntu에서 흔히 쓰는 쉬운 방화벽 도구라서 검색하면 UFW 사용법이 많이 나옵니다. **Oracle Cloud의 Ubuntu에서는 따라 하지 않고**, iptables를 직접 다룹니다.

## 증상으로 어디가 막혔는지 구분하기

| 증상 | 어디를 의심하나 |
|---|---|
| 브라우저가 한참 기다리다 시간 초과 (timeout) | ① 보안 목록. 요청이 응답 없이 버려짐 |
| 바로 "연결 거부" (connection refused) 류의 오류 | ② iptables가 거부했거나, ③ 그 포트에서 기다리는 프로그램이 없음 |

서버 안에서 프로그램이 정말 그 포트를 듣고 있는지는 다음 명령으로 확인합니다.

```bash
sudo ss -ltnp | grep -E ':80 |:443 '
```

```text
0.0.0.0:80   ... "nginx"
0.0.0.0:443  ... "nginx"
```

- `ss -ltnp`: 대기 중(**l**isten)인 **t**cp 포트를 **n**umber로, **p**rocess 이름과 함께 보여 줍니다.
- `0.0.0.0:80`: 모든 네트워크에서 80번을 듣는다는 뜻입니다. `127.0.0.1:80`이면 서버 안에서만 듣는 것입니다.

## 정리

- 요청은 **보안 목록 → iptables → 프로그램** 순서로 통과합니다.
- 두 방화벽 모두에서 열어야 합니다.
- Oracle Cloud Ubuntu에서는 **UFW를 쓰지 않고** iptables를 직접 다룹니다.
- Oracle이 넣어 둔 저장 장치 보호 규칙은 지우지 않습니다.

다음 글에서는 iptables 규칙을 한 줄씩 읽습니다.
