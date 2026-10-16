---
title: "GitHub Actions와 배포 스크립트 연결하기"
slug: "ledger-deploy-github-actions"
category: "development"
publishedAt: "2026-10-08"
summary: "저장소에 있는 SSH 배포 워크플로의 Secrets를 준비하고, 서버 배포 스크립트의 코드 갱신과 빌드, 동시 실행 제어, 배포 결과 확인을 설명합니다."
series: "ledger"
chapter: "deploy"
seriesOrder: 5
---
## 이 글에서 할 일

Ledger에는 `main` 브랜치에 push하면 서버의 배포 스크립트를 실행하는 워크플로가 있습니다. 파일을 새로 작성하는 대신 기존 파일이 요구하는 SSH 연결 정보와 권한을 준비합니다. 먼저 [수동 서버 실행](/posts/ledger-deploy-compose-env)과 [nginx 연결](/posts/ledger-deploy-nginx)을 완료합니다.

## 기존 워크플로

`.github/workflows/deploy.yml` (실행 조건)

```yaml
name: deploy

on:
  push:
    branches: [main]
  workflow_dispatch:

concurrency:
  group: deploy
  cancel-in-progress: false
```

`main`에 push하면 실행하며 수동 실행도 가능합니다. 같은 그룹의 진행 중인 실행을 취소하지 않도록 설정되어 있습니다.

`.github/workflows/deploy.yml` (배포 작업)

```yaml
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Deploy over SSH
        env:
          SSH_KEY: ${{ secrets.DEPLOY_SSH_KEY }}
          KNOWN_HOSTS: ${{ secrets.DEPLOY_KNOWN_HOSTS }}
          DEPLOY_HOST: ${{ secrets.DEPLOY_HOST }}
          DEPLOY_USER: ${{ secrets.DEPLOY_USER }}
        run: |
          install -m 700 -d ~/.ssh
          printf '%s\n' "$SSH_KEY" > ~/.ssh/id_ed25519
          chmod 600 ~/.ssh/id_ed25519
          printf '%s\n' "$KNOWN_HOSTS" > ~/.ssh/known_hosts
          ssh "$DEPLOY_USER@$DEPLOY_HOST" 'bash /srv/devgear/ledger/deploy/deploy.sh'
```

GitHub의 임시 실행 환경에 SSH 연결용 파일을 만들고 서버의 `deploy.sh`를 호출합니다. Java 빌드는 서버의 Docker 빌드에서 수행합니다.

## 1단계. SSH 연결 준비

| Secret 이름 | 설정할 값 |
|---|---|
| `DEPLOY_SSH_KEY` | 서버에 등록할 공개 키와 대응하는 배포 전용 SSH 개인 키 전체 |
| `DEPLOY_KNOWN_HOSTS` | 접속할 서버의 SSH 호스트 키가 담긴 행 |
| `DEPLOY_HOST` | 접속할 서버 주소 |
| `DEPLOY_USER` | 서버에 접속할 사용자 이름 |

등록 위치는 저장소의 **Settings → Secrets and variables → Actions**입니다. 이 경로와 `secrets` 참조 방식은 [GitHub 공식 문서](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)에서 확인했습니다(2026-10-07).

<!-- 📷 스크린샷: 값이 보이지 않는 GitHub Actions Secrets의 네 이름 -->

개인 키를 저장소에 넣지 않습니다. 공개 키는 서버 접속 계정의 `authorized_keys`에 등록하고, 서버 호스트 키는 지문을 확인한 값을 사용합니다. 키 생성과 서버 등록 절차는 [배포 전용 키 글](/posts/oracle-cloud-deploy-deploy-key), 호스트 키 확인은 [GitHub Actions 글](/posts/oracle-cloud-deploy-github-actions)에서 다룹니다.

서버의 강제 명령을 설정한다면 Ledger의 실제 명령인 `bash /srv/devgear/ledger/deploy/deploy.sh`를 지정합니다. 접속 계정은 저장소를 갱신하고 `docker` 명령을 실행할 수 있어야 하며, Compose가 root 소유 `app.env`를 읽을 권한도 필요합니다. 스크립트는 `sudo`를 붙이지 않으므로 서버의 접속 계정 권한과 맞춰 준비합니다.

## 2단계. 서버 배포 스크립트 읽기

`deploy/deploy.sh` (`main` 함수 앞부분)

```bash
main() {
    # 같은 서버의 다른 앱(ledger·fashion-archive·workout) 배포와 빌드가 겹치지 않게 한 번에 하나씩 실행한다
    exec 9>/tmp/devgear-deploy.lock
    flock -w 900 9 || { echo "다른 앱 배포가 15분 안에 끝나지 않아 중단합니다" >&2; return 1; }

    cd /srv/devgear/ledger
    git pull --ff-only
    docker compose up -d --build
```

원본 함수의 앞부분 발췌입니다. 실제 실행은 저장소의 전체 스크립트를 사용합니다.

`flock`은 공통 잠금 파일을 사용해 같은 서버의 빌드가 겹치지 않게 합니다. 최대 900초를 기다리고, 잠금을 받으면 저장소를 갱신한 뒤 Compose로 빌드와 실행을 수행합니다.

`git pull --ff-only`는 서버의 로컬 변경과 충돌할 때 임의로 병합하지 않습니다. 서버 저장소는 배포할 브랜치와 원격 추적 관계를 준비해 두어야 합니다. 앱 DB 변경은 별도로 적용하며, 이 스크립트에 SQL 실행 단계는 없습니다.

원본의 뒷부분은 이름 없는 이미지와 오래된 빌드 캐시를 정리하고 `배포 완료: <커밋>`을 출력합니다. 정리 명령은 Docker 호스트 단위로 동작하므로, 이 스크립트를 실행하면 캐시 정리도 함께 수행됩니다.

## 3단계. 실행하고 결과 확인

준비가 끝나면 `main`의 변경을 push하거나 Actions에서 `deploy`를 수동 실행합니다. 이 작업은 실제 서버의 앱을 빌드하고 교체합니다.

1. Actions 작업이 성공했고 마지막에 배포한 커밋이 나오는지 확인합니다.
2. 서버의 `/srv/devgear/ledger`에서 `docker compose ps`로 앱 상태를 확인합니다. 필요하면 실행 권한에 맞춰 `sudo`를 붙입니다.
3. 운영 HTTPS 주소에서 로그인하고 기존 데이터를 조회합니다.

배포 완료 문구는 스크립트의 명령이 끝났다는 뜻입니다. 원본 스크립트의 `up`에는 `--wait`가 없으므로, 이후 앱의 상태 확인과 실제 접속까지 확인합니다.

## 연결이 실패했을 때

| 로그 | 확인할 곳 |
|---|---|
| `Permission denied (publickey)` | 등록한 공개 키와 Secret의 개인 키가 대응하는지 |
| `Host key verification failed` | 주소와 `DEPLOY_KNOWN_HOSTS`의 호스트 키 |
| 환경 파일 읽기 권한 오류 | SSH 접속 계정과 `/etc/ledger/app.env`의 접근 권한 |
| `git pull` 실패 | 서버 저장소의 브랜치·원격 연결·로컬 변경 |
| Docker 빌드 실패 | 오류가 난 Maven 또는 Docker 단계의 로그 |

키나 환경 변수 값을 출력하지 않고, 오류 종류와 실행 단계를 기준으로 원인을 좁힙니다.
