---
title: "ARM64 서버에 Docker 설치하기"
slug: "oracle-cloud-docker-install"
category: "development"
publishedAt: "2026-10-07"
summary: "Docker 공식 문서의 apt 저장소 방식으로 Docker Engine과 compose 플러그인을 설치하고, sudo 없이 쓰기 위한 docker 그룹 설정과 그 위험을 설명합니다."
series: "oracle-cloud"
chapter: "docker"
seriesOrder: 2
---
## 이 글에서 할 일

Docker 공식 문서 "Install Docker Engine on Ubuntu"의 **apt 저장소 방식**으로 설치합니다. 공식 문서에 따르면 Ubuntu용 Docker는 `arm64`를 지원하므로 Ampere A1 서버(ARM)에도 그대로 설치됩니다.

## 따라 하기

### 1단계. 저장소 서명 키 받기

```bash
sudo apt update
sudo apt install ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
```

외부 저장소의 패키지가 진짜 Docker 회사가 만든 것인지 확인하기 위한 서명 키입니다. 원리는 [apt 글](/posts/oracle-cloud-linux-apt)에서 설명했습니다.

### 2단계. 저장소 등록

공식 문서는 저장소를 `/etc/apt/sources.list.d/docker.sources` 파일(deb822 형식)로 등록하도록 안내합니다. 등록이 끝나면 파일 내용은 다음과 같습니다.

`/etc/apt/sources.list.d/docker.sources`

```text
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: noble
Components: stable
Signed-By: /etc/apt/keyrings/docker.asc
```

`noble`은 Ubuntu 24.04의 코드네임입니다. 파일 작성 명령은 공식 문서에 있는 것을 그대로 복사해 쓰는 것을 권합니다(버전이 바뀌면 명령도 바뀔 수 있으므로).

### 3단계. 설치

```bash
sudo apt update
sudo apt install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

| 패키지 | 역할 |
|---|---|
| `docker-ce` | Docker 엔진 (컨테이너를 실제로 실행) |
| `docker-ce-cli` | `docker` 명령 |
| `containerd.io` | 컨테이너 실행을 담당하는 하위 런타임 |
| `docker-buildx-plugin` | 이미지 빌드 도구 |
| `docker-compose-plugin` | `docker compose` 명령 |

### 4단계. 동작 확인

```bash
sudo docker run hello-world
```

"Hello from Docker!" 메시지가 나오면 설치가 끝났습니다. 이때 받은 `hello-world` 이미지는 확인용이라 `docker rmi hello-world`로 지워도 됩니다.

```bash
docker --version
dpkg -l docker-compose-plugin | awk '/^ii/{print $2, $3}'
```

다음과 같은 형태로 나옵니다. 버전은 설치 시점에 따라 다릅니다.

```text
Docker version 29.8.1, build 4a63305
docker-compose-plugin 5.5.1-1~ubuntu.24.04~noble
```

## sudo 없이 docker 쓰기와 그 위험

기본 상태에서는 `docker` 명령마다 `sudo`가 필요합니다. 공식 문서는 사용자를 `docker` 그룹에 넣는 방법(Linux postinstall)을 안내합니다.

```bash
sudo usermod -aG docker ubuntu
```

그룹 변경은 **다음 로그인부터** 적용됩니다. SSH를 끊고 다시 접속합니다.

```bash
id | grep -o docker
```

`docker`가 출력되면 그룹에 들어간 것입니다. 이제 `sudo` 없이 `docker ps`가 동작합니다.

**주의할 점**: `docker` 그룹에 속하면 사실상 **root와 같은 권한**을 가집니다. 컨테이너를 띄울 때 서버의 어떤 폴더든 연결할 수 있기 때문입니다. 그래서 이 사용자로 접속하는 SSH 개인 키는 root 키처럼 안전하게 보관하고, 신뢰하는 사용자만 이 그룹에 넣습니다.

## 정리

- Docker 공식 저장소를 서명 키와 함께 등록하고, `docker-ce`와 compose 플러그인을 설치합니다.
- ARM64 서버에서도 같은 명령으로 설치됩니다.
- `docker run hello-world`로 확인합니다.
- `docker` 그룹은 root와 같은 권한이므로 신뢰하는 사용자만 넣습니다.
