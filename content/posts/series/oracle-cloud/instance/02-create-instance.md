---
title: "인스턴스 만들기"
slug: "oracle-cloud-instance-create-instance"
category: "development"
publishedAt: "2026-10-07"
summary: "콘솔에서 Ampere A1 인스턴스를 만드는 화면을 항목별로 따라가며, 각 항목을 어떻게 정해야 하는지 설명합니다."
series: "oracle-cloud"
chapter: "instance"
seriesOrder: 2
---
## 이 글에서 할 일

앞에서 만든 VCN 안에 실제 서버(인스턴스)를 만듭니다. 입력 항목 순서는 공식 튜토리얼 "Creating and Connecting to Your First Linux Instance"를 따릅니다.

## 시작하기 전에 확인할 것

- 오른쪽 위 리전이 **홈 리전**인가? (무료 서버는 홈 리전에만 만들 수 있습니다)
- [VCN](/posts/oracle-cloud-network-vcn)과 [보안 목록](/posts/oracle-cloud-network-security-list)을 만들었는가?

## 따라 하기

검색창에 `Instances`를 입력해 이동하고 **Create instance**를 누릅니다.

만들기 화면은 **Basic information → Security → Networking → Storage → Review** 다섯 단계로 나뉩니다. 아래 1~3은 Basic information, 4는 Security, 5~6은 Networking, 7은 Storage, 8은 Review 단계에서 정합니다. Oracle 공식 문서도 콘솔의 표시 순서는 바뀔 수 있다고 안내하므로, 순서가 다르면 같은 이름의 항목을 찾아 채웁니다.

<!-- 📷 스크린샷: 인스턴스 만들기 첫 화면 -->

### 1. 이름과 구획

- **Name**: 서버 이름. 나중에 서버가 늘어나도 구분되도록 `web-01`처럼 용도와 번호를 넣으면 좋습니다.
- **Create in compartment**: 앞에서 만든 구획

### 2. 배치 (Placement)

- **Availability domain**: 기본값을 그대로 둡니다. 리전 안에 있는 하나 이상의 데이터 센터로, 다른 Availability domain과 독립되어 있습니다.
- **Fault domain**: Oracle이 고르게 둡니다. 한 Availability domain 안에서 하드웨어와 설비를 나눈 묶음으로, 장애나 점검의 영향을 나누는 단위입니다.

### 3. 이미지와 Shape

1. **Image**에서 **Change image**를 눌러 **Ubuntu**를 고르고, 버전은 **24.04**를 고릅니다.
2. **Shape**에서 **Change shape**를 눌러 **Ampere**의 `VM.Standard.A1.Flex`를 고릅니다.
3. **OCPU 수**와 **메모리**(GB)를 정합니다. 무료 한도(합계 2 OCPU, 12GB)를 넘지 않게 합니다.

<!-- 📷 스크린샷: 이미지와 Shape 선택 화면 -->

### 4. 보안 (Security)

**Shielded instance**(부팅 단계 변조 방지)와 **Confidential computing**(메모리 암호화) 같은 선택 기능이 있는 단계입니다. 이 시리즈의 서버는 **기본값 그대로** 넘어갑니다. 두 기능은 지원하는 Shape와 이미지에서만 켤 수 있어서, 화면에 따라 선택지가 비활성화되어 있을 수 있습니다.

### 5. 네트워크

- **Virtual cloud network**: 앞에서 만든 VCN
- **Subnet**: **공용 서브넷(Public Subnet)**
- **Assign a public IPv4 address**: **Yes**. 인터넷에서 접속하려면 공인 IP가 필요합니다.

### 6. SSH 키 (가장 중요)

서버에 원격으로 접속할 때 비밀번호 대신 **SSH 키**를 씁니다. 공식 튜토리얼은 세 가지 방법을 안내합니다.

| 방법 | 설명 |
|---|---|
| Generate a key pair for me | 콘솔이 키를 만들어 줍니다. **Save Private Key**, **Save Public Key**로 파일을 저장합니다 |
| Upload public key files | 내 PC에서 만든 공개 키 파일을 올립니다 |
| Paste public keys | 공개 키 내용을 붙여 넣습니다 |

처음이라면 **Generate a key pair for me**가 가장 쉽습니다. 다만 공식 문서의 경고를 꼭 기억합니다.

> 개인 키와 공개 키 파일을 안전한 곳에 보관하세요. 인스턴스를 만든 뒤에는 키를 다시 받을 수 없습니다.

개인 키를 잃어버리면 서버에 접속할 방법이 사라집니다. 저장한 파일은 백업해 둡니다. SSH 키의 원리와 내 PC에서 직접 만드는 방법은 [다음 글](/posts/oracle-cloud-instance-ssh-connect)에서 설명합니다.

<!-- 📷 스크린샷: SSH 키 추가 화면 -->

### 7. 부트 볼륨

운영체제가 설치될 디스크입니다. 크기를 바꾸지 않으면 화면에 표시된 기본 크기로 만들어집니다. 무료 범위는 블록 볼륨과 합쳐 200GB이므로, Docker 이미지나 백업 파일을 많이 둘 계획이라면 이 안에서 늘려 둡니다.

### 8. 검토 (Review)

만들기 전에 마지막으로 다음을 확인합니다. 잘못 만들면 지우고 다시 만들어야 하는 항목들입니다.

| 확인 | 기대 값 |
|---|---|
| Shape | `VM.Standard.A1.Flex` |
| OCPU, 메모리 | 이미 있는 A1 인스턴스와 합쳐 2 OCPU, 12GB 이하 |
| Image | Ubuntu 24.04 |
| Subnet | 공용 서브넷, 공인 IPv4 주소 할당 |
| SSH 키 | 개인 키를 내 PC에 저장했음 |
| 부트 볼륨 | 다른 볼륨과 합쳐 200GB 이하 |

### 9. 만들기

**Create**를 누르면 상태가 `PROVISIONING`(준비 중)에서 `RUNNING`(실행 중)으로 바뀝니다. 몇 분 정도 걸립니다.

## 확인하기

| 상태 | 의미 |
|---|---|
| `RUNNING`이고 공인 IP가 표시됨 | 서버가 만들어졌습니다. 다음 글에서 접속합니다 |
| `Out of host capacity` | 홈 리전에 Always Free 자리가 일시적으로 없다는 뜻입니다. 잠시 뒤 다시 시도하거나 다른 Availability domain을 고릅니다. 계속 막히면 [Pay As You Go 업그레이드](/posts/oracle-cloud-account-pay-as-you-go)를 고려합니다 |
| 한도(limit) 관련 오류 | 무료 한도를 넘는 사양을 골랐을 수 있습니다. OCPU와 메모리 합계를 확인합니다 |
## 정리

- 이미지는 **Ubuntu 24.04**, Shape는 **`VM.Standard.A1.Flex`**, 사양은 무료 한도 안에서 정합니다.
- **공용 서브넷**에 두고 **공인 IP**를 할당합니다.
- SSH 개인 키는 **다시 받을 수 없으므로** 반드시 저장하고 백업합니다.
