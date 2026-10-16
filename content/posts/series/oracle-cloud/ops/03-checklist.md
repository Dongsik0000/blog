---
title: "정기 점검 체크리스트"
slug: "oracle-cloud-ops-checklist"
category: "development"
publishedAt: "2026-10-07"
summary: "서버를 오래 안정적으로 운영하기 위해 주간, 월간, 변경할 때마다 확인할 항목과 명령을 한 장으로 정리합니다."
series: "oracle-cloud"
chapter: "ops"
seriesOrder: 3
---
## 이 글에서 할 일

서버는 만들고 끝이 아닙니다. 디스크가 차고, 인증서가 만료되고, 쓰지 않는 키와 설정이 쌓입니다. 이 글은 이 시리즈에서 다룬 내용을 **점검표 한 장**으로 모읍니다. 정해진 날에 위에서부터 차례로 확인합니다.

## 매주

| 확인할 것 | 명령 | 정상 |
|---|---|---|
| 서비스 응답 | `curl -s -o /dev/null -w "%{http_code}\n" https://example.com/` (경로마다) | 각 경로의 평소 코드 ([확인 목록](/posts/oracle-cloud-nginx-apply-safely)) |
| 디스크 | `df -h /` | `Use%` 80% 미만 |
| 메모리 | `free -h` | `available`에 여유 |
| 컨테이너 상태 | `docker ps --format '{{.Names}} {{.Status}}'` | 모두 `Up ... (healthy)` |
| 메모리 초과 종료 | `docker inspect myapp --format '{{.State.OOMKilled}} {{.RestartCount}}'` | `false`, 재시작 횟수가 늘지 않음 |
| 백업 | `systemctl list-timers --no-pager` 와 `sudo ls -la /var/backups/myapp \| tail -3` | 어제 날짜 백업 파일이 있음 |
| 오류 로그 | `sudo grep ' 50[234] ' /var/log/nginx/access.log \| tail` | 갑자기 늘어난 오류가 없음 |

## 매달

| 확인할 것 | 명령 | 정상 |
|---|---|---|
| 재부팅 필요 여부 | `[ -f /var/run/reboot-required ] && echo 필요` | 출력 없음. 있으면 사용자가 적은 시간에 재부팅 |
| 인증서 | `sudo certbot certificates` | 만료까지 30일 넘게 남음 |
| 인증서 갱신 시험 | `sudo certbot renew --dry-run` | 성공 메시지 |
| 백업 복원 시험 | [복원 절차](/posts/oracle-cloud-postgresql-backup-restore)를 새 DB에 실행 | 데이터가 보임 |
| 백업 외부 복사 | 서버 밖의 백업 날짜 확인 | 최근 것이 있음 |
| Docker 디스크 | `docker system df` | 빌드 캐시가 과하게 크지 않음 |
| SSH 키 | `awk '{print $NF}' ~/.ssh/authorized_keys` | 모르는 키, 쓰지 않는 키 없음. 배포 키는 `restrict,command=` 있음 ([배포 키](/posts/oracle-cloud-deploy-deploy-key)) |
| 방화벽 | `sudo iptables -S INPUT` | 쓰지 않는 포트의 허용 규칙 없음 |
| 열린 포트 | `sudo ss -ltnp` | `0.0.0.0`이나 `*`처럼 외부 주소에서 듣는 포트가 의도한 것(22, 80, 443)뿐인지 |
| Oracle Cloud 한도 | 콘솔의 인스턴스 사양 | 무료 범위 안 ([무료 티어](/posts/oracle-cloud-account-free-tier)) |

마지막 "열린 포트" 확인이 특히 중요합니다. 앱 설정이 바뀌어 Tomcat이나 PostgreSQL이 모든 주소에서 듣기 시작하면, iptables의 허용 규칙이 남아 있을 때 외부에 노출됩니다.

## 무언가를 바꿀 때마다

| 상황 | 할 일 |
|---|---|
| nginx 설정 변경 | 백업 → `nginx -t` → reload → 모든 경로 응답 확인 ([절차](/posts/oracle-cloud-nginx-apply-safely)) |
| 데이터베이스 구조 변경 | 수동 백업을 자동 삭제 대상이 아닌 이름으로 먼저 |
| iptables 변경 | SSH 규칙, 마지막 REJECT, Oracle 규칙 확인 후 저장 ([저장](/posts/oracle-cloud-firewall-persist-rules)) |
| 앱 추가 | 포트, 메모리 상한 계획표 갱신, 데이터베이스와 계정 분리, 배포 키 따로 |
| 앱 제거 | nginx location, 배포 키, 이미지, 비밀값 파일, 데이터베이스(백업 후) 정리 |
| SSH 설정, `authorized_keys` 변경 | 기존 SSH 창을 유지한 채 새 창으로 접속 확인 |

## 점검을 기록하기

점검한 날짜와 결과를 짧게라도 남겨 두면, 문제가 생겼을 때 "언제부터 이상했는지"를 좁힐 수 있습니다. 서버 안이 아니라 저장소의 문서나 메모 앱처럼 서버를 잃어도 남는 곳에 둡니다.

## 정리

- 매주: 응답, 디스크, 메모리, 컨테이너, 백업, 오류 로그
- 매달: 재부팅, 인증서, 복원 시험, 외부 백업, SSH 키, 방화벽, 열린 포트
- 바꿀 때마다: 백업하고, 검사하고, 바꾸지 않은 것까지 확인
