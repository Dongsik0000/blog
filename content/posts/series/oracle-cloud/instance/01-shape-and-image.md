---
title: "Shape와 이미지 고르기"
slug: "oracle-cloud-instance-shape-and-image"
category: "development"
publishedAt: "2026-10-07"
summary: "서버 사양을 정하는 Shape와 운영체제를 정하는 이미지가 무엇인지 설명하고, 무료로 실용적인 서버를 만드는 선택 기준을 정리합니다."
series: "oracle-cloud"
chapter: "instance"
seriesOrder: 1
---
## 이 글에서 할 일

서버(Oracle Cloud에서는 **인스턴스**라고 부릅니다)를 만들 때 가장 중요한 두 가지 선택이 있습니다.

1. **Shape**: 서버의 사양. CPU 개수, 메모리 크기
2. **이미지(Image)**: 서버에 설치될 운영체제

이 글에서는 두 선택의 의미와 기준을 이해합니다. 실제 생성은 다음 글에서 합니다.

## Shape: 서버의 사양

Shape는 "어떤 종류의 컴퓨터를 몇 개의 CPU와 얼마의 메모리로 빌릴지"를 정한 것입니다. 이름은 다음 규칙을 따릅니다.

```text
VM.Standard.A1.Flex
│  │        │  └─ Flex: CPU와 메모리를 원하는 만큼 조절 가능
│  │        └──── A1: Ampere(ARM) 프로세서
│  └───────────── Standard: 일반 용도
└──────────────── VM: 가상 머신 (물리 서버를 나눠 쓰는 방식)
```

[무료 티어 글](/posts/oracle-cloud-account-free-tier)에서 본 것처럼, 무료로 쓸 수 있는 Shape는 두 가지입니다.

| Shape | 무료 사양 | 웹 서비스용으로 |
|---|---|---|
| `VM.Standard.A1.Flex` (ARM) | 합계 2 OCPU, 12GB | 충분함. Java 웹 서버 여러 개와 DB를 함께 돌릴 수 있음 |
| `VM.Standard.E2.1.Micro` (AMD) | 1/8 OCPU, 1GB | 부족함. Java 웹 서버 하나도 빠듯함 |

이 시리즈는 `VM.Standard.A1.Flex`를 씁니다. OCPU와 메모리는 무료 한도(합계 2 OCPU, 12GB) 안에서 정합니다. 서버 한 대만 만들 계획이라면 한도 가까이 잡아도 되고, 나중에 서버를 하나 더 만들 생각이라면 여유를 남겨 둡니다.

## ARM 프로세서란

우리가 흔히 쓰는 PC는 대부분 **x86(amd64)** 프로세서입니다. A1은 **ARM(arm64, aarch64)** 프로세서입니다. 스마트폰과 Apple M 시리즈 맥이 쓰는 계열입니다.

프로세서 종류가 다르면 **실행 파일 형식**이 다릅니다. Java, Node.js, Python 같은 언어는 ARM용이 따로 있어서 대부분 그대로 동작합니다. 하지만 Docker 이미지처럼 미리 만들어진 실행 파일은 ARM용이 있는지 확인해야 합니다. 자세한 내용은 [ARM64라서 생기는 일](/posts/oracle-cloud-instance-arm64)에서 다룹니다.

## 이미지: 운영체제

이미지는 서버에 처음 설치될 운영체제입니다. Oracle Cloud가 제공하는 **플랫폼 이미지**에는 Oracle Linux, Ubuntu 등이 있습니다.

이 시리즈는 **Ubuntu 24.04 LTS**를 기준으로 설명합니다.

| 기준 | 설명 |
|---|---|
| LTS | Long Term Support. 보안 업데이트를 오래 제공하는 버전입니다 |
| 자료가 많음 | 검색했을 때 Ubuntu 기준 설명이 가장 많아 초보자에게 유리합니다 |
| 패키지 | nginx, PostgreSQL, Docker 모두 Ubuntu용 공식 패키지가 있습니다 |

이미지를 고를 때 **ARM용(aarch64)** 이미지인지 확인합니다. A1 Shape를 고르면 콘솔이 맞는 이미지만 보여 줍니다.

## 서버 안에서 확인하기

서버를 만든 뒤에는 다음 명령으로 사양을 확인할 수 있습니다.

```bash
. /etc/os-release; echo "$PRETTY_NAME"   # 운영체제
uname -m                                  # 프로세서 종류
nproc                                     # CPU 개수
free -h                                   # 메모리
```

2 OCPU, 메모리 10GB로 만든 서버라면 다음과 같은 형태로 나옵니다.

```text
Ubuntu 24.04.5 LTS
aarch64
2
               total        used        free      shared  buff/cache   available
Mem:           9.7Gi       2.9Gi       1.1Gi        44Mi       5.8Gi       6.7Gi
```

- `aarch64`: ARM 64비트라는 뜻입니다.
- `nproc`가 2인 이유: A1에서 1 OCPU는 CPU 1개로 보입니다.
- 메모리가 설정값(10GB)보다 조금 작게 보이는 이유: `Gi`는 1024 기준 단위이고, 운영체제가 일부를 미리 예약하기 때문입니다.

## 정리

- **Shape** = 사양, **이미지** = 운영체제
- 무료로 실용적인 서버를 만들려면 `VM.Standard.A1.Flex`(ARM)를 고릅니다.
- 운영체제는 자료가 많은 **Ubuntu LTS**를 권합니다.
- ARM 서버이므로 Docker 이미지 등은 ARM 지원 여부를 확인해야 합니다.
