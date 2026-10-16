---
title: "VCN 만들기 (마법사 사용)"
slug: "oracle-cloud-network-vcn"
category: "development"
publishedAt: "2026-10-07"
summary: "서버가 들어갈 가상 네트워크(VCN)를 마법사로 한 번에 만들고, 마법사가 무엇을 만들어 주는지 하나씩 확인합니다."
series: "oracle-cloud"
chapter: "network"
seriesOrder: 2
---
## 이 글에서 할 일

서버를 만들기 전에 서버가 들어갈 **네트워크**가 필요합니다. Oracle Cloud에서는 이것을 **VCN**(Virtual Cloud Network, 가상 클라우드 네트워크)이라고 부릅니다. 회사 사무실에 공유기와 랜선을 까는 일을 클라우드에서 하는 것이라고 생각하면 됩니다.

VCN에는 서브넷, 게이트웨이, 라우트 테이블, 보안 목록이 필요합니다. 하나씩 만들 수도 있지만, Oracle이 제공하는 **마법사**(Wizard)를 쓰면 한 번에 만들어집니다. 처음에는 마법사를 쓰는 것을 권합니다.

## 마법사가 만들어 주는 것

공식 문서 "Virtual Networking Quickstart"에 따르면 **Create VCN with Internet Connectivity** 마법사는 다음을 만듭니다.

| 자원 | 설명 |
|---|---|
| VCN | 전체 네트워크 |
| 인터넷 게이트웨이 | 공용 서브넷이 인터넷과 통신하는 문 |
| NAT 게이트웨이 | 사설 서브넷이 밖으로만 나가는 문 |
| 서비스 게이트웨이 | Oracle 서비스와 내부 통신하는 문 |
| 공용 서브넷 | 인터넷 게이트웨이로 가는 경로가 있는 서브넷. 서버에 공인 IP를 붙일 수 있음 |
| 사설 서브넷 | NAT, 서비스 게이트웨이로 가는 경로가 있는 서브넷. 공인 IP 불가 |
| 보안 목록 규칙 | 두 서브넷의 기본 규칙. **SSH 접속 허용**이 포함됨 |

각 용어의 뜻은 [네트워크 기초](/posts/oracle-cloud-network-network-basics)에서 설명했습니다.

## 따라 하기

### 1단계. VCN 화면으로 이동

콘솔 위쪽 검색창에 `Virtual Cloud Networks`를 입력해 이동합니다. 오른쪽 위 리전이 **홈 리전**인지, 왼쪽 구획(Compartment)이 원하는 구획인지 확인합니다.

<!-- 📷 스크린샷: VCN 목록 화면 -->

### 2단계. 마법사 시작

**Start VCN Wizard**(또는 비슷한 이름의 버튼)를 누르고 **Create VCN with Internet Connectivity**를 고릅니다.

### 3단계. 이름과 주소 범위

- **VCN Name**: 알아보기 쉬운 이름(예: `web-vcn`)
- **Compartment**: 앞에서 만든 구획
- **CIDR 블록**: 마법사가 채워 주는 기본값을 그대로 둡니다. 주소 범위를 직접 설계할 이유가 없다면 기본값이 안전합니다.

<!-- 📷 스크린샷: 마법사 입력 화면 -->

### 4단계. 검토 후 생성

**Next**를 누르면 만들 자원 목록이 나옵니다. 위 표의 자원들이 있는지 확인하고 **Create**를 누릅니다. 몇 초 안에 모든 자원이 만들어집니다.

## 확인하기

만들어진 VCN을 열어 다음을 확인합니다.

| 확인할 곳 | 기대 결과 |
|---|---|
| Subnets | Public Subnet, Private Subnet 두 개 |
| Gateways | Internet Gateway, NAT Gateway, Service Gateway |
| Security Lists | 공용 서브넷용 기본 보안 목록 (SSH 22 허용) |

서버를 만든 뒤에는 서버가 속한 서브넷의 주소 범위와 사설 IP를 서버 안에서도 확인할 수 있습니다.

```bash
curl -s -H "Authorization: Bearer Oracle" http://169.254.169.254/opc/v2/vnics/ \
  | python3 -c 'import sys,json; v=json.load(sys.stdin)[0]; print(v["subnetCidrBlock"], v["privateIp"])'
```

결과는 "서브넷 범위, 사설 IP" 순서입니다.

```text
10.0.0.0/24 10.0.0.101
```

위 예시는 서버가 `10.0.0.0/24` 서브넷 안에서 `10.0.0.101`이라는 사설 IP를 받았다는 뜻입니다.

## 정리

- VCN은 클라우드 안의 가상 네트워크입니다.
- 마법사 **Create VCN with Internet Connectivity**가 서브넷, 게이트웨이, 기본 보안 규칙까지 한 번에 만듭니다.
- 웹 서버는 **공용 서브넷**에 둡니다.

지금 상태로는 SSH(22번)만 열려 있습니다. 웹 서비스를 하려면 80, 443번도 열어야 합니다. 다음 글에서 보안 목록을 수정합니다.
