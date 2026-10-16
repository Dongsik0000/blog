---
title: "처음 접속한 뒤 해 둘 일"
slug: "oracle-cloud-instance-first-setup"
category: "development"
publishedAt: "2026-10-07"
summary: "패키지 업데이트, 자동 보안 업데이트, 시간대, 스왑 메모리처럼 서버를 만들자마자 한 번 해 두면 좋은 설정을 정리합니다."
series: "oracle-cloud"
chapter: "instance"
seriesOrder: 4
---
## 이 글에서 할 일

서버에 처음 접속했다면 앱을 설치하기 전에 기본 설정을 먼저 합니다. 다음 네 가지를 차례로 진행합니다.

1. 패키지 업데이트
2. 자동 보안 업데이트 확인
3. 시간대를 한국 시간으로
4. 스왑 메모리 만들기

## 1. 패키지 업데이트

새로 만든 서버의 프로그램들은 이미지가 만들어진 시점의 버전입니다. 그사이 나온 보안 패치를 먼저 받습니다.

```bash
sudo apt update          # 설치 가능한 최신 버전 목록을 받아 옴
sudo apt upgrade -y      # 설치된 프로그램을 최신 버전으로 올림
```

- `sudo`: 관리자 권한으로 실행합니다. 시스템을 바꾸는 명령에 필요합니다.
- `apt update`는 **목록만** 갱신하고, 실제 설치는 `apt upgrade`가 합니다. 둘은 항상 이 순서로 씁니다.
- `-y`: "계속할까요?" 질문에 자동으로 예라고 답합니다.

업그레이드 후 커널이 바뀌었다면 재부팅이 필요합니다.

```bash
[ -f /var/run/reboot-required ] && echo "재부팅 필요" || echo "재부팅 불필요"
sudo reboot              # 필요할 때만
```

## 2. 자동 보안 업데이트 확인

Ubuntu에는 보안 업데이트를 매일 자동으로 설치하는 **unattended-upgrades**가 들어 있습니다. 켜져 있는지 확인합니다.

```bash
systemctl is-enabled unattended-upgrades
```

```text
enabled
```

`enabled`가 나오면 켜져 있는 것입니다. 보안 패치는 자동으로 설치되지만, **자동 재부팅은 꺼져 있습니다**(기본값). 서비스가 예고 없이 멈추지 않게 하려는 것입니다. 그래서 커널 업데이트 뒤에는 위의 `reboot-required` 확인을 가끔 직접 해 줍니다.

## 3. 시간대를 한국 시간으로

서버의 기본 시간대는 보통 UTC(세계 표준시)입니다. 한국 시간보다 9시간 느립니다. 로그를 볼 때나 "매일 새벽 4시 백업" 같은 예약 작업을 만들 때 헷갈리므로 한국 시간으로 바꿉니다.

```bash
sudo timedatectl set-timezone Asia/Seoul
timedatectl
```

결과에서 다음 두 줄을 확인합니다.

```text
                Time zone: Asia/Seoul (KST, +0900)
              NTP service: active
```

- `NTP service: active`: 인터넷 시간 서버와 자동으로 시간을 맞추고 있다는 뜻입니다.

## 4. 스왑 메모리 만들기

**스왑**(swap)은 메모리가 부족할 때 디스크 일부를 메모리처럼 쓰는 공간입니다. 디스크는 메모리보다 훨씬 느리기 때문에 성능을 높이는 장치가 아닙니다. **메모리가 갑자기 모자랄 때 프로그램이 강제 종료되는 것을 막는 안전장치**입니다.

먼저 스왑이 있는지 확인합니다.

```bash
swapon --show
```

아무것도 나오지 않으면 스왑이 없는 것입니다.

### 만드는 방법

다음 순서로 2GB 스왑 파일을 만듭니다. 크기는 서버 메모리와 디스크 여유에 맞춰 정합니다.

```bash
sudo fallocate -l 2G /swapfile    # 1. 2GB 크기의 빈 파일 만들기
sudo chmod 600 /swapfile          # 2. root만 읽고 쓰게 (메모리 내용이 담기므로)
sudo mkswap /swapfile             # 3. 스왑 형식으로 준비
sudo swapon /swapfile             # 4. 지금 바로 사용 시작
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab   # 5. 재부팅 후에도 자동 사용
```

5번의 `/etc/fstab`은 "부팅할 때 연결할 디스크 목록"입니다. 이 줄이 없으면 재부팅 후 스왑이 사라집니다.

만든 뒤 다시 확인하면 다음과 같이 나옵니다.

```bash
swapon --show
grep swapfile /etc/fstab
```

```text
NAME      TYPE SIZE USED PRIO
/swapfile file   2G   0B   -2
/swapfile none swap sw 0 0
```

`USED`(사용량)와 `PRIO`(우선순위) 값은 서버마다 다를 수 있습니다.

## 확인하기

| 명령 | 기대 결과 |
|---|---|
| `systemctl is-enabled unattended-upgrades` | `enabled` |
| `timedatectl \| grep 'Time zone'` | `Asia/Seoul` |
| `swapon --show` | `/swapfile` 한 줄 |
| `[ -f /var/run/reboot-required ] && echo 필요` | 출력 없음 (재부팅 불필요) |

## 정리

- 서버를 만들면 먼저 `apt update && apt upgrade`로 보안 패치를 받습니다.
- 자동 보안 업데이트가 켜져 있는지 확인하고, 재부팅은 직접 판단해서 합니다.
- 시간대를 `Asia/Seoul`로 맞춰야 로그와 예약 작업 시간이 헷갈리지 않습니다.
- 스왑은 메모리 부족으로 프로그램이 죽는 것을 막는 안전장치입니다.
