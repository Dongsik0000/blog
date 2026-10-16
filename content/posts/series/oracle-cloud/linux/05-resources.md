---
title: "디스크와 메모리 사용량 확인하기"
slug: "oracle-cloud-linux-resources"
category: "development"
publishedAt: "2026-10-07"
summary: "df, du, free, docker stats로 서버의 남은 디스크와 메모리를 확인하고, 한 서버에서 여러 앱이 메모리를 나눠 쓰는 계획을 세우는 법을 설명합니다."
series: "oracle-cloud"
chapter: "linux"
seriesOrder: 5
---
## 이 글에서 할 일

서버가 갑자기 느려지거나 앱이 꺼지는 가장 흔한 원인은 **디스크 가득 참**과 **메모리 부족**입니다. 이 글에서는 둘을 확인하는 명령을 익히고, 여러 앱이 메모리를 나눠 쓰는 계획을 세워 봅니다.

## 디스크: df와 du

```bash
df -h /
```

```text
Filesystem      Size  Used Avail Use% Mounted on
/dev/sda1        45G   18G   27G  41% /
```

| 열 | 뜻 |
|---|---|
| `Size` | 전체 크기 |
| `Used` / `Avail` | 쓴 양 / 남은 양 |
| `Use%` | 사용률. 80%를 넘으면 정리를 시작하는 것이 좋습니다 |

- `-h`: 사람이 읽기 쉬운 단위(G, M)로 보여 줍니다.

어디가 공간을 차지하는지는 `du`로 찾습니다.

```bash
sudo du -sh /var/lib/docker /var/log /srv 2>/dev/null
```

Docker로 배포하는 서버에서 디스크를 가장 많이 늘리는 것은 보통 **Docker 이미지와 빌드 캐시**입니다. 배포할 때마다 새 이미지가 생기기 때문입니다. 그래서 배포 스크립트에서 오래된 이미지를 자동으로 정리하도록 만듭니다. 자세한 내용은 [Docker 정리](/posts/oracle-cloud-docker-cleanup)에서 다룹니다.

## 메모리: free

```bash
free -h
```

메모리 10GB 서버라면 다음과 같은 형태로 나옵니다.

```text
               total        used        free      shared  buff/cache   available
Mem:           9.7Gi       2.9Gi       1.1Gi        44Mi       5.8Gi       6.7Gi
Swap:          2.0Gi        16K        2.0Gi
```

여기서 초보자가 가장 많이 오해하는 것이 `free` 열입니다. "메모리가 1.1Gi밖에 안 남았다"고 생각하기 쉽지만, 봐야 할 것은 `available`입니다.

| 열 | 뜻 |
|---|---|
| `used` | 프로그램이 실제로 쓰는 메모리 |
| `buff/cache` | 리눅스가 파일을 빨리 읽으려고 잠시 담아 둔 공간. **필요하면 바로 비워서 내줍니다** |
| `available` | 새 프로그램이 실제로 쓸 수 있는 양. **이 값을 봅니다** |

리눅스는 남는 메모리를 놀리지 않고 캐시로 씁니다. 그래서 `free`가 작아 보이는 것은 정상입니다. 위 예시는 `available`이 6.7Gi라 여유가 있는 상태입니다.

## 앱별 메모리: docker stats

```bash
docker stats --no-stream --format '{{.Name}} {{.MemUsage}}'
```

컨테이너마다 한 줄씩 나옵니다.

```text
app-a 321.2MiB / 768MiB
app-b 242.9MiB / 640MiB
```

`/` 앞은 지금 사용량, 뒤는 그 컨테이너의 **메모리 상한**입니다. 상한은 `docker-compose.yml`의 `mem_limit`으로 정합니다. 상한을 정하지 않으면 `/` 뒤에 서버 전체 메모리가 나옵니다.

## 여러 앱이 메모리 나눠 쓰기

서버 한 대에 여러 프로그램이 함께 살면, 하나가 메모리를 다 써 버릴 때 다른 프로그램까지 멈춥니다. 그래서 앱을 올리기 전에 표로 계획을 세워 둡니다. 예를 들어 10GB 서버에 Java 앱 두 개와 PostgreSQL을 올린다면 다음과 같습니다.

| 프로그램 | 메모리 상한 | 설정 위치 |
|---|---|---|
| Java 앱 A | 768MB (Java 힙 최대 512MB) | `docker-compose.yml`의 `mem_limit`, `Dockerfile`의 `-Xmx` |
| Java 앱 B | 640MB (Java 힙 최대 384MB) | 같은 방식 |
| PostgreSQL | `shared_buffers = 128MB` (기본값) + 연결별 메모리 | `/etc/postgresql/16/main/postgresql.conf` |
| nginx | 상한 없음. 매우 적게 씀 | |
| 스왑 | 2GB | 위 프로그램이 순간적으로 넘칠 때의 안전장치 |

합계가 서버 메모리보다 충분히 작으면 됩니다. 새 앱을 추가할 때마다 이 표에 한 줄을 더하고 합계를 다시 봅니다.

**왜 컨테이너 상한이 Java 힙보다 큰가?** Java는 힙(객체를 담는 공간) 말고도 클래스 정보, 스레드, 코드 캐시에 메모리를 씁니다. 힙만큼만 상한을 주면 힙 밖 메모리 때문에 컨테이너가 강제 종료됩니다. 그래서 힙보다 여유를 둡니다. 이 내용은 [메모리 상한 나누기](/posts/oracle-cloud-docker-memory)에서 자세히 다룹니다.

## 실시간으로 보기

```bash
top          # q로 종료. 기본 설치되어 있음
```

`top` 화면에서 `M`을 누르면 메모리 순, `P`를 누르면 CPU 순으로 정렬됩니다.

## 정리

| 확인할 것 | 명령 | 볼 값 |
|---|---|---|
| 디스크 남은 양 | `df -h /` | `Use%`가 80% 미만인지 |
| 디스크 많이 쓰는 곳 | `sudo du -sh 경로` | Docker, 로그 |
| 메모리 여유 | `free -h` | **`available`** |
| 앱별 메모리 | `docker stats --no-stream` | 상한 대비 사용량 |
