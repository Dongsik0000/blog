---
title: "iptables 규칙을 재부팅 후에도 유지하기"
slug: "oracle-cloud-firewall-persist-rules"
category: "development"
publishedAt: "2026-10-07"
summary: "iptables 명령은 재부팅하면 사라집니다. iptables-persistent로 규칙을 파일에 저장하고 부팅 때 다시 불러오는 방법을 설명합니다."
series: "oracle-cloud"
chapter: "firewall"
seriesOrder: 3
---
## 이 글에서 할 일

[앞 글](/posts/oracle-cloud-firewall-iptables-rules)의 `iptables -I` 명령은 메모리에만 적용됩니다. 서버를 재부팅하면 규칙이 저장된 파일 내용으로 되돌아갑니다. 이 글에서는 바꾼 규칙을 파일에 저장해 재부팅 후에도 유지합니다.

## 규칙이 저장되는 곳

규칙을 파일에 저장하고 부팅 때 불러오는 일은 **iptables-persistent**(와 그 핵심인 **netfilter-persistent**)가 맡습니다. 설치되어 있는지 먼저 확인합니다.

```bash
dpkg -l iptables-persistent netfilter-persistent | awk '/^ii/{print $2, $3}'
ls /etc/iptables/
```

두 패키지 이름과 버전, 그리고 `rules.v4  rules.v6` 두 파일이 보이면 준비된 상태입니다. 패키지가 없다면 설치합니다.

```bash
sudo apt install iptables-persistent
```

| 파일 | 내용 |
|---|---|
| `/etc/iptables/rules.v4` | IPv4 규칙 |
| `/etc/iptables/rules.v6` | IPv6 규칙 |

부팅할 때 `netfilter-persistent`가 이 파일을 읽어 규칙을 적용합니다.

## 저장 파일 읽기

`rules.v4`는 `iptables-save` 형식입니다. 앞 글에서 80, 443을 열고 저장했다면 INPUT 부분은 다음과 같습니다.

`/etc/iptables/rules.v4` (일부)

```text
*filter
:INPUT ACCEPT [0:0]
:FORWARD ACCEPT [0:0]
:OUTPUT ACCEPT [...]
:InstanceServices - [0:0]
-A INPUT -p tcp -m tcp --dport 443 -j ACCEPT
-A INPUT -p tcp -m tcp --dport 80 -j ACCEPT
-A INPUT -m state --state RELATED,ESTABLISHED -j ACCEPT
-A INPUT -p icmp -j ACCEPT
-A INPUT -i lo -j ACCEPT
-A INPUT -p tcp -m state --state NEW -m tcp --dport 22 -j ACCEPT
-A INPUT -j REJECT --reject-with icmp-host-prohibited
...
COMMIT
```

- `*filter`: 아래 규칙이 filter 표(일반 방화벽 규칙)에 속한다는 뜻입니다.
- `:INPUT ACCEPT`: 체인과 기본 정책 선언
- `-A ...`: 규칙. 파일에 적힌 순서대로 들어갑니다.
- `COMMIT`: 이 표의 끝

현재 메모리의 규칙(`iptables -S`)과 순서가 같으면 저장이 잘 된 상태입니다.

## 따라 하기: 바꾼 규칙 저장하기

### 1단계. 지금 규칙이 의도한 대로인지 확인

```bash
sudo iptables -S INPUT
```

### 2단계. 저장

```bash
sudo netfilter-persistent save
```

지금 메모리의 규칙이 `/etc/iptables/rules.v4`와 `rules.v6`에 기록됩니다.

### 3단계. 저장된 내용 확인

```bash
sudo grep -n 'INPUT' /etc/iptables/rules.v4
```

## 저장 전에 꼭 확인할 것

저장은 "현재 상태를 그대로 굳히는" 일입니다. 실수로 SSH(22) 규칙을 지운 상태에서 저장하고 재부팅하면, **서버에 다시는 접속할 수 없게 될 수 있습니다.** 그래서 저장 전에 반드시 확인합니다.

| 확인 | 명령 | 기대 결과 |
|---|---|---|
| SSH 허용 규칙이 있는가 | `sudo iptables -S INPUT \| grep 'dport 22'` | 한 줄 이상 |
| 거부 규칙이 맨 끝인가 | `sudo iptables -S INPUT \| tail -1` | `-j REJECT ...` |
| Oracle 규칙이 남아 있는가 | `sudo iptables -S InstanceServices \| wc -l` | 0보다 큼 |

혹시 모를 경우를 대비해 저장 전에 지금 파일을 복사해 둡니다.

```bash
sudo cp /etc/iptables/rules.v4 ~/rules.v4.bak-$(date +%F)
```

## 정리

- `iptables` 명령은 재부팅하면 사라지고, `/etc/iptables/rules.v4`의 내용이 다시 적용됩니다.
- 바꾼 규칙은 `sudo netfilter-persistent save`로 저장합니다.
- 저장 전에 SSH 규칙과 마지막 거부 규칙, Oracle 규칙이 그대로인지 확인하고, 파일을 백업합니다.
