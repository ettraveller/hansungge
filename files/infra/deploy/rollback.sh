#!/usr/bin/env bash
# =====================================================================
# 롤백 — 직전 릴리스로 symlink 를 되돌린다.
#   사용: ./rollback.sh api|web
# =====================================================================
set -euo pipefail

TARGET="${1:?사용법: $0 [api|web]}"
REMOTE_USER="${REMOTE_USER:-hanseongge}"
REMOTE_HOST="${REMOTE_HOST:?REMOTE_HOST 환경변수를 지정하세요}"

case "$TARGET" in
  api) BASE=/opt/hanseongge/api; SVC=hanseongge-api; CHECK="http://127.0.0.1:8081/actuator/health" ;;
  web) BASE=/opt/hanseongge/web; SVC=hanseongge-web; CHECK="http://127.0.0.1:3000/" ;;
  *)   echo "사용법: $0 [api|web]" >&2; exit 1 ;;
esac

ssh "$REMOTE_USER@$REMOTE_HOST" bash -s <<EOF
set -euo pipefail
current="\$(readlink -f $BASE/current)"
previous="\$(ls -1dt $BASE/releases/*/ | grep -v "\$current" | head -n1)"
if [ -z "\$previous" ]; then echo "롤백할 이전 릴리스가 없습니다." >&2; exit 1; fi
echo "현재: \$current"
echo "복귀: \$previous"
ln -sfn "\$previous" $BASE/current
sudo systemctl restart $SVC
sleep 5
curl -fsS $CHECK > /dev/null
echo "롤백 완료"
EOF
