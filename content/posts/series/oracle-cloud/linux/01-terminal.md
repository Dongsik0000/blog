---
title: "터미널과 기본 명령어"
slug: "oracle-cloud-linux-terminal"
category: "development"
publishedAt: "2026-10-07"
summary: "서버에는 마우스가 없습니다. 경로, 파일 보기, 편집, 검색, 파이프처럼 서버 작업에 매일 쓰는 명령어를 실제 서버 파일로 익힙니다."
series: "oracle-cloud"
chapter: "linux"
seriesOrder: 1
---
## 이 글에서 할 일

SSH로 접속한 서버에는 화면(GUI)이 없고, 글자로 명령을 입력하는 **터미널**만 있습니다. 이 글에서는 이 시리즈 전체에서 반복해서 쓰는 명령어를 정리합니다. 외울 필요는 없고, 필요할 때 돌아와서 찾아보면 됩니다.

## 프롬프트 읽기

접속하면 다음과 같은 줄이 보입니다.

```text
ubuntu@web-01:~$
```

| 부분 | 뜻 |
|---|---|
| `ubuntu` | 지금 로그인한 사용자 |
| `web-01` | 서버 이름(호스트 이름) |
| `~` | 지금 있는 폴더. `~`는 내 홈 폴더(`/home/ubuntu`)입니다 |
| `$` | 일반 사용자라는 표시. root면 `#`으로 바뀝니다 |

## 경로와 이동

리눅스의 모든 파일은 `/`(루트)에서 시작하는 나무 구조에 있습니다. Windows의 `C:\`와 비슷합니다.

이 시리즈에서 자주 가는 곳은 다음과 같습니다.

| 경로 | 무엇이 있나 |
|---|---|
| `/srv/` | 웹 서비스 파일과 앱 소스 코드를 두는 곳 (관례) |
| `/etc/nginx/` | nginx 설정 |
| `/etc/postgresql/16/main/` | PostgreSQL 설정 |
| `/var/log/` | 각종 로그 |

```bash
pwd                      # 지금 위치 출력 (print working directory)
cd /etc/nginx            # 폴더 이동 (change directory)
ls                       # 목록 보기
ls -la                   # 숨김 파일 포함, 자세히 보기
cd ..                    # 한 단계 위로
cd ~                     # 홈 폴더로
cd -                     # 바로 전 폴더로
```

- **절대 경로**: `/`로 시작합니다. 어디서 실행해도 같은 곳을 가리킵니다. 예: `/etc/nginx/nginx.conf`
- **상대 경로**: 지금 위치 기준입니다. 예: `deploy/deploy.sh`

## 파일 내용 보기

```bash
cat /etc/os-release                           # 전체 출력 (짧은 파일)
less /etc/nginx/nginx.conf                    # 한 화면씩 보기. q로 종료, /검색어로 검색
head -20 파일                                  # 앞 20줄
tail -20 파일                                  # 뒤 20줄
tail -f /var/log/nginx/access.log             # 새로 추가되는 줄을 계속 보기. Ctrl+C로 종료
```

`tail -f`는 로그를 실시간으로 볼 때 가장 많이 씁니다.

## 파일 편집: nano

서버에서 설정 파일을 고칠 때는 터미널 편집기를 씁니다. 처음에는 **nano**가 가장 쉽습니다.

```bash
sudo nano /etc/nginx/sites-available/example.com
```

| 키 | 동작 |
|---|---|
| 화살표 | 이동 |
| `Ctrl+O` → Enter | 저장 |
| `Ctrl+X` | 종료 |
| `Ctrl+W` | 검색 |

설정 파일을 고치기 전에는 **항상 복사본을 먼저 만듭니다.**

```bash
sudo cp /etc/nginx/sites-available/example.com ~/example.com.bak-$(date +%F)
```

`$(date +%F)`는 오늘 날짜(예: `2026-10-07`)로 바뀝니다. 잘못 고쳤을 때 이 파일로 되돌릴 수 있습니다.

## 검색: grep

파일 안에서 원하는 줄만 찾습니다.

```bash
grep proxy_pass /etc/nginx/sites-available/*     # 여러 파일에서 찾기
grep -n listen /etc/nginx/sites-available/example.com   # 줄 번호와 함께
grep -r "8080" /srv/myapp                          # 폴더 전체에서 찾기
```

## 파이프: 명령을 이어 붙이기

`|`(파이프)는 앞 명령의 출력을 뒤 명령의 입력으로 넘깁니다. 작은 도구들을 조합해 원하는 결과를 만듭니다.

```bash
ls /etc/nginx | grep conf                   # 목록 중 conf가 들어간 것만
grep -vE '^\s*#|^\s*$' /etc/nginx/nginx.conf   # 주석과 빈 줄을 뺀 실제 설정만
```

두 번째 명령은 설정 파일을 읽을 때 매우 유용합니다. `-v`는 "이 패턴이 **아닌** 줄", `^\s*#`는 "공백 뒤에 #으로 시작하는 줄(주석)", `^\s*$`는 "빈 줄"입니다.

## 명령 결과를 파일로: 리다이렉션

```bash
echo "hello" > a.txt      # 덮어쓰기
echo "world" >> a.txt     # 이어 쓰기
```

root만 쓸 수 있는 파일에는 `>`가 `sudo`와 함께 동작하지 않습니다. 이때는 `tee`를 씁니다.

```bash
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 도움말 보기

```bash
man ls          # 명령 설명서. q로 종료
ls --help       # 짧은 도움말
```

## 정리

| 하고 싶은 일 | 명령 |
|---|---|
| 위치 확인, 이동, 목록 | `pwd`, `cd`, `ls -la` |
| 파일 보기 | `cat`, `less`, `head`, `tail -f` |
| 파일 고치기 | `sudo nano`, 고치기 전 `cp`로 백업 |
| 찾기 | `grep -n`, `grep -r` |
| 명령 잇기 | `\|` (파이프) |
| root 파일에 쓰기 | `\| sudo tee -a` |
