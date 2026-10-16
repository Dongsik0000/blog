---
title: "GitHub Actions로 push하면 배포되게 하기"
slug: "oracle-cloud-deploy-github-actions"
category: "development"
publishedAt: "2026-10-07"
summary: "main 브랜치에 push하면 서버의 배포 스크립트를 SSH로 실행하는 워크플로를 작성하고, Secrets 등록, known_hosts, 동시 실행 제어를 설명합니다."
series: "oracle-cloud"
chapter: "deploy"
seriesOrder: 3
---
## 이 글에서 할 일

**GitHub Actions**는 저장소에 push 같은 일이 생기면 정해 둔 작업을 GitHub의 컴퓨터에서 자동으로 실행해 주는 기능입니다. 이 글에서는 main 브랜치에 push하면 서버의 [배포 스크립트](/posts/oracle-cloud-deploy-deploy-script)를 실행하는 워크플로를 만듭니다.

## 용어

| 용어 | 뜻 |
|---|---|
| 워크플로(workflow) | 자동으로 실행할 작업 전체. `.github/workflows/` 폴더의 YAML 파일 하나 |
| 트리거(`on`) | 언제 실행할지 (push, 버튼 클릭 등) |
| 잡(job) | 워크플로 안의 작업 단위. 각 잡은 새 임시 컴퓨터(러너)에서 실행 |
| 스텝(step) | 잡 안에서 차례로 실행하는 명령 |
| Secrets | 저장소에 암호화해 보관하는 비밀값. 로그에 출력되면 `***`로 가려짐 |

## 워크플로 파일

`.github/workflows/deploy.yml`

```yaml
name: deploy

on:
  push:
    branches: [main]
  workflow_dispatch:

concurrency:
  group: deploy
  cancel-in-progress: false

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: 서버에서 배포 스크립트 실행
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
          ssh "$DEPLOY_USER@$DEPLOY_HOST" 'bash /srv/myapp/deploy/deploy.sh'
```

### on: 언제 실행하나

| 줄 | 뜻 |
|---|---|
| `push: branches: [main]` | main 브랜치에 push될 때 |
| `workflow_dispatch:` | GitHub 화면의 **Run workflow** 버튼으로 직접 실행할 수도 있게 함. 코드 변경 없이 다시 배포할 때 씁니다 |

### concurrency: 겹치지 않게

짧은 시간에 여러 번 push하면 워크플로도 여러 번 실행됩니다. `concurrency.group`이 같은 실행은 **한 번에 하나만** 돕니다. `cancel-in-progress: false`는 진행 중인 배포를 중간에 끊지 않는다는 뜻입니다. 배포 도중에 끊기면 컨테이너가 반쯤 바뀐 상태로 남을 수 있기 때문입니다.

기다리는 실행은 **하나만** 남습니다. 배포 A가 도는 동안 B와 C가 차례로 들어오면, 기다리던 B는 취소되고 C가 대기합니다.

| 순서 | A (실행 중) | B | C |
|---|---|---|---|
| B push | 계속 실행 | 대기 | |
| C push | 계속 실행 | **취소** | 대기 |
| A 끝남 | 완료 | | 실행 |

배포에서는 이 동작이 문제가 되지 않습니다. 배포 스크립트가 `git pull`로 **그 시점의 최신 main**을 받기 때문에, C가 실행되면 B의 변경도 함께 배포됩니다. 모든 실행을 하나씩 다 돌려야 하는 작업이라면 `concurrency`에 `queue: max`를 더해 최대 100개까지 대기시킬 수 있습니다. 이 옵션은 `cancel-in-progress: true`와 함께 쓸 수 없습니다.

이 설정은 **같은 저장소 안에서만** 동작합니다. 다른 앱 저장소의 배포와 겹치는 것은 서버 쪽 [flock](/posts/oracle-cloud-deploy-deploy-script)이 막습니다.

### steps: 하는 일

1. `install -m 700 -d ~/.ssh`: 러너에 `.ssh` 폴더를 본인만 쓸 수 있게 만듭니다.
2. Secrets의 개인 키를 파일로 쓰고 `600` 권한을 줍니다. 권한이 넓으면 ssh가 키 사용을 거부합니다.
3. Secrets의 서버 공개 키를 `known_hosts`에 씁니다(아래에서 설명).
4. 서버에 SSH로 접속해 배포 스크립트를 실행합니다. 스크립트가 실패하면 ssh도 실패 코드를 돌려주어 워크플로가 **실패**(빨간색)로 표시됩니다.

빌드를 서버에서 하므로 `actions/checkout`(러너에 코드 받기) 스텝이 필요 없습니다.

### 비밀값을 env로 넘기는 이유

`run` 안에 `${{ secrets.DEPLOY_SSH_KEY }}`를 직접 쓰지 않고 `env`로 넘긴 뒤 `"$SSH_KEY"`로 씁니다. `${{ }}`는 스크립트가 실행되기 **전에** 글자 그대로 끼워 넣어지므로, 값에 따옴표나 특수 문자가 있으면 명령이 깨지거나 의도치 않은 명령이 실행될 수 있습니다. 환경 변수로 넘기면 셸이 값을 하나의 문자열로 다룹니다. GitHub 공식 문서의 보안 강화 안내도 이 방식을 권합니다.

## Secrets 준비하기

저장소의 **Settings → Secrets and variables → Actions → New repository secret**에서 네 개를 등록합니다.

| 이름 | 값 | 만드는 법 |
|---|---|---|
| `DEPLOY_SSH_KEY` | 배포 전용 **개인 키** 전체 (`-----BEGIN ...`부터 `END ...-----`까지) | [다음 글](/posts/oracle-cloud-deploy-deploy-key)에서 만듦 |
| `DEPLOY_KNOWN_HOSTS` | 서버의 SSH 공개 키 한 줄 | 아래 설명 |
| `DEPLOY_HOST` | 서버 공인 IP 또는 도메인 | |
| `DEPLOY_USER` | `ubuntu` | |

<!-- 📷 스크린샷: GitHub 저장소 Secrets 등록 화면 -->

### known_hosts: 진짜 서버인지 확인

[SSH 접속 글](/posts/oracle-cloud-instance-ssh-connect)에서 처음 접속할 때 "이 서버를 믿겠습니까?"라고 물었습니다. 러너는 이 질문에 답할 수 없으므로, 서버의 공개 키를 미리 알려 줍니다. 그러면 누군가 중간에서 서버인 척해도 키가 달라 접속이 거부됩니다.

내 PC에서 서버의 공개 키를 받습니다.

```bash
ssh-keyscan -t ed25519 <서버 주소>
```

```text
<서버 주소> ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA...
```

이 한 줄을 `DEPLOY_KNOWN_HOSTS`에 넣습니다. 받은 키가 진짜인지는 서버에 SSH로 들어가 지문(fingerprint)을 비교해 확인합니다.

```bash
ssh-keyscan -t ed25519 <서버 주소> | ssh-keygen -lf -      # 내 PC에서
ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub           # 서버에서
```

두 명령이 출력하는 `SHA256:...` 값이 같아야 합니다.

`StrictHostKeyChecking=no` 옵션으로 확인을 끄는 예제를 인터넷에서 자주 봅니다. 동작은 하지만 위의 보호가 사라지므로 쓰지 않습니다.

## 확인하기

1. 아무 커밋이나 main에 push합니다.
2. 저장소의 **Actions** 탭에서 `deploy` 워크플로를 엽니다.
3. 스텝 로그 마지막에 `배포 완료: <커밋>`이 보이면 성공입니다. 배포 스크립트가 앱의 응답까지 확인한 뒤에 출력하는 줄입니다.

| 로그 메시지 | 원인 |
|---|---|
| `Permission denied (publickey)` | 서버 `authorized_keys`에 공개 키가 없거나, Secrets의 개인 키가 잘렸습니다(줄바꿈 포함 전체를 넣어야 함) |
| `Host key verification failed` | `DEPLOY_KNOWN_HOSTS`가 비었거나 서버 키와 다릅니다 |
| `Connection timed out` | `DEPLOY_HOST`가 틀렸거나 22번 포트가 막혔습니다 |
| `배포 후 앱이 응답하지 않습니다` | 컨테이너는 떴지만 앱이 시작하지 못했습니다. 서버에서 `docker compose logs`로 원인을 찾습니다 |
| 스크립트의 오류 메시지 | 서버에서 [손으로 실행](/posts/oracle-cloud-deploy-deploy-script)해 같은 오류가 나는지 봅니다 |

## 정리

- 워크플로는 main에 push되면 서버에 SSH로 접속해 `deploy.sh`를 실행합니다.
- `concurrency`로 같은 저장소의 배포가 겹치지 않게 합니다. 대기는 하나만 남고, 최신 실행이 최신 main을 배포합니다.
- 비밀값은 Secrets에 두고 `env`로 넘깁니다.
- `known_hosts`로 진짜 서버인지 확인하고, 확인을 끄는 옵션은 쓰지 않습니다.
