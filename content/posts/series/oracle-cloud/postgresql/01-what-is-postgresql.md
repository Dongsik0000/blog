---
title: "PostgreSQL은 무엇이고 어떻게 쓰이나"
slug: "oracle-cloud-postgresql-what-is-postgresql"
category: "development"
publishedAt: "2026-10-07"
summary: "데이터베이스, DBMS, 클러스터, 스키마, 롤(사용자) 같은 기본 용어를 정리하고, 서버 한 대에서 여러 앱이 PostgreSQL 하나를 나눠 쓰는 구조를 설명합니다."
series: "oracle-cloud"
chapter: "postgresql"
seriesOrder: 1
---
## 이 글에서 할 일

로그인이 있는 웹 앱은 회원 정보와 게시글 같은 데이터를 어딘가에 저장해야 합니다. 이 장에서는 그 저장소인 **PostgreSQL**을 설치하고 설정하는 방법을 다룹니다. 그 전에 용어부터 정리합니다.

## 데이터베이스와 DBMS

- **데이터베이스**: 정리된 데이터의 모음입니다. 엑셀 파일 여러 개를 모아 둔 폴더를 떠올리면 됩니다.
- **DBMS(Database Management System)**: 데이터베이스를 저장, 조회, 수정하고 여러 사람이 동시에 써도 깨지지 않게 관리하는 프로그램입니다. PostgreSQL, MySQL, Oracle Database가 여기에 해당합니다.
- **SQL**: DBMS에게 일을 시키는 언어입니다. `SELECT * FROM category;`처럼 씁니다.

PostgreSQL은 무료 오픈소스 DBMS이고, 표준 SQL을 잘 지키며 기능이 풍부해서 널리 쓰입니다.

## PostgreSQL의 구조

앱 두 개(`myapp`, `otherapp`)가 PostgreSQL 하나를 함께 쓴다면 구조는 다음과 같습니다.

```text
PostgreSQL 서버 (클러스터, Ubuntu에서는 "16/main")
├── 데이터베이스 myapp       ← myapp 앱 전용
│   └── 스키마 public
│       └── 테이블 app_user, category, ...
├── 데이터베이스 otherapp    ← otherapp 앱 전용
│   └── 스키마 public
│       └── 테이블 ...
└── 롤(사용자): postgres, myapp_user, otherapp_user
```

| 용어 | 뜻 | 비유 |
|---|---|---|
| 클러스터 | PostgreSQL 서버 프로그램 하나가 관리하는 전체 | 건물 |
| 데이터베이스 | 서로 완전히 분리된 데이터 묶음 | 층 |
| 스키마 | 데이터베이스 안의 이름 공간. 기본은 `public` | 사무실 |
| 테이블 | 실제 데이터가 행과 열로 저장되는 곳 | 서류함 |
| 롤(role) | 접속하는 사용자. 권한의 주체 | 출입증 |

## 원칙: 앱마다 데이터베이스와 사용자를 따로

PostgreSQL 하나에 여러 앱을 올릴 때는 앱마다 **데이터베이스와 접속 계정을 분리**합니다.

| 앱 | 데이터베이스 | 접속 계정 |
|---|---|---|
| myapp | `myapp` | `myapp_user` |
| otherapp | `otherapp` | `otherapp_user` |

이렇게 나누면 한 앱의 비밀번호가 새거나 코드에 버그가 있어도 **다른 앱의 데이터는 건드릴 수 없습니다.** 이것을 **최소 권한 원칙**이라고 합니다. 만드는 방법은 [앱용 데이터베이스 만들기](/posts/oracle-cloud-postgresql-app-database)에서 다룹니다.

## 서버에 직접 설치할까, Docker로 띄울까

PostgreSQL도 Docker 컨테이너로 실행할 수 있습니다. 이 시리즈는 앱(Tomcat)은 Docker로, PostgreSQL은 Ubuntu에 직접 설치합니다. 두 방식의 차이는 다음과 같습니다.

| | 서버에 직접 설치 (이 시리즈) | Docker 컨테이너 |
|---|---|---|
| 설치와 업데이트 | apt로 설치, 보안 업데이트도 apt로 | 이미지 태그를 바꿔 다시 띄움 |
| 데이터 위치 | `/var/lib/postgresql/` (자동) | 볼륨을 직접 연결해야 함. 빠뜨리면 컨테이너를 지울 때 데이터도 사라짐 |
| 관리자 접속 | `sudo -u postgres psql` | `docker exec`로 컨테이너 안에 들어가서 |
| 버전 고정, 여러 버전 | 서버에 하나 | 앱마다 다른 버전도 쉬움 |

서버 한 대에 데이터베이스 하나를 오래 운영한다면 직접 설치가 단순합니다. 앱마다 다른 PostgreSQL 버전이 필요하거나, 개발 PC와 똑같은 환경을 띄워야 한다면 Docker가 편합니다.

직접 설치한 PostgreSQL에 Docker 안의 앱이 접속하는 방법은 [compose 글](/posts/oracle-cloud-docker-compose)에서 다룹니다.

## 정리

- PostgreSQL은 데이터베이스를 관리하는 프로그램(DBMS)입니다.
- 구조는 **클러스터 → 데이터베이스 → 스키마 → 테이블**이고, 접속 주체는 **롤**입니다.
- 앱마다 데이터베이스와 계정을 분리해 서로의 데이터를 건드리지 못하게 합니다.
