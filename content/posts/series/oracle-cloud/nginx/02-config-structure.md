---
title: "nginx 설정 파일 구조"
slug: "oracle-cloud-nginx-config-structure"
category: "development"
publishedAt: "2026-10-07"
summary: "nginx.conf에서 시작해 conf.d, sites-available, sites-enabled, snippets로 이어지는 설정 파일의 관계와 블록 계층을 설명하고, 앱별로 설정을 나누는 방법을 정리합니다."
series: "oracle-cloud"
chapter: "nginx"
seriesOrder: 2
---
## 이 글에서 할 일

nginx 설정은 파일 하나가 아니라 여러 파일이 `include`로 연결된 구조입니다. 어느 파일에 무엇을 써야 하는지 헷갈리지 않도록 전체 지도를 그립니다.

## 파일 지도

Ubuntu에서 apt로 설치한 nginx는 다음 구조를 씁니다. 오른쪽 설명 중 `server-tokens.conf`, `shop-limit.conf`, `snippets/` 안의 파일은 이 장에서 직접 만들 파일입니다.

```text
/etc/nginx/
├── nginx.conf                  ← 시작점. 전체 공통 설정
├── conf.d/                     ← http 블록에 자동 포함 (서버 전체에 적용할 설정)
│   ├── server-tokens.conf          server_tokens off;
│   └── shop-limit.conf             로그인 속도 제한 영역 정의
├── sites-available/            ← 사이트 설정 원본 보관소
│   ├── default                     설치할 때 들어 있는 기본 사이트
│   └── example.com
├── sites-enabled/              ← 실제로 켜진 사이트 (sites-available을 가리키는 링크)
│   └── example.com -> /etc/nginx/sites-available/example.com
└── snippets/                   ← 여러 곳에서 include해 쓰는 조각
    ├── deny-private.conf           숨김 파일 차단
    ├── react-app.conf              /app/
    └── shop.conf                   /shop/, /uploads/
```

## 시작점: nginx.conf

`/etc/nginx/nginx.conf` (주요 줄)

```nginx
user www-data;
worker_processes auto;
error_log /var/log/nginx/error.log;

events {
    worker_connections 768;
}

http {
    sendfile on;
    access_log /var/log/nginx/access.log;
    gzip on;

    include /etc/nginx/conf.d/*.conf;
    include /etc/nginx/sites-enabled/*;
}
```

| 줄 | 뜻 |
|---|---|
| `user www-data;` | nginx 작업 프로세스가 `www-data` 사용자 권한으로 실행됩니다. 그래서 nginx가 읽을 파일은 `www-data`가 읽을 수 있어야 합니다 |
| `worker_processes auto;` | CPU 개수만큼 작업 프로세스를 띄웁니다 |
| `worker_connections 768;` | 프로세스 하나가 동시에 처리할 연결 수 |
| `gzip on;` | 응답을 압축해 전송량을 줄입니다 |
| `include .../conf.d/*.conf;` | conf.d의 모든 `.conf`를 **http 블록 안에** 넣습니다 |
| `include .../sites-enabled/*;` | 켜진 사이트 설정을 http 블록 안에 넣습니다 |

이 파일은 보통 고치지 않습니다. 필요한 설정은 `conf.d/`나 사이트 파일에 추가합니다. 패키지를 업데이트할 때 `nginx.conf`를 고쳐 두었다면 덮어쓸지 묻는 질문이 나와 번거로워지기 때문입니다.

## 블록 계층

nginx 설정은 중괄호 `{}` 블록이 겹겹이 쌓인 구조입니다. **어느 블록 안에 쓰느냐에 따라 적용 범위가 달라집니다.**

```text
main (파일 맨 바깥)        user, worker_processes, error_log
└── events { }              연결 처리 방식
└── http { }                모든 웹 사이트 공통   ← conf.d/*.conf가 여기 들어감
    └── server { }          사이트 하나 (포트 + 도메인)   ← sites-enabled/*가 여기 들어감
        └── location { }    주소 경로 하나        ← snippets/*.conf의 location들
```

그래서 파일을 둘 위치가 정해집니다.

| 설정 | 들어가야 할 블록 | 둘 파일 |
|---|---|---|
| `limit_req_zone` (속도 제한 영역 정의) | http | `conf.d/shop-limit.conf` |
| `server_tokens off;` (전체에 적용) | http | `conf.d/server-tokens.conf` |
| `location /shop/ { ... }` | server | `snippets/shop.conf`를 server 블록에서 include |

`limit_req_zone`을 snippets에 넣어 server 블록 안에서 include하면 "여기서는 쓸 수 없는 지시어"라는 오류가 납니다. 지시어마다 쓸 수 있는 블록이 정해져 있기 때문입니다. 공식 문서의 각 지시어 설명에 있는 **Context** 항목이 그 블록입니다.

## sites-available과 sites-enabled

- 실제 파일은 `sites-available`에 둡니다.
- 켜고 싶은 사이트만 `sites-enabled`에 **심볼릭 링크**(바로 가기)를 만듭니다.

```bash
sudo ln -s /etc/nginx/sites-available/example.com /etc/nginx/sites-enabled/   # 켜기
sudo rm /etc/nginx/sites-enabled/example.com                                  # 끄기 (원본은 남음)
ls -l /etc/nginx/sites-enabled/                                               # 켜진 사이트 확인
```

내 사이트 설정을 만들었다면 설치 때 들어 있던 `default` 링크는 지웁니다. 두 설정이 모두 80번 포트의 기본 서버를 차지하려고 해서 충돌하기 때문입니다.

설정을 백업할 때 **sites-enabled 폴더 안에 복사본을 두면 안 됩니다.** `sites-enabled/*`를 모두 include하므로 백업 파일까지 설정으로 읽혀 중복 오류가 납니다. 백업은 홈 폴더처럼 다른 곳에 둡니다.

## snippets: 앱별로 나눠 관리하기

앱이 여러 개라면 앱마다 location 설정을 별도 파일로 나누고, 사이트 설정에서 include합니다.

`/etc/nginx/sites-available/example.com` (443 server 블록 일부)

```nginx
    root /srv/site;
    index index.html;

    include /etc/nginx/snippets/deny-private.conf;
    include /etc/nginx/snippets/react-app.conf;
    include /etc/nginx/snippets/shop.conf;
```

이렇게 나누면 앱을 추가하거나 뺄 때 해당 파일과 include 한 줄만 다루면 됩니다. 앱 저장소의 `deploy/` 폴더에 같은 파일을 넣어 두면 nginx 설정도 앱 코드와 함께 버전 관리할 수 있습니다.

## 전체 설정을 한 번에 보기

include로 흩어진 설정을 합쳐서 보고 싶을 때는 다음 명령을 씁니다.

```bash
sudo nginx -T | less
```

`-T`는 설정을 검사한 뒤, 실제로 읽은 모든 파일 내용을 순서대로 출력합니다.

## 정리

- 시작점은 `nginx.conf`, 서버 전체 설정은 `conf.d/`, 사이트는 `sites-available/` + `sites-enabled/` 링크, 앱별 조각은 `snippets/`입니다.
- 블록 계층은 **main → http → server → location**이고, 지시어마다 쓸 수 있는 블록이 정해져 있습니다.
- 백업 파일을 `sites-enabled/`에 두지 않습니다.
- 합쳐진 설정은 `sudo nginx -T`로 봅니다.
