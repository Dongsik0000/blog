---
title: "데이터베이스를 외부에 열지 않기"
slug: "oracle-cloud-postgresql-network-access"
category: "development"
publishedAt: "2026-10-07"
summary: "PostgreSQL을 127.0.0.1에서만 듣게 하는 listen_addresses 설정과, Docker 컨테이너의 앱이 그 주소로 접속할 수 있게 하는 방법을 설명합니다."
series: "oracle-cloud"
chapter: "postgresql"
seriesOrder: 6
---
## 이 글에서 할 일

인터넷에 열린 데이터베이스는 자동화된 공격 도구가 끊임없이 찾아다니는 표적입니다. 그래서 PostgreSQL은 **서버 자신(`127.0.0.1`)에서만** 접속할 수 있게 둡니다. 이 글에서는 그 설정과, 그래도 앱이 정상적으로 접속하는 원리를 설명합니다.

## 세 겹의 차단

| 겹 | 설정 | 상태 |
|---|---|---|
| ① Oracle Cloud 보안 목록 | 5432 규칙을 추가하지 않음 | 외부 요청이 서버까지 오지 않음 |
| ② Ubuntu iptables | 5432 허용 규칙을 추가하지 않음 | 와도 거부 |
| ③ PostgreSQL `listen_addresses` | `localhost` | 애초에 외부 주소에서 듣지 않음 |

①, ②는 앞 장에서 다뤘으니, 여기서는 ③을 봅니다.

## listen_addresses

```bash
sudo grep -n listen_addresses /etc/postgresql/16/main/postgresql.conf
```

설치 직후에는 다음과 같은 줄이 나옵니다.

```text
#listen_addresses = 'localhost'		# what IP address(es) to listen on;
```

줄 앞에 `#`이 있어 **주석 처리**되어 있습니다. 주석인 설정은 **기본값**이 적용되고, `listen_addresses`의 기본값은 `localhost`입니다. 즉 아무것도 바꾸지 않은 상태가 가장 안전한 상태입니다.

실제로 듣고 있는 주소로 확인합니다.

```bash
sudo ss -ltnp | grep 5432
```

```text
127.0.0.1:5432  ... "postgres"
```

인터넷 블로그에서 "외부에서 접속하려면 `listen_addresses = '*'`로 바꾸세요"라는 안내를 자주 봅니다. 이렇게 하면 모든 네트워크에서 듣게 됩니다. 이 시리즈에서는 **바꾸지 않습니다.** PC에서 데이터베이스를 봐야 할 때는 [SSH 터널](/posts/oracle-cloud-postgresql-dbeaver)을 씁니다.

## 컨테이너 안의 앱은 어떻게 접속하나

앱을 Docker 컨테이너로 실행하면 문제가 하나 생깁니다. 보통 컨테이너는 서버와 **분리된 네트워크**를 가지기 때문에, 컨테이너 안의 `127.0.0.1`은 컨테이너 자신을 가리킵니다. 그러면 서버의 PostgreSQL에 닿지 않습니다.

가장 간단한 해결책은 **host 네트워크 모드**입니다.

`docker-compose.yml` (일부)

```yaml
services:
  app:
    network_mode: host
```

`network_mode: host`는 컨테이너가 **서버의 네트워크를 그대로 함께 쓰게** 합니다. 그래서 컨테이너 안의 `127.0.0.1:5432`가 곧 서버의 PostgreSQL입니다. 앱 설정의 접속 주소도 그대로 `127.0.0.1`을 씁니다.

```text
DB_URL=jdbc:postgresql://127.0.0.1:5432/myapp
```

host 모드는 포트 충돌을 직접 관리해야 하는 단점이 있습니다. 장단점과 다른 방법은 [docker compose](/posts/oracle-cloud-docker-compose)에서 다시 다룹니다.

## 정리

- PostgreSQL은 기본값(`listen_addresses = 'localhost'`)이면 서버 자신에서만 듣습니다. 바꾸지 않습니다.
- 보안 목록, iptables, listen_addresses 세 겹으로 외부 접속을 막습니다.
- 컨테이너의 앱은 `network_mode: host`로 서버의 `127.0.0.1:5432`에 접속할 수 있습니다.
- 외부에서 봐야 할 때는 포트를 열지 않고 SSH 터널을 씁니다.
