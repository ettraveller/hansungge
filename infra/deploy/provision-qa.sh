#!/usr/bin/env bash
# =====================================================================
# QA 자리 만들기 — 한 번만 돌린다 (root)
#
#   sudo bash infra/deploy/provision-qa.sh
#
# 만드는 것:
#   DB      hanseongge_qa            운영 DB 를 통째로 복사
#   API     127.0.0.1:8180           hanseongge-api-qa.service
#   웹      127.0.0.1:3100           hanseongge-web-qa.service
#   주소    https://hanseongge.com/qa
#
# 운영은 건드리지 않는다. 포트도 DB 도 설정 파일도 전부 다른 것을 쓴다.
#
# 다시 돌려도 된다. DB 는 이미 있으면 그대로 두고(지우지 않는다), 나머지는
# 덮어쓴다. DB 를 운영 최신으로 다시 맞추려면 --refresh-db 를 붙인다.
# =====================================================================
set -euo pipefail

REFRESH_DB=false
[ "${1:-}" = "--refresh-db" ] && REFRESH_DB=true

if [ "$(id -u)" -ne 0 ]; then
  echo "root 로 실행해야 한다: sudo bash $0" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROD_ENV=/etc/hanseongge/api.env
QA_ENV=/etc/hanseongge/api-qa.env
QA_WEB_ENV=/etc/hanseongge/web-qa.env

[ -f "$PROD_ENV" ] || { echo "$PROD_ENV 가 없다. 운영이 먼저 깔려 있어야 한다." >&2; exit 1; }

# ---------------------------------------------------------------------
# 1. DB — 운영 복사본
#
# 복사본을 쓰는 이유: 빈 DB 면 상품도 사진도 없어서 진열 순서를 시험할 수가
# 없다. 운영 DB 를 그대로 쓰면 QA 에서 끌어다 놓은 순서가 손님 화면에 나간다.
# ---------------------------------------------------------------------
echo "==> DB"
EXISTS="$(su -s /bin/bash postgres -c \
  "psql -tAc \"SELECT 1 FROM pg_database WHERE datname='hanseongge_qa'\"")"

if [ "$EXISTS" = "1" ] && [ "$REFRESH_DB" = false ]; then
  echo "  hanseongge_qa 이미 있음 — 그대로 둔다 (--refresh-db 로 다시 복사)"
else
  if [ "$EXISTS" = "1" ]; then
    echo "  기존 hanseongge_qa 를 지우고 다시 만든다"
    systemctl stop hanseongge-api-qa 2>/dev/null || true
    su -s /bin/bash postgres -c "dropdb hanseongge_qa"
  fi
  # 운영 DB 의 소유자·권한을 그대로 가져가야 같은 계정으로 붙을 수 있다.
  OWNER="$(su -s /bin/bash postgres -c \
    "psql -tAc \"SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='hanseongge'\"")"
  echo "  운영 DB 복사 중 (소유자 ${OWNER})…"
  su -s /bin/bash postgres -c "createdb -O '${OWNER}' hanseongge_qa"
  su -s /bin/bash postgres -c "pg_dump hanseongge" \
    | su -s /bin/bash postgres -c "psql -q -d hanseongge_qa" > /dev/null
  echo "  복사 완료"
fi

# ---------------------------------------------------------------------
# 2. API 설정 — 운영 값을 바탕으로 하되, 밖으로 나가는 것은 전부 끊는다
#
# 끊지 않으면 QA 에서 가입 한 번 할 때마다 본인확인 요금이 나가고, 시험용
# 주문이 진짜 결제창을 연다. QA 는 안에서만 돌아야 한다.
# (결제는 끊지 않고 테스트 키로 돌린다 — 바로 아래.)
# ---------------------------------------------------------------------
echo "==> ${QA_ENV}"

# 결제 키는 운영에서 베끼지 않는다 (2026-11-19).
#
# 전에는 아래 grep 이 PAYMENT_TOSS_* 를 걸러내지 않아 운영의 라이브 키가 QA 로
# 그대로 넘어왔다. 손으로 테스트 키로 바꿔 둬도, 이 스크립트를 다시 돌리면
# 파일을 새로 쓰면서 라이브 키로 되돌아갔다 — 그 상태의 시험 주문은 진짜 결제다.
#
# 그래서 QA 에 이미 적혀 있는 값을 먼저 집어 두었다가 그대로 다시 적는다.
# 없으면 비워 둔다 (QA 에서 결제만 꺼진다).
qa_value() {
  [ -f "$QA_ENV" ] || return 0
  { grep -E "^$1=" "$QA_ENV" || true; } | tail -n 1 | cut -d= -f2-
}
QA_TOSS_CLIENT_KEY="$(qa_value PAYMENT_TOSS_CLIENT_KEY)"
QA_TOSS_SECRET_KEY="$(qa_value PAYMENT_TOSS_SECRET_KEY)"

# 라이브 키는 QA 에 두지 않는다. 값은 찍지 않는다 — 어느 칸인지만 알린다.
for name in QA_TOSS_CLIENT_KEY QA_TOSS_SECRET_KEY; do
  case "${!name}" in
    live_*)
      echo "오류: ${QA_ENV} 의 PAYMENT_${name#QA_} 가 라이브 키(live_)다." >&2
      echo "      QA 에는 테스트 키(test_)만 둔다. 그 줄을 테스트 키로 바꾸거나 지운 뒤 다시 돌린다." >&2
      exit 1
      ;;
  esac
done

grep -vE '^(PAYMENT_TOSS_CLIENT_KEY|PAYMENT_TOSS_SECRET_KEY|DB_URL|SERVER_PORT|MANAGEMENT_PORT|IDENTITY_PROVIDER|IDENTITY_MID|IDENTITY_API_KEY|IDENTITY_NOTI_URL|IDENTITY_CALLBACK_URL|IDENTITY_RETURN_PAGE_URL|UPLOAD_DIR|UPLOAD_PUBLIC_BASE_URL|CORS_ALLOWED_ORIGINS)=' \
  "$PROD_ENV" > "$QA_ENV"

cat >> "$QA_ENV" <<'EOF'

# ---- QA 전용 (provision-qa.sh 가 붙인다) ----
DB_URL=jdbc:postgresql://127.0.0.1:5432/hanseongge_qa
SERVER_PORT=8180
MANAGEMENT_PORT=8181

# 본인확인은 비워 둔다. QA 에서 자체 가입만 닫히고 로그인·소셜·관리자는 된다.
#
# MOCK 으로 두면 기동 자체가 막힌다 — 코드가 prod 프로파일에서 목 인증을 거부한다
# (누구나 아무 이름으로 가입할 수 있게 되므로). QA 도 prod 프로파일로 돈다.
# MCASH 로 두면 시험 가입 한 건마다 요금이 나간다.
IDENTITY_PROVIDER=

# 돌아오는 주소도 /qa 아래여야 한다.
IDENTITY_CALLBACK_URL=https://hanseongge.com/qa-api/api/v1/auth/identity/return
IDENTITY_RETURN_PAGE_URL=https://hanseongge.com/qa/auth/identity

# 업로드도 따로 쌓는다. 운영과 같은 곳에 두면 QA 에서 지운 사진이 운영에서 사라진다.
#
# ⚠️ 이름은 UPLOAD_DIR 이다. 한때 APP_UPLOAD_DIR 로 적어 뒀는데, 그런 이름을 읽는
# 코드가 없어서 조용히 무시되고 QA 가 <b>운영 업로드 폴더</b>를 바라봤다. 유닛의
# ReadWritePaths 가 uploads-qa 뿐이라 쓰기가 막혀 500 으로 드러났다 — 막히지
# 않았다면 QA 에서 올린 사진이 운영 폴더에 쌓였을 것이다 (2026-11-10).
UPLOAD_DIR=/var/lib/hanseongge/uploads-qa
UPLOAD_PUBLIC_BASE_URL=https://hanseongge.com/qa-api/media
EOF

# 결제 키 — 위에서 집어 둔 QA 의 값. 여기만 변수를 풀어야 해서 따로 붙인다.
{
  echo
  echo "# 결제는 테스트 키(test_)만. 운영 키를 베끼지 않는다 — 비어 있으면 QA 결제가 꺼진다."
  echo "PAYMENT_TOSS_CLIENT_KEY=${QA_TOSS_CLIENT_KEY}"
  echo "PAYMENT_TOSS_SECRET_KEY=${QA_TOSS_SECRET_KEY}"
} >> "$QA_ENV"

if [ -z "$QA_TOSS_CLIENT_KEY" ] || [ -z "$QA_TOSS_SECRET_KEY" ]; then
  echo "  ⚠️ 결제 키가 비어 있다 — QA 에서 결제가 꺼진다."
  echo "     ${QA_ENV} 의 PAYMENT_TOSS_CLIENT_KEY / PAYMENT_TOSS_SECRET_KEY 에 테스트 키(test_)를 채운다."
else
  echo "  결제 키: QA 에 있던 테스트 키를 그대로 둔다"
fi

chown root:hanseongge "$QA_ENV"
chmod 640 "$QA_ENV"
install -d -o hanseongge -g hanseongge /var/lib/hanseongge/uploads-qa

echo "==> ${QA_WEB_ENV}"
printf 'NODE_ENV=production\n' > "$QA_WEB_ENV"
chown root:hanseongge "$QA_WEB_ENV"
chmod 640 "$QA_WEB_ENV"

# ---------------------------------------------------------------------
# 3. 배포 자리
# ---------------------------------------------------------------------
# install -d 는 <b>마지막 칸에만</b> 소유자를 준다. 부모까지 적지 않으면 api-qa 가
# root 소유로 남고, 배포가 그 안에 current 링크를 만들지 못해 거기서 멈춘다.
# 빌드는 성공한 뒤라 로그만 보면 다 된 것처럼 보인다 (2026-11-09 에 겪었다).
echo "==> /opt/hanseongge/{api-qa,web-qa}"
for d in /opt/hanseongge/api-qa /opt/hanseongge/web-qa; do
  install -d -o hanseongge -g hanseongge "$d"
  install -d -o hanseongge -g hanseongge "$d/releases"
done

# ---------------------------------------------------------------------
# 4. systemd
# ---------------------------------------------------------------------
echo "==> systemd"
install -m 0644 "$SCRIPT_DIR/../systemd/hanseongge-api-qa.service" /etc/systemd/system/
install -m 0644 "$SCRIPT_DIR/../systemd/hanseongge-web-qa.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable hanseongge-api-qa hanseongge-web-qa >/dev/null

# 배포가 systemctl restart 를 비밀번호 없이 부를 수 있어야 한다.
SUDOERS=/etc/sudoers.d/hanseongge-qa
cat > "$SUDOERS" <<'EOF'
hanseongge ALL=(root) NOPASSWD: /bin/systemctl restart hanseongge-api-qa, /bin/systemctl restart hanseongge-web-qa, /bin/systemctl status hanseongge-api-qa, /bin/systemctl status hanseongge-web-qa
EOF
chmod 440 "$SUDOERS"
visudo -cf "$SUDOERS" >/dev/null

# ---------------------------------------------------------------------
# 5. nginx
# ---------------------------------------------------------------------
echo "==> nginx"
install -m 0644 "$SCRIPT_DIR/../nginx/hanseongge.conf" /etc/nginx/sites-available/hanseongge.conf
nginx -t
systemctl reload nginx

echo
echo "준비 끝. 아직 올라간 코드는 없다."
echo "qa 브랜치에 푸시하면 자동으로 배포된다 (.github/workflows/deploy-qa.yml)."
echo "주소: https://hanseongge.com/qa"
