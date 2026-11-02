#!/usr/bin/env bash
# =====================================================================
# 이미지 업로드 1회 적용 (2026-10-29)
#
# 코드 배포(deploy.sh)만으로는 업로드가 동작하지 않는다. 서버에 세 가지가
# 먼저 있어야 한다.
#
#   1. 사진을 쓸 디렉터리
#   2. systemd 가 그 디렉터리에 쓰기를 허용하는 설정
#   3. nginx 가 큰 요청을 통과시키는 설정
#
# 세 개 중 하나라도 빠지면 업로드만 실패한다. 화면은 정상이고 로그도
# 조용해서, 빠진 것을 찾는 데 오래 걸린다.
#
#   서버에서:  sudo bash enable-uploads.sh
#
# 여러 번 실행해도 안전하다 — 이미 되어 있으면 건드리지 않는다.
# =====================================================================
set -euo pipefail

APP_USER=hanseongge
UPLOAD_DIR=/var/lib/hanseongge/uploads
UNIT=/etc/systemd/system/hanseongge-api.service
NGINX_CONF=/etc/nginx/sites-available/hanseongge.conf

[[ $EUID -eq 0 ]] || { echo "sudo 로 실행하세요"; exit 1; }

log() { printf '\n==> %s\n' "$1"; }

# ---------------------------------------------------------------------
log "1. 업로드 디렉터리"
#
# ⚠️ /opt/hanseongge 밖이다. 배포는 releases 디렉터리를 새로 만들고 심볼릭
# 링크를 갈아끼우므로, 릴리스 안에 두면 배포 한 번에 사진이 전부 사라진다.
# ---------------------------------------------------------------------
install -d -o "$APP_USER" -g "$APP_USER" -m 0755 \
  /var/lib/hanseongge \
  "$UPLOAD_DIR"
ls -ld "$UPLOAD_DIR"

# ---------------------------------------------------------------------
log "2. systemd ReadWritePaths"
#
# 유닛에 ProtectSystem=strict 가 있어 / 전체가 읽기전용이다. 디렉터리가
# 있고 소유자가 맞아도 여기 적히지 않으면 쓰기가 거부된다.
# ---------------------------------------------------------------------
if grep -q "$UPLOAD_DIR" "$UNIT"; then
  echo "이미 적용되어 있다"
else
  cp -a "$UNIT" "$UNIT.bak-$(date +%Y%m%d-%H%M%S)"
  sed -i "s|^ReadWritePaths=/var/log/hanseongge$|ReadWritePaths=/var/log/hanseongge $UPLOAD_DIR|" "$UNIT"
  grep -n ReadWritePaths "$UNIT"
  systemctl daemon-reload
  echo "daemon-reload 완료 — 다음 restart 때 적용된다"
fi

# ---------------------------------------------------------------------
log "3. nginx"
#
# 사진 열 장(장당 10MB)이 한 요청으로 올라간다. 20m 이면 nginx 가 먼저
# 끊는데, 그때 돌아가는 413 은 우리 오류 형식이 아니라 화면이 사유를
# 보여주지 못한다.
#
# /media/ 를 따로 빼는 이유 — api_zone 은 분당 120 요청이고 상품 상세 한
# 장이 사진 열 개를 동시에 부른다. 목록에서 몇 번 들어가면 그 사람의
# 사진이 429 로 막힌다.
# ---------------------------------------------------------------------
cp -a "$NGINX_CONF" "$NGINX_CONF.bak-$(date +%Y%m%d-%H%M%S)"

# api 서버 블록의 상한만 올린다. 쇼핑몰 블록(68행 부근)은 그대로 둔다 —
# 거기로는 업로드가 가지 않는다.
if grep -q 'client_max_body_size 64m' "$NGINX_CONF"; then
  echo "상한은 이미 적용되어 있다"
else
  awk '
    /server_name api\.hanseongge\.com;/ { in_api = 1 }
    in_api && /client_max_body_size 20m;/ {
      sub(/20m/, "64m"); in_api = 0
    }
    { print }
  ' "$NGINX_CONF" > /tmp/hanseongge.conf.new
  mv /tmp/hanseongge.conf.new "$NGINX_CONF"
  grep -n client_max_body_size "$NGINX_CONF"
fi

if grep -q 'location /media/' "$NGINX_CONF"; then
  echo "/media/ 블록은 이미 있다"
else
  # Swagger 차단 블록 바로 뒤에 끼운다. api 서버 블록 안이고, location / 의
  # 포괄 규칙보다 앞이어야 한다.
  awk '
    /return 404;/ && !done {
      print; getline; print            # 차단 블록의 닫는 괄호
      print ""
      print "    # 업로드된 상품 사진. api_zone(120r/m) 밖으로 뺀다 — 상세 한 장이"
      print "    # 사진 열 개를 동시에 부르므로, 몇 번 들어가면 429 로 막힌다."
      print "    # 파일명이 UUID 라 같은 주소가 다른 그림이 되는 일이 없다 → 길게 캐시."
      print "    location /media/ {"
      print "        proxy_pass http://127.0.0.1:8080;"
      print "        include /etc/nginx/proxy_params;"
      print "        add_header Cache-Control \"public, max-age=31536000, immutable\" always;"
      print "        add_header X-Content-Type-Options \"nosniff\" always;"
      print "    }"
      done = 1
      next
    }
    { print }
  ' "$NGINX_CONF" > /tmp/hanseongge.conf.new
  mv /tmp/hanseongge.conf.new "$NGINX_CONF"
fi

# ⚠️ reload 전에 반드시 검사한다. 문법이 틀린 설정으로 reload 하면 nginx 가
# 기존 설정을 유지하지만, restart 가 걸리는 날 사이트가 통째로 내려간다.
nginx -t
systemctl reload nginx

log "완료"
cat <<'NEXT'
이제 개발 PC 에서 배포한다.

  REMOTE_HOST=49.50.129.220 ./infra/deploy/deploy.sh all

배포 후 확인:
  1. 관리자 > 상품관리 > 상품 등록 — 대표이미지 칸에 사진을 끌어다 놓는다
  2. 서버에서 ls -R /var/lib/hanseongge/uploads — products/2026/10/ 아래에 파일
  3. 상품 상세를 열어 사진이 보이는지
NEXT
