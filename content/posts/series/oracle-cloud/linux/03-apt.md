---
title: "apt로 프로그램 설치하고 관리하기"
slug: "oracle-cloud-linux-apt"
category: "development"
publishedAt: "2026-10-07"
summary: "Ubuntu의 패키지 관리자 apt가 무엇인지, 저장소와 버전 확인, 외부 저장소(Docker) 추가 방식까지 설명합니다."
series: "oracle-cloud"
chapter: "linux"
seriesOrder: 3
---
## 이 글에서 할 일

이 시리즈에서 쓰는 nginx, PostgreSQL, Docker, certbot은 모두 **apt**로 설치합니다. apt는 Ubuntu의 **패키지 관리자**입니다. 스마트폰의 앱 스토어처럼, 프로그램을 검색하고 설치하고 업데이트합니다.

## 기본 명령

```bash
sudo apt update                 # 설치 가능한 패키지 목록 새로 받기
apt search nginx                # 패키지 검색
apt show nginx                  # 패키지 설명 보기
sudo apt install nginx          # 설치
sudo apt upgrade                # 설치된 패키지 전체 업데이트
sudo apt remove nginx           # 삭제 (설정 파일은 남김)
sudo apt purge nginx            # 설정 파일까지 삭제
sudo apt autoremove             # 더 이상 필요 없는 의존 패키지 정리
```

**의존성(dependency)**: 프로그램이 동작하려면 다른 프로그램이 필요한 경우가 많습니다. apt는 필요한 것을 알아서 함께 설치합니다.

## 무엇이 어떤 버전으로 설치되어 있나

```bash
dpkg -l nginx postgresql-16 docker-ce certbot python3-certbot-nginx | awk '/^ii/{print $2, $3}'
```

결과는 "패키지 이름 버전" 형태로 나옵니다. 버전은 설치한 시점에 따라 다릅니다.

```text
certbot 2.9.0-1
docker-ce 5:29.8.1-1~ubuntu.24.04~noble
nginx 1.24.0-2ubuntu7.18
postgresql-16 16.15-0ubuntu0.24.04.1
python3-certbot-nginx 2.9.0-1
```

- `dpkg`: apt가 내부에서 쓰는 저수준 도구입니다. 설치된 목록을 볼 때 씁니다.
- `ii`: "설치됨" 상태라는 뜻입니다.
- `noble`: Ubuntu 24.04의 코드네임입니다.

## 저장소(repository)

apt는 정해진 **저장소**에서 패키지를 받습니다. 저장소 목록은 `/etc/apt/sources.list.d/` 폴더에 있습니다.

Docker 저장소를 추가한 서버라면 다음과 같이 보입니다.

```bash
ls /etc/apt/sources.list.d/
```

```text
docker.sources
ubuntu.sources
```

- `ubuntu.sources`: Ubuntu 공식 저장소. nginx, PostgreSQL, certbot은 여기서 받습니다.
- `docker.sources`: Docker 회사의 공식 저장소. Docker는 Ubuntu 저장소에도 있지만, 최신 버전을 받기 위해 Docker 공식 문서가 안내하는 저장소를 추가합니다.

### 외부 저장소를 추가할 때 하는 일

외부 저장소는 아무 서버나 믿을 수 없으므로 **서명 키**로 진짜인지 확인합니다. Docker 공식 문서의 순서를 보면 그 원리가 보입니다.

```bash
sudo install -m 0755 -d /etc/apt/keyrings                                 # 키 보관 폴더
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc   # 서명 키 받기
sudo chmod a+r /etc/apt/keyrings/docker.asc
```

그다음 `docker.sources` 파일에 저장소 주소와 이 키를 적습니다. 내용은 다음과 같습니다.

`/etc/apt/sources.list.d/docker.sources`

```text
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: noble
Components: stable
Signed-By: /etc/apt/keyrings/docker.asc
```

`Signed-By`가 "이 저장소의 패키지는 이 키로 서명되어 있어야 한다"는 뜻입니다. 자세한 설치 과정은 [Docker 설치](/posts/oracle-cloud-docker-install)에서 다룹니다.

## snap과 apt

Ubuntu에는 **snap**이라는 다른 설치 방식도 있습니다. certbot 공식 안내는 snap 설치를 권장하지만, 이 시리즈는 다른 프로그램과 관리 방식을 맞추려고 Ubuntu 저장소의 apt 패키지로 설치합니다.

어떤 방식으로 설치되어 있는지는 다음처럼 확인합니다.

```bash
snap list | grep -i certbot || echo "certbot은 snap이 아님"
```

```text
certbot은 snap이 아님
```

둘 다 동작합니다. apt 버전은 Ubuntu 저장소에 맞춰 버전이 조금 오래될 수 있고, snap 버전은 최신을 빠르게 받습니다. 한 서버에서 둘을 섞어 설치하면 같은 프로그램이 두 개 생겨 헷갈리므로 하나만 씁니다.

## 정리

- `apt update` 후 `apt install`로 설치합니다.
- `dpkg -l`로 설치된 버전을 확인합니다.
- 외부 저장소는 서명 키와 함께 `/etc/apt/sources.list.d/`에 추가합니다.
- 이 시리즈는 Docker만 Docker 공식 저장소에서, 나머지는 Ubuntu 공식 저장소에서 설치합니다.
