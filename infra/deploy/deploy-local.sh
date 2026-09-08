#!/usr/bin/env bash
# =====================================================================
# 서버 안에서 도는 배포 (GitHub Actions 자체 호스팅 러너용)
#
#   ./deploy-local.sh api|web|all
#
# deploy.sh 와 하는 일은 같고 <b>거리가 다르다.</b> deploy.sh 는 개발 PC 에서
# ssh·scp 로 서버를 조작하고, 이것은 서버 위에서 자기 자신에게 설치한다.
#
# 왜 나눴는가 — 자체 호스팅 러너를 쓰는 이유가 <b>SSH 를 아예 안 쓰는 것</b>인데,
# deploy.sh 에 "로컬이면 ssh 를 건너뛴다" 같은 분기를 넣으면 그 분기가 실제로
# 검증되지 않는다. 두 파일이 각자 한 가지 일만 한다.
#
# 이 스크립트는 hanseongge 계정으로 돈다. sudo 는 provision.sh 가 허용한
# systemctl restart 두 개만 쓴다.
# =====================================================================
set -euo pipefail

TARGET="${1:-all}"
STAMP="$(date +%Y%m%d-%H%M%S)"
KEEP=3

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# 기동을 확인하는 방식이 deploy.sh 와 같다. 고정 대기(sleep)로는 안 된다 —
# Spring Boot 기동에 8~10초가 걸려 짧게 자면 매 배포가 실패로 보이고, 길게 자면
# 진짜 실패했을 때 그만큼을 통째로 버린다.
wait_healthy() {
  local url="$1" name="$2"
  for i in $(seq 1 60); do
    if curl -fsS "$url" > /dev/null 2>&1; then
      echo "  기동 확인 (${i}초)"
      return 0
    fi
    sleep 1
  done
  echo "  60초 안에 기동하지 못했습니다 — journalctl -u $name -n 50" >&2
  return 1
}

prune() {
  ls -1dt "$1"/*/ | tail -n +$((KEEP + 1)) | xargs -r rm -rf
}

deploy_api() {
  echo "==> 백엔드 빌드"
  (cd "$ROOT/backend" && ./gradlew clean build -x test --no-daemon)

  local jar
  jar="$(ls "$ROOT"/backend/build/libs/*.jar | grep -v plain | head -n1)"
  echo "==> 산출물: $jar"

  mkdir -p "/opt/hanseongge/api/releases/$STAMP"
  cp "$jar" "/opt/hanseongge/api/releases/$STAMP/app.jar"

  ln -sfn "/opt/hanseongge/api/releases/$STAMP" /opt/hanseongge/api/current
  sudo systemctl restart hanseongge-api
  wait_healthy http://127.0.0.1:8081/actuator/health hanseongge-api
  prune /opt/hanseongge/api/releases

  echo "==> 백엔드 배포 완료: $STAMP"
}

deploy_web() {
  # NEXT_PUBLIC_* 는 런타임이 아니라 빌드 시점에 번들에 박힌다. 이 파일이 없으면
  # 기본값(localhost:8080)으로 빌드되어, 배포는 성공했는데 운영 사이트가 아무
  # 데이터도 못 불러오는 상태가 된다.
  #
  # 러너에서는 워크플로가 이 파일을 먼저 쓴다 (GitHub Variables 에서 가져온다).
  if [ ! -f "$ROOT/frontend/.env.production" ]; then
    echo "오류: frontend/.env.production 이 없습니다." >&2
    echo "      워크플로의 '운영 빌드 값 준비' 단계가 실패했는지 확인하세요." >&2
    exit 1
  fi

  echo "==> 프론트엔드 빌드"
  (cd "$ROOT/frontend" && npm ci && npm run build)

  # 산출물을 직접 열어 본다. .env.production 이 있다는 것만으로는 부족했다 —
  # Next 의 우선순위에서 .env.local 이 .env.production 을 이겨, 파일은 제자리에
  # 있는데 값은 개발용이 박히는 일이 실제로 있었다.
  echo "==> 번들 검사 (개발용 주소가 박혔는지)"
  local leaked
  leaked="$(grep -rl 'localhost:8080\|localhost:3000' \
              "$ROOT/frontend/.next/static" \
              "$ROOT/frontend/.next/standalone/.next" 2>/dev/null || true)"
  if [ -n "$leaked" ]; then
    echo "오류: 운영 번들에 개발용 주소가 박혀 있습니다." >&2
    echo "$leaked" | sed 's|^|      |' >&2
    exit 1
  fi

  # "없어야 할 것"만 확인하면 부족하다. 실제로 겪었다 (2026-09-08):
  # GitHub Variable 의 값이 실수로 <b>변수 이름 그 자체</b>였고, 번들에
  #
  #     new URL(path, "NEXT_PUBLIC_API_BASE_URL")
  #
  # 가 박혔다. localhost 가 아니므로 위 검사를 그대로 통과했고, 배포는 성공했으며,
  # 브라우저에서만 TypeError: Invalid URL 로 죽었다. 로그인 화면에는 "잠시 후 다시
  # 시도해 주세요"만 뜨고 서버 로그에는 아무것도 남지 않는다 — 요청이 브라우저를
  # 떠나지 못하기 때문이다.
  #
  # 그래서 <b>있어야 할 것이 있는지</b>도 본다. .env.production 에 적힌 그 값이
  # 번들 안에 실제로 들어갔는지 확인한다.
  echo "==> 번들 검사 (API 주소가 제대로 박혔는지)"
  local api_url
  api_url="$(grep -m1 '^NEXT_PUBLIC_API_BASE_URL=' "$ROOT/frontend/.env.production" | cut -d= -f2-)"

  case "$api_url" in
    https://*|http://*) ;;
    *)
      echo "오류: NEXT_PUBLIC_API_BASE_URL 이 http(s):// 로 시작하지 않습니다." >&2
      echo "      값: '${api_url}'" >&2
      echo "      GitHub > Settings > Secrets and variables > Actions > Variables 를 확인하세요." >&2
      exit 1
      ;;
  esac

  if ! grep -rqF "$api_url" \
        "$ROOT/frontend/.next/static" \
        "$ROOT/frontend/.next/standalone/.next" 2>/dev/null; then
    echo "오류: 번들에서 API 주소를 찾지 못했습니다: $api_url" >&2
    echo "      값이 빌드에 주입되지 않았습니다. 배포해도 브라우저가 API 를 못 부릅니다." >&2
    exit 1
  fi
  echo "  깨끗함 (API 주소: $api_url)"

  local dest="/opt/hanseongge/web/releases/$STAMP"
  mkdir -p "$dest/.next"
  cp -r "$ROOT/frontend/.next/standalone/." "$dest/"
  cp -r "$ROOT/frontend/.next/static" "$dest/.next/static"
  [ -d "$ROOT/frontend/public" ] && cp -r "$ROOT/frontend/public" "$dest/public"

  ln -sfn "$dest" /opt/hanseongge/web/current
  sudo systemctl restart hanseongge-web
  wait_healthy http://127.0.0.1:3000/ hanseongge-web
  prune /opt/hanseongge/web/releases

  echo "==> 프론트엔드 배포 완료: $STAMP"
}

case "$TARGET" in
  api) deploy_api ;;
  web) deploy_web ;;
  all) deploy_api; deploy_web ;;
  *)   echo "사용법: $0 [api|web|all]" >&2; exit 1 ;;
esac
