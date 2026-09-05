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
# 고정 대기로는 안 된다. Spring Boot 기동에 8~10초가 걸려 sleep 5 는 매 배포를
# 실패로 만들고(앱은 멀쩡히 떠 있는데 배포만 실패한 것처럼 보인다), 넉넉히
# 30초를 자면 진짜 실패했을 때도 30초를 버린다. 뜰 때까지 1초씩 확인한다.
for i in \$(seq 1 60); do
  if curl -fsS http://127.0.0.1:8081/actuator/health > /dev/null 2>&1; then
    echo "  기동 확인 (\${i}초)"
    break
  fi
  if [ "\$i" -eq 60 ]; then
    echo "  60초 안에 기동하지 못했습니다 — journalctl -u hanseongge-api -n 50" >&2
    exit 1
  fi
  sleep 1
done
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

  # 빌드 결과를 직접 확인한다. .env.production 이 있다는 것만으로는 부족했다 —
  # Next 의 우선순위에서 .env.local 이 .env.production 을 이기기 때문에, 파일은
  # 제자리에 있는데 값은 개발용이 박히는 일이 실제로 있었다.
  #
  # 그때 증상이 고약하다. 배포는 성공하고 화면도 다 뜨는데, 브라우저만 localhost
  # 를 부르다 CORS 로 막혀 로그인 같은 클라이언트 요청만 실패한다. 서버 렌더는
  # 멀쩡하니 목록·상세는 잘 보이고, 그래서 원인이 프론트 빌드라는 데까지 가는 데
  # 오래 걸린다. 산출물을 열어 보는 것이 유일하게 확실한 검사다.
  #
  # 검사 범위는 <b>우리가 만든 청크</b>로 좁힌다. standalone/node_modules 에는 Next
  # 런타임이 내부 기본값으로 갖는 localhost 문자열이 들어 있어(clean-url.js 등),
  # 통째로 훑으면 늘 걸린다 — 늘 걸리는 검사는 곧 무시되는 검사가 된다.
  echo "==> 번들 검사 (개발용 주소가 박혔는지)"
  local leaked
  leaked="$(grep -rl 'localhost:8080\|localhost:3000' \
              "$ROOT/frontend/.next/static" \
              "$ROOT/frontend/.next/standalone/.next" 2>/dev/null || true)"
  if [ -n "$leaked" ]; then
    echo "오류: 운영 번들에 개발용 주소가 박혀 있습니다." >&2
    echo "$leaked" | sed 's|^|      |' >&2
    echo "      원인은 대개 frontend/.env.local 입니다 — Next 우선순위에서" >&2
    echo "      .env.production 을 이깁니다. .env.development.local 로 바꾸세요." >&2
    exit 1
  fi
  echo "  깨끗함"

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
# api 와 같은 이유로 고정 대기를 쓰지 않는다.
for i in \$(seq 1 60); do
  if curl -fsS http://127.0.0.1:3000/ > /dev/null 2>&1; then
    echo "  기동 확인 (\${i}초)"
    break
  fi
  if [ "\$i" -eq 60 ]; then
    echo "  60초 안에 기동하지 못했습니다 — journalctl -u hanseongge-web -n 50" >&2
    exit 1
  fi
  sleep 1
done
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
