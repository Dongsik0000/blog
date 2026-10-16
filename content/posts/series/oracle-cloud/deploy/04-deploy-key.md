---
title: "배포 전용 SSH 키 만들고 권한 좁히기"
slug: "oracle-cloud-deploy-deploy-key"
category: "development"
publishedAt: "2026-10-07"
summary: "GitHub Actions에 줄 배포 전용 SSH 키를 만들고, authorized_keys의 restrict와 command 옵션으로 그 키가 배포 스크립트만 실행할 수 있게 제한합니다."
series: "oracle-cloud"
chapter: "deploy"
seriesOrder: 4
---
## 이 글에서 할 일

GitHub Actions가 서버에 접속하려면 SSH 개인 키를 GitHub Secrets에 맡겨야 합니다. 이 키가 새면 누구든 서버에 접속할 수 있습니다. 그래서 두 가지를 지킵니다.

1. 내가 쓰는 키와 **다른, 배포 전용 키**를 만든다.
2. 그 키로는 **배포 스크립트만** 실행할 수 있게 제한한다.

## 1단계. 배포 전용 키 만들기

내 PC에서 실행합니다.

```bash
ssh-keygen -t ed25519 -f ./deploy_myapp -N "" -C "github-actions-myapp"
```

| 옵션 | 뜻 |
|---|---|
| `-f ./deploy_myapp` | `deploy_myapp`(개인 키), `deploy_myapp.pub`(공개 키) 두 파일을 만듦 |
| `-N ""` | 암호(passphrase) 없음. 자동 실행에서는 암호를 입력할 사람이 없기 때문입니다 |
| `-C "github-actions-myapp"` | 메모. 나중에 `authorized_keys`에서 어떤 키인지 알아보는 표시가 됩니다 |

- `deploy_myapp`(개인 키) 내용 전체를 GitHub Secrets의 `DEPLOY_SSH_KEY`에 등록합니다.
- `deploy_myapp.pub`(공개 키)는 다음 단계에서 서버에 등록합니다.
- 등록이 끝나면 PC의 개인 키 파일은 지웁니다. GitHub Secrets에만 있으면 됩니다. 다시 필요하면 새로 만들면 됩니다.

## 2단계. 서버에 등록하면서 권한 좁히기

서버의 `~/.ssh/authorized_keys`에 공개 키를 한 줄 추가합니다. 이때 줄 **앞에 옵션**을 붙입니다.

```text
restrict,command="bash /srv/myapp/deploy/deploy.sh" ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA... github-actions-myapp
```

| 옵션 | 뜻 |
|---|---|
| `command="..."` | 이 키로 접속하면 **접속한 쪽이 무슨 명령을 보내든 무시하고 이 명령만** 실행합니다 (강제 명령) |
| `restrict` | 포트 포워딩(터널), 에이전트 포워딩, X11, 터미널(pty) 할당을 모두 막습니다 |

이렇게 하면 이 키가 새더라도 할 수 있는 일은 "배포 스크립트 실행" 하나뿐입니다. 셸을 얻을 수도, 서버를 경유해 다른 곳에 접속할 수도 없습니다.

`command=`가 있으면 워크플로의 `ssh ... 'bash /srv/myapp/deploy/deploy.sh'`에 적은 명령은 무시되고 등록한 명령이 실행됩니다. 둘을 같게 적어 두면 읽는 사람이 헷갈리지 않습니다.

### 편집할 때 주의

`authorized_keys`를 잘못 고치면 **나 자신도 접속하지 못할 수 있습니다.**

```bash
cp ~/.ssh/authorized_keys ~/authorized_keys.bak-$(date +%F)   # 백업
nano ~/.ssh/authorized_keys                                    # 맨 아래에 한 줄 추가
```

- 한 키는 반드시 **한 줄**입니다. 붙여 넣다가 줄이 나뉘면 그 키는 동작하지 않습니다.
- 내가 쓰는 키 줄은 건드리지 않습니다.
- 저장한 뒤 **지금 SSH 창은 닫지 말고** 새 창으로 내 키 접속이 되는지 확인합니다.

## 3단계. 확인하기

내 PC에서 배포 키로 접속해 봅니다(개인 키를 지우기 전에). 강제 명령이 동작하면 **실제로 배포가 한 번 실행되므로**, 배포해도 되는 상태에서 확인합니다.

```bash
ssh -i ./deploy_myapp ubuntu@<서버 주소> 'whoami'
```

`whoami`가 아니라 배포 스크립트가 실행되고 `배포 완료: ...`가 나오면 강제 명령이 동작하는 것입니다. 셸 창이 열리지 않는 것도 확인합니다.

```bash
ssh -i ./deploy_myapp ubuntu@<서버 주소>
```

배포 스크립트만 실행되고 연결이 끝나야 합니다.

## 키 관리 습관

서버에 등록된 키를 가끔 점검합니다. 키마다 `-C`로 붙인 메모가 줄 끝에 있어서 알아보기 쉽습니다.

```bash
awk '{print $NF}' ~/.ssh/authorized_keys
```

옵션이 있는 줄은 마지막 칸이 메모입니다. 다음과 같은 키는 지웁니다.

| 점검 | 조치 |
|---|---|
| 더 이상 쓰지 않는 앱의 배포 키 | 줄 삭제 |
| 강제 명령(`command=`)이 가리키는 스크립트가 없는 키 | 줄 삭제 |
| 배포용인데 `restrict,command=`가 없는 키 | 옵션 추가 |
| 누구의 키인지 알 수 없는 키 | 확인 후 삭제 |

앱이 여러 개라면 **앱마다 배포 키를 따로** 만듭니다. 한 앱의 키가 새도 다른 앱 배포에는 영향이 없고, 앱을 없앨 때 그 키 한 줄만 지우면 됩니다.

GitHub Secrets에 넣은 키가 샜다고 의심되면, 서버의 `authorized_keys`에서 그 줄을 먼저 지우고 새 키를 만들어 다시 등록합니다.

## 정리

- GitHub Actions에는 내 키가 아닌 **배포 전용 키**를 줍니다.
- `authorized_keys`에서 `restrict,command="..."`로 그 키가 배포 스크립트만 실행하게 합니다.
- 편집 전에 백업하고, 기존 SSH 창을 유지한 채 새 창으로 확인합니다.
- 쓰지 않는 키와 제한 없는 배포 키는 정기적으로 정리합니다.
