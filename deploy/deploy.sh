#!/usr/bin/env bash
# GitHub Actions 가 SSH 로 실행하는 배포 스크립트 (서버: /srv/devgear/blog)
#   bash /srv/devgear/blog/deploy/deploy.sh
#
# 전체를 main 함수로 감싼다. git pull 이 이 파일 자체를 바꿔도 bash 는 이미 읽은 함수를 끝까지 실행한다.
set -euo pipefail

main() {
    # 같은 서버의 다른 앱(ledger 등) 배포와 빌드가 겹치지 않게 한 번에 하나씩 실행한다 (ledger 와 같은 잠금 파일)
    exec 9>/tmp/devgear-deploy.lock
    flock -w 900 9 || { echo "다른 앱 배포가 15분 안에 끝나지 않아 중단합니다" >&2; return 1; }

    cd /srv/devgear/blog
    git pull --ff-only

    # 이 앱은 서버에서 WAR 를 만들고 Dockerfile 이 그 WAR 를 복사한다. 테스트는 Actions 에서 통과한 뒤라 건너뛴다
    mvn -B -q clean package -DskipTests
    docker compose up -d --build

    # 블로그가 실제로 응답할 때까지 기다린다(최대 2분). 응답이 없으면 실패로 끝내 Actions 에 표시되게 한다
    for _ in $(seq 1 24); do
        if curl -fsS -o /dev/null --max-time 5 http://127.0.0.1:8082/blog/; then
            break
        fi
        sleep 5
    done
    curl -fsS -o /dev/null --max-time 5 http://127.0.0.1:8082/blog/ \
        || { echo "배포 후 블로그가 응답하지 않습니다: docker compose logs app 으로 확인하세요" >&2; return 1; }

    # 빌드할수록 쌓이는 이름 없는 이미지와 7일 지난 빌드 캐시를 지운다. 실패해도 배포는 끝났으므로 알리기만 한다
    docker image prune -f > /dev/null || echo "이미지 정리 실패 (배포는 완료)" >&2
    docker builder prune -f --filter until=168h > /dev/null || echo "빌드 캐시 정리 실패 (배포는 완료)" >&2
    echo "배포 완료: $(git log --oneline -1)"
}

main "$@"
exit
