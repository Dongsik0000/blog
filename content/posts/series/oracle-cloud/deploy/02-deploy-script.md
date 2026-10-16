---
title: "배포 스크립트 작성하기 (set -euo pipefail과 flock)"
slug: "oracle-cloud-deploy-deploy-script"
category: "development"
publishedAt: "2026-10-07"
summary: "서버에서 실제로 배포를 수행하는 deploy.sh를 한 줄씩 작성하며 안전한 셸 스크립트의 기본과, 여러 앱 배포가 겹치지 않게 하는 flock을 설명합니다."
series: "oracle-cloud"
chapter: "deploy"
seriesOrder: 2
---
## 이 글에서 할 일

GitHub Actions가 서버에 접속해 실행하는 것은 앱 저장소의 `deploy/deploy.sh` 하나입니다. 이 스크립트를 작성합니다. 배포 순서를 파일로 남겨 두면 자동 배포든 손으로 하는 배포든 항상 같은 순서로 실행됩니다.

## 전체 스크립트

`deploy/deploy.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

main() {
    exec 9>/tmp/deploy.lock
    flock -w 900 9 || { echo "다른 앱 배포가 15분 안에 끝나지 않아 중단합니다" >&2; return 1; }

    cd /srv/myapp
    git pull --ff-only
    docker compose up -d --build

    for _ in $(seq 1 24); do
        curl -fsS -o /dev/null --max-time 5 http://127.0.0.1:8080/shop/ && break
        sleep 5
    done
    curl -fsS -o /dev/null --max-time 5 http://127.0.0.1:8080/shop/ \
        || { echo "배포 후 앱이 응답하지 않습니다: docker compose logs 로 확인하세요" >&2; return 1; }

    docker image prune -f > /dev/null || echo "이미지 정리 실패 (배포는 완료)" >&2
    docker builder prune -f --filter until=168h > /dev/null || echo "빌드 캐시 정리 실패 (배포는 완료)" >&2
    echo "배포 완료: $(git log --oneline -1)"
}

main "$@"
exit
```

[단일 스테이지 Dockerfile](/posts/oracle-cloud-docker-dockerfile)을 쓴다면 `docker compose` 앞에 `mvn -q package`(서버에서 war 빌드) 한 줄을 넣습니다.

## set -euo pipefail: 실패하면 멈추기

셸 스크립트는 기본적으로 **중간 명령이 실패해도 다음 줄을 계속 실행**합니다. 배포에서 이것은 위험합니다. 예를 들어 `git pull`이 실패했는데 `docker compose up`이 실행되면, 옛 코드로 다시 빌드하고 "배포 완료"를 출력합니다. GitHub Actions에도 성공으로 표시되어 실패를 알아채지 못합니다.

| 옵션 | 뜻 |
|---|---|
| `-e` | 명령이 실패하면(0이 아닌 종료 코드) 즉시 스크립트 종료 |
| `-u` | 정의되지 않은 변수를 쓰면 오류. 오타 난 변수가 빈 값으로 넘어가는 것을 막음 |
| `-o pipefail` | `a \| b`에서 `a`가 실패해도 실패로 처리 (기본은 마지막 `b`의 결과만 봄) |

이 세 옵션은 운영 스크립트의 첫 줄에 습관처럼 넣습니다. [백업 스크립트](/posts/oracle-cloud-postgresql-backup-restore)도 같은 줄로 시작합니다.

### `|| echo`: 실패해도 괜찮은 명령

```bash
docker image prune -f > /dev/null || echo "이미지 정리 실패 (배포는 완료)" >&2
```

`-e` 때문에 정리 명령이 실패하면 스크립트가 실패로 끝납니다. 하지만 정리는 부가 작업이라 배포를 실패로 만들 이유가 없습니다. `|| 명령`을 붙이면 "실패하면 이것을 대신 실행"이 되어 `-e`가 발동하지 않습니다. `>&2`는 메시지를 오류 출력으로 보냅니다. 정리 명령의 뜻은 [정리 글](/posts/oracle-cloud-docker-cleanup)에서 설명했습니다.

## flock: 배포를 한 번에 하나씩

같은 서버에 앱이 여러 개 있으면, 각 앱의 GitHub Actions는 서로를 모릅니다. 두 앱을 거의 동시에 push하면 두 배포가 **동시에** 이미지를 빌드합니다. Maven 빌드와 Docker 빌드는 메모리와 CPU를 많이 써서, 동시에 돌면 서버가 버거워지고 실행 중인 앱까지 느려질 수 있습니다.

```bash
exec 9>/tmp/deploy.lock
flock -w 900 9 || { echo "다른 앱 배포가 15분 안에 끝나지 않아 중단합니다" >&2; return 1; }
```

| 부분 | 뜻 |
|---|---|
| `exec 9>/tmp/deploy.lock` | 잠금용 파일을 열어 9번 파일 번호로 잡아 둠 |
| `flock -w 900 9` | 9번 파일에 잠금을 걸음. 이미 누가 잠갔으면 **최대 900초(15분) 기다림** |
| `\|\| { ...; return 1; }` | 15분이 지나도 못 잠그면 메시지를 내고 실패로 종료 |

화장실 열쇠와 같습니다. 모든 앱의 배포 스크립트가 **같은 파일**(`/tmp/deploy.lock`)을 열쇠로 씁니다. 먼저 온 배포가 열쇠를 가져가고, 나중에 온 배포는 열쇠가 돌아올 때까지 기다립니다. 스크립트가 끝나면 파일이 닫히면서 잠금이 자동으로 풀립니다. 앱이 하나뿐이어도 넣어 두면, 앱이 늘었을 때 그대로 동작합니다.

## git pull --ff-only

```bash
git pull --ff-only
```

`--ff-only`는 "서버의 코드가 GitHub의 코드보다 **뒤처져 있기만** 할 때만 받는다"는 뜻입니다. 누군가 서버에서 직접 코드를 고쳐 커밋했다면 두 이력이 갈라져 있으므로, 자동으로 합치지 않고 **실패**합니다. `set -e` 때문에 배포가 멈추고, 이상한 상태로 배포되는 것을 막습니다.

## docker compose up -d --build

새 코드로 이미지를 빌드하고, 바뀐 컨테이너를 새로 만들어 교체합니다. 이 순간 기존 컨테이너가 내려가고 새 컨테이너가 뜨므로, Tomcat이 다시 뜨는 동안(수십 초) 그 앱에 접속하면 **502**가 날 수 있습니다. 개인 프로젝트라면 짧은 중단은 감수하는 것이 단순합니다.

## 앱이 실제로 응답하는지 확인

`docker compose up -d`는 컨테이너를 **시작시키고 바로** 끝납니다. 이 명령이 성공했다고 해서 Tomcat이 다 떴는지, 앱이 데이터베이스에 연결했는지는 알 수 없습니다. 여기서 바로 "배포 완료"를 출력하면, 앱이 시작하다 죽어도 GitHub Actions에는 성공으로 표시됩니다.

```bash
for _ in $(seq 1 24); do
    curl -fsS -o /dev/null --max-time 5 http://127.0.0.1:8080/shop/ && break
    sleep 5
done
curl -fsS -o /dev/null --max-time 5 http://127.0.0.1:8080/shop/ \
    || { echo "배포 후 앱이 응답하지 않습니다: docker compose logs 로 확인하세요" >&2; return 1; }
```

| 부분 | 뜻 |
|---|---|
| `for _ in $(seq 1 24)` ... `sleep 5` | 5초 간격으로 최대 24번(2분) 앱 주소를 요청해 봅니다. 응답이 오면 `break`로 바로 빠져나옵니다 |
| `curl -f` | 응답 코드가 400 이상이면 실패로 처리합니다. 앱이 500 오류를 내는 것도 실패로 잡습니다 |
| `--max-time 5` | 한 번 요청에 5초 넘게 걸리면 포기합니다 |
| 마지막 `curl ... \|\| { ...; return 1; }` | 2분이 지나도 응답이 없으면 실패로 끝냅니다. GitHub Actions에 실패로 표시됩니다 |

반복문 안의 `curl` 실패는 `&&` 앞에 있어 `set -e`가 발동하지 않습니다. 그래서 마지막에 한 번 더 요청해 결과를 판정합니다. 확인할 주소는 앱이 데이터베이스를 읽어야 그릴 수 있는 페이지로 고르면, DB 연결까지 함께 확인됩니다. 2분은 Tomcat이 뜨는 시간에 맞춘 값이므로 앱에 맞게 늘리거나 줄입니다.

이미지와 캐시 정리는 이 확인을 **통과한 뒤에** 합니다. 확인 전에 지우면, 새 앱이 실패했을 때 되돌릴 이전 이미지까지 지워질 수 있습니다.

## main 함수로 감싸는 이유

스크립트 전체를 `main()` 함수로 감싸고 마지막에 `main "$@"`과 `exit`를 둡니다. bash는 스크립트를 실행하면서 파일을 조금씩 읽는데, 실행 도중 `git pull`이 **이 스크립트 파일 자체를 바꿀 수 있습니다.** 함수로 감싸면 bash가 함수 전체를 먼저 읽은 뒤 실행하므로, 실행 중 파일이 바뀌어도 엉뚱한 줄을 실행하지 않습니다. `exit`는 그 뒤에 무엇이 추가되어도 읽지 않게 합니다.

## 손으로 실행해 보기

자동화하기 전에 서버에서 직접 실행해 봅니다.

```bash
bash /srv/myapp/deploy/deploy.sh
```

| 결과 | 의미 |
|---|---|
| 마지막 줄 `배포 완료: <커밋>` | 성공. 앱이 응답하는 것까지 확인됐습니다 |
| `배포 후 앱이 응답하지 않습니다` | 컨테이너는 떴지만 앱이 응답하지 않습니다. `docker compose logs`에서 시작 오류를 찾습니다 |
| `Not possible to fast-forward` | 서버 코드가 GitHub과 갈라졌습니다. 서버에서 고친 내용을 확인하고 정리합니다 |
| `permission denied ... docker.sock` | 실행한 사용자가 `docker` 그룹이 아닙니다 ([Docker 설치](/posts/oracle-cloud-docker-install)) |
| 빌드 중 오류 | 출력의 마지막 오류 줄을 보고 코드나 Dockerfile을 고칩니다 |

## 정리

- `set -euo pipefail`로 실패하면 즉시 멈추고, 실패해도 되는 명령에만 `|| ...`를 붙입니다.
- `flock`으로 여러 앱의 배포를 한 번에 하나씩 실행합니다.
- `git pull --ff-only`로 서버 코드가 GitHub과 갈라졌으면 배포를 멈춥니다.
- 컨테이너를 띄운 뒤 앱 주소에 실제로 요청해, 응답할 때만 배포 성공으로 끝냅니다.
- 스크립트를 `main` 함수로 감싸 실행 중 자기 파일이 바뀌어도 안전하게 합니다.
