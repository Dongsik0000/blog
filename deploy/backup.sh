#!/usr/bin/env bash
# 매일 블로그 DB(pg_dump)와 업로드 이미지(tar) 백업. 30일 보관.
#   sudo install -m 750 deploy/backup.sh /usr/local/bin/blog-backup
#   매일 04:20 실행은 deploy/blog-backup.service·timer (ledger 백업 04:10 과 겹치지 않게)
#
# 글은 처음 한 번만 md 에서 가져오고 이후 수정은 DB 에만 있으므로 DB 백업이 원본이다.
# 업로드 이미지는 git 에 없으므로 함께 묶는다.
set -euo pipefail
umask 077   # 덤프에 댓글 비밀번호 해시가 있으므로 root 만 읽게

BACKUP_DIR=/var/backups/blog
mkdir -p "$BACKUP_DIR"

# 시각까지 넣어 같은 날 다시 실행해도 기존 백업을 덮어쓰지 않는다.
# 임시 파일에 다 쓴 뒤 둘 다 성공했을 때만 이름을 바꾸므로 남은 파일은 끝까지 성공한 백업뿐이다
STAMP=$(date +%F-%H%M%S)
DB_FILE="$BACKUP_DIR/blog-$STAMP.dump"
UPLOADS_FILE="$BACKUP_DIR/blog-uploads-$STAMP.tar.gz"
trap 'rm -f "$DB_FILE.tmp" "$UPLOADS_FILE.tmp"' EXIT

sudo -u postgres pg_dump -Fc blog > "$DB_FILE.tmp"
# ponytail: 업로드 폴더 전체를 매일 묶는다. 수백 MB 로 커지면 보관 일수를 줄이거나 rsync 증분으로 바꾼다
tar -czf "$UPLOADS_FILE.tmp" -C /var/lib/blog uploads
mv "$DB_FILE.tmp" "$DB_FILE"
mv "$UPLOADS_FILE.tmp" "$UPLOADS_FILE"
find "$BACKUP_DIR" \( -name 'blog-*.dump' -o -name 'blog-uploads-*.tar.gz' \) -mtime +30 -delete
