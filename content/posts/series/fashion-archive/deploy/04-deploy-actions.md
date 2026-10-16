---
title: "배포 스크립트와 GitHub Actions"
slug: "fashion-archive-deploy-deploy-actions"
category: "development"
publishedAt: "2026-10-08"
summary: "main에 push하면 GitHub Actions가 SSH로 서버의 deploy/deploy.sh를 실행하는 구성, 스크립트의 flock 잠금, git pull --ff-only, 테스트를 포함한 mvn package, 이미지 빌드와 정리, 워크플로의 Secrets 준비를 설명합니다."
series: "fashion-archive"
chapter: "deploy"
seriesOrder: 4
---
## 이 글에서 할 일

Fashion Archive는 `main` 브랜치에 push하면 서버에 자동으로 배포됩니다(커밋 `f41f09c` "push 시 서버 자동 배포 워크플로 추가"). 이 글에서는 GitHub Actions 워크플로와, 서버에서 실제로 배포를 하는 스크립트를 봅니다.

```text
git push origin main
  → GitHub Actions: SSH로 서버 접속
  → 서버: bash /srv/devgear/fashion-archive/deploy/deploy.sh
      ├─ 다른 앱 배포가 끝날 때까지 기다림 (flock)
      ├─ git pull --ff-only
      ├─ mvn -q package   (테스트 + war)
      ├─ docker compose up -d --build
      └─ 오래된 이미지, 빌드 캐시 정리
```

자동 배포의 일반적인 구성(배포 키 권한, known_hosts)은 [Oracle Cloud 시리즈의 자동 배포 장](/posts/oracle-cloud-deploy-deploy-flow)에 있습니다.

## 워크플로

`.github/workflows/deploy.yml` (실행 조건)

```yaml
on:
  push:
    branches: [main]
  workflow_dispatch:

concurrency:
  group: deploy
  cancel-in-progress: false
```

| 줄 | 뜻 |
|---|---|
| `push: branches: [main]` | main에 push하면 실행 |
| `workflow_dispatch` | GitHub 화면의 Run workflow 버튼으로도 실행 |
| `concurrency` | 같은 저장소의 배포가 겹치면 기다림. 진행 중인 배포는 중간에 끊지 않음 |

`.github/workflows/deploy.yml` (배포 단계)

```yaml
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
  ssh "$DEPLOY_USER@$DEPLOY_HOST" 'bash /srv/devgear/fashion-archive/deploy/deploy.sh'
```

비밀값 네 개를 저장소의 **Settings → Secrets and variables → Actions**에 등록합니다.

| Secret | 값 |
|---|---|
| `DEPLOY_SSH_KEY` | 배포 전용 SSH 개인 키 |
| `DEPLOY_KNOWN_HOSTS` | 서버의 SSH 공개 키 한 줄 (진짜 서버인지 확인) |
| `DEPLOY_HOST` | 서버 주소 |
| `DEPLOY_USER` | 서버 사용자 |

빌드는 GitHub의 컴퓨터가 아니라 **서버에서** 합니다. 워크플로는 SSH로 접속해 배포 스크립트를 실행하라는 신호만 보내므로 저장소 코드를 받는 단계(`actions/checkout`)가 없습니다. 비밀값은 `${{ }}`를 명령에 직접 끼워 넣지 않고 `env`로 넘겨 셸이 하나의 문자열로 다루게 합니다.

## 배포 스크립트

`deploy/deploy.sh`

```bash
#!/usr/bin/env bash
# GitHub Actions 가 SSH 로 실행하는 배포 스크립트 (서버: /srv/devgear/fashion-archive)
#
# 전체를 main 함수로 감싼다 — git pull 이 이 파일 자체를 바꿔도 bash 는 이미 읽은 함수를 끝까지 실행한다.
set -euo pipefail

main() {
    exec 9>/tmp/devgear-deploy.lock
    flock -w 900 9 || { echo "다른 앱 배포가 15분 안에 끝나지 않아 중단합니다" >&2; return 1; }

    cd /srv/devgear/fashion-archive
    git pull --ff-only
    # Dockerfile 이 target/fashion-archive.war 를 복사하므로 이미지 빌드 전에 war 를 만든다
    mvn -q package
    docker compose up -d --build
    # 빌드할수록 쌓이는 이름 없는 이미지와 7일 지난 빌드 캐시를 지운다. 실패해도 배포는 끝났으므로 알리기만 한다
    docker image prune -f > /dev/null || echo "이미지 정리 실패 (배포는 완료)" >&2
    docker builder prune -f --filter until=168h > /dev/null || echo "빌드 캐시 정리 실패 (배포는 완료)" >&2
    echo "배포 완료: $(git log --oneline -1)"
}

main "$@"
exit
```

| 줄 | 하는 일 |
|---|---|
| `set -euo pipefail` | 명령 하나라도 실패하면 즉시 멈춤 |
| `flock -w 900 9` | 같은 서버의 다른 앱 배포와 겹치지 않게 같은 잠금 파일로 차례를 기다림(최대 15분) |
| `git pull --ff-only` | 서버 코드가 GitHub보다 뒤처져 있을 때만 받음. 이력이 갈라져 있으면 실패 |
| `mvn -q package` | 테스트를 실행하고 war를 만듦. `-q`는 오류만 출력 |
| `docker compose up -d --build` | 새 war로 이미지를 만들고 컨테이너를 교체 |
| `prune` 두 줄 | 정리는 실패해도 배포를 실패로 만들지 않음 (`\|\| echo`) |
| `main` 함수로 감싸기 | 주석대로, 실행 중 `git pull`이 이 파일을 바꿔도 이미 읽은 함수를 끝까지 실행 |

각 줄의 원리(`set -e`, `flock`, `--ff-only`, 함수로 감싸기)는 [Oracle Cloud 시리즈의 배포 스크립트 글](/posts/oracle-cloud-deploy-deploy-script)에서 자세히 다룹니다.

### 테스트가 실패하면 배포가 멈춤

`mvn -q package`는 Maven의 `test` 단계를 포함합니다. 테스트가 하나라도 실패하면 Maven이 실패로 끝나고, `set -e` 때문에 `docker compose up`까지 가지 않습니다. 실행 중인 컨테이너는 이전 버전 그대로입니다. GitHub Actions에는 실패로 표시됩니다([테스트](/posts/fashion-archive-test-tests)).

이 줄은 워크플로를 처음 만든 날(2026-09-30) 바로 이어진 커밋 `38e9880`에서 추가되었습니다. Dockerfile이 war를 복사하는 구조라, war를 새로 만들지 않으면 예전 war로 이미지가 만들어지기 때문입니다.

## 확인

GitHub 저장소의 **Actions** 탭에서 `deploy` 실행을 열고, 마지막 줄의 `배포 완료: <커밋>`을 확인합니다. 서버에서는 다음으로 확인합니다.

```bash
cd /srv/devgear/fashion-archive
sudo docker compose ps
git log --oneline -1
```

## 정리

- main에 push하면 GitHub Actions가 SSH로 서버의 `deploy/deploy.sh`를 실행합니다.
- 스크립트는 다른 앱 배포를 기다린 뒤 `git pull --ff-only` → `mvn -q package` → `docker compose up -d --build` → 정리 순서로 진행합니다.
- `mvn package`가 테스트를 포함하므로, 테스트가 실패하면 이미지를 만들지 않고 이전 버전이 계속 실행됩니다.
