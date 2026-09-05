#!/usr/bin/env bash
# =====================================================================
# 배포 스크립트 (Docker 미사용 — systemd 직접 배포, 설계서 §14.3)
#
#   사용: ./deploy.sh api        # 백엔드만
#         ./deploy.sh web        # 프론트엔드만
#         ./deploy.sh all
#
# 릴리스는 타임스탬프 디렉터리에 풀고 symlink 를 바꾼다.
# 롤백은 symlink 를 이전 릴리스로 되돌리면 끝난다 (rollback.sh).
# =====================================================================
set -euo pipefail

TARGET="${1:-all}"
REMOTE_USER="${REMOTE_USER:-hanseongge}"
REMOTE_HOST="${REMOTE_HOST:?REMOTE_HOST 환경변수를 지정하세요}"
STAMP="$(date +%Y%m%d-%H%M%S)"
KEEP=3

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

deploy_api() {
  echo "==> 백엔드 빌드"
  (cd "$ROOT/backend" && ./gradlew clean build -x test)

  local jar
  jar="$(ls "$ROOT"/backend/build/libs/*.jar | grep -v plain | head -n1)"
  echo "==> 산출물: $jar"

  ssh "$REMOTE_USER@$REMOTE_HOST" "mkdir -p /opt/hanseongge/api/releases/$STAMP"
  scp "$jar" "$REMOTE_USER@$REMOTE_HOST:/opt/hanseongge/api/releases/$STAMP/app.jar"

  ssh "$REMOTE_USER@$REMOTE_HOST" bash -s <<EOF
set -euo pipefail
ln -sfn /opt/hanseongge/api/releases/$STAMP /opt/hanseongge/api/current
sudo systemctl restart hanseongge-api
sleep 5
# 기동 확인 — 실패하면 즉시 알 수 있게 종료 코드를 전파한다
curl -fsS http://127.0.0.1:8081/actuator/health > /dev/null
ls -1dt /opt/hanseongge/api/releases/*/ | tail -n +$((KEEP + 1)) | xargs -r rm -rf
EOF
  echo "==> 백엔드 배포 완료: $STAMP"
}

deploy_web() {
  # NEXT_PUBLIC_* 는 런타임이 아니라 빌드 시점에 번들에 박힌다. 서버의 web.env 로는
  # 못 바꾼다. 이 파일이 없으면 기본값(localhost:8080)으로 빌드되어, 배포는 성공했는데
  # 운영 사이트가 아무 데이터도 못 불러오는 상태가 된다 — 화면은 뜨는데 전부 비어 있어
  # 원인을 찾는 데 시간이 걸린다. 그래서 여기서 멈춘다.
  if [ ! -f "$ROOT/frontend/.env.production" ]; then
    echo "오류: frontend/.env.production 이 없습니다." >&2
    echo "      cp frontend/.env.production.example frontend/.env.production 후 값을 채우세요." >&2
    echo "      (NEXT_PUBLIC_* 는 빌드 시점에 박히므로 서버 환경변수로는 바꿀 수 없습니다)" >&2
    exit 1
  fi

  echo "==> 프론트엔드 빌드"
  (cd "$ROOT/frontend" && npm ci && npm run build)

  local staging="$ROOT/frontend/.deploy"
  rm -rf "$staging" && mkdir -p "$staging"
  cp -r "$ROOT/frontend/.next/standalone/." "$staging/"
  mkdir -p "$staging/.next"
  cp -r "$ROOT/frontend/.next/static" "$staging/.next/static"
  [ -d "$ROOT/frontend/public" ] && cp -r "$ROOT/frontend/public" "$staging/public"

  ssh "$REMOTE_USER@$REMOTE_HOST" "mkdir -p /opt/hanseongge/web/releases/$STAMP"
  scp -r "$staging/." "$REMOTE_USER@$REMOTE_HOST:/opt/hanseongge/web/releases/$STAMP/"

  ssh "$REMOTE_USER@$REMOTE_HOST" bash -s <<EOF
set -euo pipefail
ln -sfn /opt/hanseongge/web/releases/$STAMP /opt/hanseongge/web/current
sudo systemctl restart hanseongge-web
sleep 5
curl -fsS http://127.0.0.1:3000/ > /dev/null
ls -1dt /opt/hanseongge/web/releases/*/ | tail -n +$((KEEP + 1)) | xargs -r rm -rf
EOF
  echo "==> 프론트엔드 배포 완료: $STAMP"
}

case "$TARGET" in
  api) deploy_api ;;
  web) deploy_web ;;
  all) deploy_api; deploy_web ;;
  *)   echo "사용법: $0 [api|web|all]" >&2; exit 1 ;;
esac
