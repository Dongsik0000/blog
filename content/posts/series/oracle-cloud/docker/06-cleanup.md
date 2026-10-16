---
title: "이미지와 빌드 캐시 정리하기"
slug: "oracle-cloud-docker-cleanup"
category: "development"
publishedAt: "2026-10-07"
summary: "배포할 때마다 쌓이는 Docker 이미지와 빌드 캐시가 디스크를 얼마나 쓰는지 확인하고, 배포 스크립트에서 자동으로 정리하는 방법과 직접 정리하는 방법을 설명합니다."
series: "oracle-cloud"
chapter: "docker"
seriesOrder: 6
---
## 이 글에서 할 일

`docker compose up -d --build`로 배포할 때마다 새 이미지가 만들어지고, 빌드 중간 결과(캐시)가 쌓입니다. 그대로 두면 디스크가 가득 찹니다. 이 글에서는 사용량을 확인하고 정리하는 방법을 익힙니다.

## 사용량 확인

```bash
docker system df
```

다음과 같은 표가 나옵니다.

```text
TYPE            TOTAL     ACTIVE    SIZE      RECLAIMABLE
Images          5         2         1.681GB   76.14MB (4%)
Containers      2         2         59.7MB    0B (0%)
Local Volumes   0         0         0B        0B
Build Cache     113       0         3.532GB   1.851GB
```

| 열, 줄 | 뜻 |
|---|---|
| `Images` | 저장된 이미지. `ACTIVE`는 실행 중인 컨테이너가 쓰는 것 |
| `Build Cache` | 빌드할 때 재사용하려고 남겨 둔 레이어. Maven 의존성처럼 큰 레이어가 쌓여 **가장 크게 자랍니다** |
| `RECLAIMABLE` | 지워도 되는(쓰이지 않는) 양 |

어떤 이미지가 있는지는 목록으로 봅니다.

```bash
docker images
```

`REPOSITORY`가 `<none>`인 이미지는 새로 빌드하면서 이름을 잃은 예전 이미지입니다. 같은 이름(`myapp:local`)으로 새 이미지를 만들면 이름이 새 이미지로 옮겨 가고, 예전 이미지는 `<none>`으로 남습니다.

## 배포 스크립트에서 자동 정리

배포 스크립트에서 새 앱이 응답하는 것을 확인한 직후에 정리하면 따로 신경 쓸 일이 없습니다. 확인보다 먼저 정리하면 새 앱이 실패했을 때 되돌릴 이전 이미지까지 지워질 수 있습니다.

`deploy/deploy.sh` (일부, 전체는 [배포 스크립트 글](/posts/oracle-cloud-deploy-deploy-script))

```bash
docker compose up -d --build
# ... 앱이 응답할 때까지 확인 ...
docker image prune -f > /dev/null || echo "이미지 정리 실패 (배포는 완료)" >&2
docker builder prune -f --filter until=168h > /dev/null || echo "빌드 캐시 정리 실패 (배포는 완료)" >&2
```

| 명령 | 지우는 것 |
|---|---|
| `docker image prune -f` | 이름이 없는(`<none>`) 이미지 |
| `docker builder prune -f --filter until=168h` | 168시간(7일)보다 오래된 빌드 캐시 |

- `-f`: "정말 지울까요?" 질문 없이 진행
- `|| echo ... >&2`: 정리가 실패해도 **배포 자체는 성공**으로 둡니다. 정리는 부가 작업이라 배포를 실패로 만들 이유가 없기 때문입니다. 스크립트 전체는 [배포 스크립트 글](/posts/oracle-cloud-deploy-deploy-script)에서 다룹니다.

빌드 캐시를 7일치 남기는 이유는, 하루에 여러 번 배포할 때 캐시를 재사용해 빌드를 빠르게 하기 위해서입니다.

**이름이 있는 이미지는 자동 정리 대상이 아닙니다.** 더 이상 쓰지 않는 앱의 이미지나, 되돌리기용으로 따로 이름을 붙여 둔 이미지는 직접 관리합니다.

## 되돌리기용 이미지 남겨 두기

새 버전에 문제가 있을 때 바로 예전 버전으로 돌아가고 싶다면, 배포 전에 지금 이미지에 다른 이름(태그)을 붙여 둡니다.

```bash
docker tag myapp:local myapp:prev-$(date +%Y%m%d)
```

이름이 있으므로 `image prune`에 지워지지 않습니다. 필요 없어지면 직접 지웁니다.

## 직접 정리하기

```bash
docker rmi myapp:prev-20261001        # 이름으로 이미지 하나 지우기
docker image prune -a                 # 어떤 컨테이너도(멈춘 것 포함) 쓰지 않는 이미지 모두 (확인 질문 있음)
docker builder prune -a               # 사용 중이 아닌 빌드 캐시 모두 (확인 질문 있음)
docker system df                      # 정리 후 다시 확인
```

`docker image prune -a`는 되돌리기용으로 남긴 이미지까지 지웁니다. 반대로 멈춰 있는 컨테이너가 쓰는 이미지는 지우지 않으므로, 안 쓰는 컨테이너가 있다면 먼저 `docker ps -a`로 확인해 지웁니다. `docker builder prune`은 `-a` 없이 쓰면 어떤 빌드에도 연결되지 않은 캐시만 지우고, `-a`를 붙여야 사용 중이 아닌 캐시를 모두 지웁니다. 지우기 전에 `docker images`로 목록을 확인합니다. 빌드 캐시를 전부 지우면 다음 빌드는 의존성부터 다시 받으므로 오래 걸립니다.

## 정리

- `docker system df`로 이미지, 컨테이너, 빌드 캐시 사용량을 확인합니다.
- 빌드 캐시가 가장 크게 자라므로 주기적으로 정리합니다.
- 배포 스크립트에서 이름 없는 이미지와 오래된 캐시를 자동으로 지웁니다.
- 이름이 있는 이미지는 자동 정리되지 않으므로 직접 관리합니다.
