---
title: "iptables 규칙 읽고 80, 443 열기"
slug: "oracle-cloud-firewall-iptables-rules"
category: "development"
publishedAt: "2026-10-07"
summary: "Oracle Cloud Ubuntu 서버의 기본 iptables 규칙을 한 줄씩 해석하고, 규칙 순서가 왜 중요한지, 웹 포트를 여는 명령과 쓰지 않는 규칙을 지우는 방법을 다룹니다."
series: "oracle-cloud"
chapter: "firewall"
seriesOrder: 2
---
## 이 글에서 할 일

서버의 iptables 규칙을 읽는 법을 익히고, 웹 포트(80, 443)를 엽니다.

## iptables 기본 개념

iptables는 규칙을 **체인**(chain)이라는 목록에 담습니다. 중요한 체인은 두 개입니다.

| 체인 | 검사하는 요청 |
|---|---|
| `INPUT` | 밖에서 서버로 **들어오는** 요청 |
| `OUTPUT` | 서버에서 밖으로 **나가는** 요청 |

체인의 규칙은 **위에서부터 차례로** 검사합니다. 조건이 맞는 첫 규칙에서 결정이 나고, 그 아래 규칙은 보지 않습니다. 은행 창구 번호표처럼 순서가 전부입니다.

결정은 보통 둘 중 하나입니다.

- `ACCEPT`: 통과
- `REJECT`: 거부하고 거부했다고 알려 줌

## 처음 만든 서버의 INPUT 규칙

```bash
sudo iptables -S INPUT
```

서버를 막 만든 상태에서는 다음과 같은 규칙이 보입니다.

```text
-P INPUT ACCEPT
-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT
-A INPUT -p icmp -j ACCEPT
-A INPUT -i lo -j ACCEPT
-A INPUT -p tcp -m state --state NEW -m tcp --dport 22 -j ACCEPT
-A INPUT -j REJECT --reject-with icmp-host-prohibited
```

한 줄씩 읽어 봅니다.

| 줄 | 뜻 |
|---|---|
| `-P INPUT ACCEPT` | 기본 정책. 어떤 규칙에도 안 걸리면 통과. 하지만 마지막 줄이 모두 거부하므로 실제로는 여기까지 오지 않습니다 |
| `--state RELATED,ESTABLISHED` | 서버가 먼저 시작한 연결의 응답은 허용. 이 줄이 없으면 서버가 업데이트를 받을 때 응답이 막힙니다 |
| `-p icmp` | ping 같은 네트워크 진단 메시지 허용 |
| `-i lo` | 서버 자기 자신(`127.0.0.1`)끼리의 통신 허용. nginx가 같은 서버의 앱에 요청을 전달할 때 필요합니다 |
| `--state NEW ... --dport 22` | 새 SSH 접속 허용 |
| `-j REJECT ...` | **위에 해당하지 않는 모든 요청 거부** |

80, 443 규칙이 없으므로 지금은 웹 요청이 마지막 줄에서 거부됩니다.

### 왜 순서가 중요한가

마지막 `REJECT` 줄이 "나머지 전부 거부"입니다. 그래서 허용 규칙은 반드시 `REJECT`보다 **위에** 있어야 합니다. 맨 끝에 추가(`-A`, append)하면 `REJECT` 아래에 붙어 영원히 도달하지 못합니다. 그래서 새 규칙은 **맨 앞에 끼워 넣기**(`-I`, insert)로 추가합니다.

## 80, 443 열기

```bash
sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
```

- `-I INPUT`: INPUT 체인의 맨 앞에 넣습니다.
- `-p tcp --dport 80`: TCP 80번 포트로 들어오는 요청
- `-j ACCEPT`: 허용

다시 확인하면 두 규칙이 맨 위에 들어가 있습니다. 443을 나중에 넣었으므로 443이 맨 위, 80이 그다음입니다.

```text
-P INPUT ACCEPT
-A INPUT -p tcp -m tcp --dport 443 -j ACCEPT
-A INPUT -p tcp -m tcp --dport 80 -j ACCEPT
-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT
...
-A INPUT -j REJECT --reject-with icmp-host-prohibited
```

이 명령은 **지금 당장만** 적용됩니다. 재부팅하면 사라지므로 [다음 글](/posts/oracle-cloud-firewall-persist-rules)의 방법으로 저장해야 합니다.

## 쓰지 않는 규칙 지우기

테스트하느라 앱 서버 포트(예: 8080)를 열었다가 nginx 뒤로 옮기고 나면, 그 허용 규칙은 필요가 없어집니다. 앱이 `127.0.0.1`에서만 듣고 있으면 당장은 외부에서 접속되지 않지만, 나중에 앱 설정이 바뀌면 의도치 않게 외부에 노출될 수 있습니다. 쓰지 않는 허용 규칙은 지웁니다.

규칙을 지울 때는 `-S`로 본 줄에서 `-A`를 `-D`(delete)로 바꿔 그대로 씁니다.

```bash
sudo iptables -S INPUT | grep 8080
sudo iptables -D INPUT -p tcp -m tcp --dport 8080 -j ACCEPT
```

## 손대지 말아야 할 규칙: InstanceServices

`OUTPUT` 체인에는 Oracle이 넣어 둔 규칙 묶음이 있습니다.

```bash
sudo iptables -S OUTPUT
```

```text
-P OUTPUT ACCEPT
-A OUTPUT -d 169.254.0.0/16 -j InstanceServices
```

`169.254.x.x`로 나가는 요청은 `InstanceServices`라는 별도 체인에서 검사합니다. 이 체인의 규칙에는 모두 다음 주석이 붙어 있습니다.

```text
"See the Oracle-Provided Images section in the Oracle Cloud Infrastructure documentation
 for security impact of modifying or removing this rule"
```

부트 디스크 연결(iSCSI, 3260번), 메타데이터, 내부 DNS, 시간 동기화 같은 클라우드 기능을 위한 규칙입니다. [앞 글](/posts/oracle-cloud-firewall-two-layers)에서 본 것처럼 공식 문서가 지우지 말라고 권고하므로 **그대로 둡니다.** 특히 `iptables -F`(모든 규칙 삭제)는 실행하지 않습니다.

## 정리

- iptables는 **위에서부터** 검사하고 처음 맞는 규칙에서 결정합니다.
- 마지막에 "나머지 거부" 규칙이 있으므로 허용 규칙은 **`-I`로 앞에** 넣습니다.
- 쓰지 않는 허용 규칙은 `-D`로 지웁니다.
- `InstanceServices` 규칙과 `iptables -F`는 건드리지 않습니다.
