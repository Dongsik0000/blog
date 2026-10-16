---
title: "자동 배포 흐름 한눈에 보기"
slug: "oracle-cloud-deploy-deploy-flow"
category: "development"
publishedAt: "2026-10-07"
summary: "코드를 GitHub에 push하면 서버의 앱이 새 버전으로 바뀌기까지의 전체 흐름을 그림으로 정리하고, 각 단계를 맡는 파일을 연결합니다."
series: "oracle-cloud"
chapter: "deploy"
seriesOrder: 1
---
## 이 글에서 할 일

**배포**는 고친 코드를 실제 서버에 반영해 사용자가 새 버전을 쓰게 하는 일입니다. 처음에는 서버에 SSH로 들어가 명령을 하나씩 쳐서 배포해도 됩니다. 하지만 매번 반복하다 보면 순서를 빠뜨리거나 다른 앱 배포와 겹치는 실수가 생깁니다. 이 장에서는 **GitHub에 push하면 자동으로 배포되는** 구성을 만듭니다.

## 전체 흐름

```text
① 내 PC: git push origin main
        │
        ▼
② GitHub: push를 감지하고 GitHub Actions 워크플로 실행
        │  (GitHub이 빌려주는 임시 리눅스 컴퓨터에서)
        ▼
③ 워크플로: 저장해 둔 비밀값(Secrets)으로 SSH 키를 준비하고 서버에 SSH 접속
        │  ssh ubuntu@서버 'bash /srv/myapp/deploy/deploy.sh'
        ▼
④ 서버의 deploy.sh
        ├─ 다른 앱 배포가 진행 중이면 기다림 (flock)
        ├─ git pull --ff-only           ← GitHub에서 새 코드 받기
        ├─ docker compose up -d --build ← 새 이미지 빌드, 컨테이너 교체
        └─ 오래된 이미지, 빌드 캐시 정리
        │
        ▼
⑤ 새 컨테이너가 뜨면 nginx가 /shop/ 요청을 새 버전으로 전달
```

빌드는 GitHub이 아니라 **서버에서** 합니다. GitHub Actions는 "서버에 접속해서 배포 스크립트를 실행하라"는 신호만 보냅니다. 구성이 단순하고, [ARM 서버](/posts/oracle-cloud-instance-arm64)에 맞는 이미지가 자연스럽게 만들어집니다.

## 단계별로 맡는 파일

| 단계 | 파일 | 위치 | 다루는 글 |
|---|---|---|---|
| ② ③ | `.github/workflows/deploy.yml` | 앱 저장소 | [GitHub Actions](/posts/oracle-cloud-deploy-github-actions) |
| ③ | `~/.ssh/authorized_keys` | 서버 | [배포 키 보안](/posts/oracle-cloud-deploy-deploy-key) |
| ④ | `deploy/deploy.sh` | 앱 저장소 (서버에 clone) | [배포 스크립트](/posts/oracle-cloud-deploy-deploy-script) |
| ④ | `docker-compose.yml`, `Dockerfile` | 앱 저장소 | [Docker 장](/posts/oracle-cloud-docker-compose) |
| ⑤ | `/etc/nginx/snippets/shop.conf` | 서버 | [리버스 프록시](/posts/oracle-cloud-nginx-reverse-proxy) |

## 처음 한 번: 서버에 저장소 clone하기

자동 배포는 "서버에 이미 저장소가 있다"는 전제에서 `git pull`로 새 코드를 받습니다. 그래서 처음 한 번은 직접 clone합니다.

```bash
sudo mkdir -p /srv/myapp
sudo chown ubuntu:ubuntu /srv/myapp
git clone https://github.com/<계정>/<저장소>.git /srv/myapp
```

공개 저장소라면 위 주소 그대로 받을 수 있습니다. 비공개 저장소라면 서버가 GitHub에서 읽을 수 있도록 **저장소 전용 읽기 키**(GitHub의 Deploy keys, 읽기 전용)를 등록하고 SSH 주소(`git@github.com:...`)로 clone합니다.

그다음 [비밀값 파일](/posts/oracle-cloud-docker-compose)(`/etc/myapp/app.env`)을 만들고, `docker compose up -d --build`로 한 번 직접 띄워 앱이 정상으로 뜨는지 확인합니다. 손으로 한 번 성공한 뒤에 자동화합니다.

## 서버에서 코드를 직접 고치지 않습니다

서버의 `/srv/myapp`은 GitHub 저장소의 **사본**입니다. 서버에서 파일을 고치면 다음 `git pull`이 실패하거나, 어떤 코드가 실행 중인지 알 수 없게 됩니다. 모든 변경은 PC에서 하고 push합니다.

## 자동 배포 대상이 아닌 것

이 흐름은 **앱 코드**만 배포합니다. 다음은 서버에서 직접 관리합니다.

| 대상 | 이유 |
|---|---|
| 비밀값 파일 (`/etc/myapp/app.env`) | 저장소에 넣으면 안 됨 |
| nginx 설정 | 서버 전체가 함께 쓰는 설정. 저장소에 사본을 두되 적용은 [안전한 절차](/posts/oracle-cloud-nginx-apply-safely)로 직접 |
| 데이터베이스 구조 변경 (마이그레이션 SQL) | 데이터를 바꾸는 작업이라 [백업](/posts/oracle-cloud-postgresql-backup-restore) 후 직접 실행 |

## 정리

- push → GitHub Actions → SSH → 서버의 `deploy.sh` → `git pull` + `docker compose up --build` 순서입니다.
- 빌드는 서버에서 하고, GitHub Actions는 배포 스크립트를 실행하라는 신호만 보냅니다.
- 처음 한 번은 직접 clone하고 손으로 띄워 본 뒤 자동화합니다.
- 비밀값, nginx, DB 구조 변경은 자동 배포 대상이 아닙니다.
