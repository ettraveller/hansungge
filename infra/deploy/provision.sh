#!/usr/bin/env bash
# =====================================================================
# 서버 최초 1회 세팅 (Ubuntu 22.04 / 24.04 LTS, NCP Server)
#
#   서버에서 root 로 실행한다:
#     sudo bash provision.sh
#
# deploy.sh 는 서버가 이미 준비되어 있다고 전제한다 — 사용자·디렉터리·JDK·Node·
# systemd 유닛·sudoers 가 없으면 첫 배포가 중간에 멈춘다. 이 스크립트가 그 전제를
# 만든다.
#
# 여러 번 실행해도 안전하다(멱등). 이미 있는 것은 건너뛴다.
#
# 2026-09-23 부터 **DB 도 이 서버에 있다.** NCP Cloud DB(HA) 를 없애고 PostgreSQL 을
# 직접 설치했다 — 대기 서버 몫이 월 요금의 3분의 2였고, 이 트래픽에 HA 가 필요 없었다.
#
# 이 스크립트가 하지 않는 것
#   · 비밀값 주입      /etc/hanseongge/*.env 는 템플릿만 만든다. 값은 사람이 채운다
#   · DB 비밀번호 설정  역할(role)만 만든다. 비밀번호는 사람이 정해 api.env 와 맞춘다
#   · SSL 인증서 발급   DNS 가 서버를 가리킨 뒤에 해야 한다 (아래 안내 참조)
#   · 애플리케이션 배포  deploy.sh 가 한다
# =====================================================================
set -euo pipefail

APP_USER="hanseongge"
APP_GROUP="hanseongge"
JDK_VERSION="21"
PG_VERSION="16"
NODE_MAJOR="20"
DOMAIN="${DOMAIN:-hanseongge.com}"

if [ "$(id -u)" -ne 0 ]; then
  echo "root 로 실행해야 합니다: sudo bash $0" >&2
  exit 1
fi

log() { echo -e "\n\033[1m==> $*\033[0m"; }

# ---------------------------------------------------------------------
# 1. 기본 패키지
# ---------------------------------------------------------------------
log "패키지 갱신"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq ca-certificates curl gnupg lsb-release ufw

# ---------------------------------------------------------------------
# 2. 시간대 — 서버와 DB 가 같아야 한다
#
# 어긋나면 시세 "노후" 판정(PRICE_STALE_MINUTES)이 틀어져 멀쩡한 시세가
# "시세문의"로 바뀐다. 화면만 보고는 원인을 찾기 어렵다.
#
# DB 가 같은 서버로 들어온 지금도 각각 정한다 — PostgreSQL 은 OS 시간대를 그대로
# 따르지 않아, 6절에서 timezone 을 따로 박아 둔다.
# ---------------------------------------------------------------------
log "시간대 Asia/Seoul"
timedatectl set-timezone Asia/Seoul

# ---------------------------------------------------------------------
# 3. JDK 21
#
# Ubuntu 22.04 의 기본 저장소에 openjdk-21-jre-headless 가 있다. 없으면
# Temurin 저장소를 붙인다.
# ---------------------------------------------------------------------
log "JDK ${JDK_VERSION}"
if ! java -version 2>&1 | grep -q "\"${JDK_VERSION}\."; then
  if apt-cache show "openjdk-${JDK_VERSION}-jre-headless" >/dev/null 2>&1; then
    apt-get install -y -qq "openjdk-${JDK_VERSION}-jre-headless"
  else
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public \
      | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
    echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" \
      > /etc/apt/sources.list.d/adoptium.list
    apt-get update -qq
    apt-get install -y -qq "temurin-${JDK_VERSION}-jre"
  fi
fi
java -version

# systemd 유닛이 /usr/bin/java 를 절대경로로 부른다
if [ ! -x /usr/bin/java ]; then
  ln -sf "$(command -v java)" /usr/bin/java
fi

# ---------------------------------------------------------------------
# 4. Node.js
#
# Next.js standalone 산출물을 /usr/bin/node 로 실행한다. 빌드는 로컬에서 하므로
# 서버에는 npm 이 필요 없지만, 같이 딸려 온다.
# ---------------------------------------------------------------------
log "Node.js ${NODE_MAJOR}"
if ! node -v 2>/dev/null | grep -qE "^v(${NODE_MAJOR}|2[0-9])\."; then
  curl -fsSL "https://deb.nodesource.com/setup_${NODE_MAJOR}.x" | bash -
  apt-get install -y -qq nodejs
fi
node -v

if [ ! -x /usr/bin/node ]; then
  ln -sf "$(command -v node)" /usr/bin/node
fi

# ---------------------------------------------------------------------
# 5. 스왑 2GB
#
# 8GB 서버에 JVM 과 PostgreSQL 이 같이 산다. 스왑이 없으면 순간적으로 메모리가
# 몰릴 때 OOM killer 가 둘 중 하나를 죽인다 — 그리고 그것이 보통 DB 다.
# 스왑은 느려서 쓰이면 안 되는 물건이지만, 없으면 프로세스가 사라진다.
# ---------------------------------------------------------------------
log "스왑 2GB"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
fi
if ! grep -q '^/swapfile' /etc/fstab; then
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
free -h | head -3

# ---------------------------------------------------------------------
# 6. PostgreSQL ${PG_VERSION} (이 서버에 직접)
#
# 2026-09-23 이전에는 NCP Cloud DB for PostgreSQL(HA) 였다. 월 102,500원으로
# 전체 요금의 66% 였는데, 그 대부분이 쓰이지 않는 대기 서버(Secondary) 값이었다.
#
# ⚠️ 외부에 열지 않는다. listen_addresses 를 localhost 로 묶고, ACG·ufw 에도
#    5432 를 넣지 않는다 — Private Subnet 에 있던 때와 노출 면이 같다.
#    설계서의 "DB 외부 비노출"은 그대로 지켜진다.
#
# 우분투 기본 저장소 대신 PGDG 를 쓴다. 기본 저장소는 배포판 수명 동안 한 버전에
# 묶여 있어, 마이너 보안 패치가 늦거나 끊긴다.
# ---------------------------------------------------------------------
log "PostgreSQL ${PG_VERSION}"
if ! [ -d "/etc/postgresql/${PG_VERSION}" ]; then
  install -d -m 0755 /usr/share/postgresql-common/pgdg
  curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc \
    -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc
  echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" \
    > /etc/apt/sources.list.d/pgdg.list
  apt-get update -qq
  apt-get install -y -qq "postgresql-${PG_VERSION}"
fi

# 설정은 별도 파일로 둔다. postgresql.conf 를 직접 고치면 패키지 업그레이드 때
# 충돌하고, 무엇을 우리가 바꿨는지 알 수 없게 된다.
#
# shared_buffers 1GB / effective_cache_size 3GB 는 8GB 서버에서 JVM(최대 3GB)과
# 나눠 쓰는 값이다. 서버 사양을 바꾸면 이 값과 유닛의 MaxRAMPercentage 를 같이 본다.
PG_CONF="/etc/postgresql/${PG_VERSION}/main/conf.d/hanseongge.conf"
cat > "${PG_CONF}.new" <<'PGCONF'
listen_addresses = 'localhost'
timezone = 'Asia/Seoul'
shared_buffers = 1GB
effective_cache_size = 3GB
work_mem = 8MB
maintenance_work_mem = 256MB
PGCONF
systemctl enable postgresql >/dev/null

# 내용이 같으면 재시작하지 않는다. 이 스크립트는 여러 번 돌려도 안전해야 하는데,
# 무조건 restart 하면 이미 돌고 있는 서비스의 DB 연결이 그때마다 끊긴다.
if cmp -s "${PG_CONF}.new" "$PG_CONF"; then
  rm -f "${PG_CONF}.new"
  systemctl start postgresql
else
  mv "${PG_CONF}.new" "$PG_CONF"
  systemctl restart postgresql
  echo "  설정이 바뀌어 PostgreSQL 을 재시작했다"
fi

# 역할과 DB. 비밀번호는 넣지 않는다 — 비밀값은 사람 몫이다(아래 안내 2번).
#
# ⚠️ OWNER 를 반드시 준다. PostgreSQL 15 부터 public 스키마에 일반 사용자가
#    테이블을 만들 수 없어, 소유자가 아니면 Flyway 가 첫 마이그레이션에서 멈춘다.
if ! runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='${APP_USER}'" | grep -q 1; then
  runuser -u postgres -- psql -qc "CREATE ROLE ${APP_USER} LOGIN"
fi
if ! runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_database WHERE datname='${APP_USER}'" | grep -q 1; then
  runuser -u postgres -- createdb -O "${APP_USER}" "${APP_USER}"
fi

# ---------------------------------------------------------------------
# 7. DB 백업 (매일 04:00, 14일 보관)
#
# ⚠️ 이 백업은 **같은 디스크에 있다.** 실수로 지운 데이터는 되살리지만, 서버나
#    디스크가 통째로 죽으면 DB 와 백업을 함께 잃는다. 오픈 전에 Object Storage 로
#    복사하는 것을 반드시 붙일 것 (TODO J).
#    Cloud DB 를 쓸 때는 NCP 자동 백업이 이 일을 대신했다.
# ---------------------------------------------------------------------
log "DB 백업 스크립트·크론"
cat > /usr/local/bin/pg-backup.sh <<'BKEOF'
#!/bin/bash
set -euo pipefail
DIR=/var/backups/pg
mkdir -p $DIR
chmod 700 $DIR
TS=$(date +%Y%m%d_%H%M)
runuser -u postgres -- pg_dump -Fc hanseongge > $DIR/hanseongge_$TS.dump
find $DIR -name '*.dump' -mtime +14 -delete
BKEOF
chmod 0700 /usr/local/bin/pg-backup.sh
install -d -o root -g root -m 0700 /var/backups/pg
echo '0 4 * * * root /usr/local/bin/pg-backup.sh' > /etc/cron.d/pg-backup
chmod 0644 /etc/cron.d/pg-backup

# ---------------------------------------------------------------------
# 8. 앱 계정
#
# 로그인 셸을 주지 않는다. 이 계정은 프로세스를 돌리기 위한 것이지 사람이 쓰는
# 계정이 아니다. 다만 deploy.sh 가 scp/ssh 로 붙으므로 SSH 키는 받는다.
# ---------------------------------------------------------------------
log "앱 계정 ${APP_USER}"
if ! id "$APP_USER" >/dev/null 2>&1; then
  useradd --system --create-home --shell /bin/bash "$APP_USER"
fi
install -d -o "$APP_USER" -g "$APP_GROUP" -m 0700 "/home/$APP_USER/.ssh"
touch "/home/$APP_USER/.ssh/authorized_keys"
chown "$APP_USER:$APP_GROUP" "/home/$APP_USER/.ssh/authorized_keys"
chmod 0600 "/home/$APP_USER/.ssh/authorized_keys"

# ---------------------------------------------------------------------
# 9. 디렉터리
# ---------------------------------------------------------------------
log "디렉터리"
# ⚠️ install -d 는 <b>중간에 만들어지는 상위 디렉터리에 소유권을 적용하지 않는다.</b>
# releases 만 지정하면 그 부모인 api/ · web/ 이 root 소유로 남고, deploy.sh 가
# 그 안에 current 심볼릭 링크를 만들지 못해 "Permission denied" 로 멈춘다 —
# 그런데 releases 로의 업로드는 성공한 뒤라, 원인이 업로드 쪽으로 보인다.
# 부모까지 명시한다.
install -d -o root -g root -m 0755 /opt/hanseongge
install -d -o "$APP_USER" -g "$APP_GROUP" -m 0755 \
  /opt/hanseongge/api \
  /opt/hanseongge/web \
  /opt/hanseongge/api/releases \
  /opt/hanseongge/web/releases

# systemd 유닛의 ProtectSystem=strict 아래에서 유일하게 쓰기 가능한 경로다
install -d -o "$APP_USER" -g "$APP_GROUP" -m 0755 /var/log/hanseongge

# certbot 의 HTTP-01 검증용
install -d -o root -g root -m 0755 /var/www/certbot

# ---------------------------------------------------------------------
# 10. 환경변수 파일 (템플릿만)
#
# 값은 사람이 채운다. 640 · root:hanseongge 라 앱은 읽고 남은 못 읽는다.
# ---------------------------------------------------------------------
log "환경변수 파일"
install -d -o root -g "$APP_GROUP" -m 0750 /etc/hanseongge

if [ ! -f /etc/hanseongge/api.env ]; then
  cat > /etc/hanseongge/api.env <<'ENVEOF'
# 백엔드 운영 환경변수. prod 프로파일은 기본값(:-)이 없어, 빠뜨리면 기동에 실패한다.
# 조용히 잘못된 값으로 뜨는 것보다 낫다.

# --- 데이터베이스 (이 서버의 PostgreSQL. 2026-09-23 이전에는 NCP Cloud DB) ---
#     DB_PASSWORD 는 여기와 DB 역할 양쪽에 같은 값을 넣어야 한다 (아래 안내 2번)
DB_URL=jdbc:postgresql://localhost:5432/hanseongge
DB_USERNAME=hanseongge
DB_PASSWORD=
DB_POOL_SIZE=20

# --- JWT (32바이트 이상 랜덤. 로컬/운영 값을 반드시 다르게) ---
#     생성: openssl rand -base64 48
JWT_SECRET=

# --- 관리자 OTP (2단계 인증) ---
#     관리자는 첫 로그인에서 휴대폰 인증 앱(Google Authenticator 등)을 등록해야 들어온다.
#     암호화 키를 비우면 JWT_SECRET 에서 파생한다 — 그때는 JWT_SECRET 을 바꾸면 OTP 를 전부 초기화해야 한다.
#     생성: openssl rand -base64 32
ADMIN_OTP_REQUIRED=true
ADMIN_OTP_ENCRYPTION_KEY=

# --- 최초 관리자 (admin_users 가 비어 있을 때만 1회 생성) ---
#     첫 로그인 후 비밀번호를 바꾸고 이 두 줄을 지운 뒤 재기동할 것.
ADMIN_INIT_ID=
ADMIN_INIT_PASSWORD=

# --- 소셜 로그인 (A-4) ---
#     값을 채운 제공자만 로그인 화면에 버튼으로 나온다. 비어 있으면 기동은 되지만
#     로그인 수단이 없다.
OAUTH_REDIRECT_BASE_URL=https://hanseongge.com/auth/callback
REFRESH_COOKIE_DOMAIN=.hanseongge.com
OAUTH_GOOGLE_CLIENT_ID=
OAUTH_GOOGLE_CLIENT_SECRET=
OAUTH_NAVER_CLIENT_ID=
OAUTH_NAVER_CLIENT_SECRET=
OAUTH_KAKAO_CLIENT_ID=
OAUTH_KAKAO_CLIENT_SECRET=

# --- 본인인증 — KG이니시스 간편인증 (TODO E-4) ---
#     비어 있으면 휴대폰(자체) 가입·비밀번호 찾기가 닫힌다. 소셜 가입·로그인은 된다. 계약 후 INICIS.
#     MOCK 은 운영(prod) 프로파일에서 기동 자체가 실패한다 — 넣지 말 것.
IDENTITY_PROVIDER=
IDENTITY_MID=
IDENTITY_API_KEY=
#     01 간편인증(PASS·카카오·네이버·토스·은행, CI 제공) — 기본값. 03 본인확인(은행·토스만)
IDENTITY_REQ_SVC_CD=01
#     CI 해시용 비밀값. 비우면 본인인증이 꺼진다. ⚠️ 한 번 정하면 바꾸지 않는다 — 바꾸면 기존 회원과
#     같은 사람 판정이 전부 끊긴다. 생성: openssl rand -base64 48
IDENTITY_HASH_PEPPER=
#     아래 두 주소는 기본값이 운영 주소다. 바꿀 때만 주석을 푼다 — 빈 값으로 두면 기본값을 덮어쓴다.
# IDENTITY_CALLBACK_URL=https://api.hanseongge.com/api/v1/auth/identity/return
# IDENTITY_RETURN_PAGE_URL=https://hanseongge.com/auth/identity

# --- 문자 발송 (TODO E-7, NCP SENS) ---
#     비어 있으면 「인증번호로 로그인」이 화면에서 숨는다. 발신번호는 사전 등록·승인이 필요하다.
SENS_ACCESS_KEY=
SENS_SECRET_KEY=
SENS_SERVICE_ID=
SENS_SENDER_PHONE=

# --- 메일 (TODO E-5) ---
#     NCP Cloud Outbound Mailer. 비어 있으면 이메일 인증번호가 나가지 않는다
#     (가입 시 이메일은 선택이라 가입 자체는 막히지 않는다).
#     ⚠️ 보내는 도메인에 SPF/DKIM/DMARC 를 설정해야 한다. 없으면 대부분 스팸함으로 간다.
APP_MAIL_ACCESS_KEY=
APP_MAIL_SECRET_KEY=
APP_MAIL_FROM_ADDRESS=no-reply@hanseongge.com

# --- 서버 ---
SERVER_PORT=8080
SERVER_ADDRESS=127.0.0.1
MANAGEMENT_PORT=8081
CORS_ALLOWED_ORIGINS=https://hanseongge.com,https://www.hanseongge.com
LOG_PATH=/var/log/hanseongge
ENVEOF
  chown root:"$APP_GROUP" /etc/hanseongge/api.env
  chmod 640 /etc/hanseongge/api.env
  echo "  생성: /etc/hanseongge/api.env — 값을 채워야 한다"
else
  echo "  이미 있음: /etc/hanseongge/api.env (건드리지 않음)"
fi

if [ ! -f /etc/hanseongge/web.env ]; then
  cat > /etc/hanseongge/web.env <<'ENVEOF'
# 프론트엔드 운영 환경변수.
#
# ⚠️ NEXT_PUBLIC_* 는 여기 적어도 소용이 없다. 빌드 시점에 번들에 박히는 값이라
#    빌드하는 쪽(deploy.sh 를 돌리는 개발 PC)의 frontend/.env.production 이 정한다.
#    이 파일은 Next 런타임이 읽는 서버 전용 값을 위한 자리다.
NODE_ENV=production
PORT=3000
HOSTNAME=127.0.0.1
ENVEOF
  chown root:"$APP_GROUP" /etc/hanseongge/web.env
  chmod 640 /etc/hanseongge/web.env
  echo "  생성: /etc/hanseongge/web.env"
else
  echo "  이미 있음: /etc/hanseongge/web.env (건드리지 않음)"
fi

# ---------------------------------------------------------------------
# 11. sudoers — deploy.sh 가 재시작할 수 있도록
#
# 이것이 없으면 배포가 비밀번호를 물으며 멈춘다. 허용 범위를 이 두 서비스의
# restart/status 로만 좁힌다.
# ---------------------------------------------------------------------
log "sudoers"
cat > /etc/sudoers.d/hanseongge <<EOF
$APP_USER ALL=(root) NOPASSWD: /bin/systemctl restart hanseongge-api, \\
                               /bin/systemctl restart hanseongge-web, \\
                               /bin/systemctl status hanseongge-api, \\
                               /bin/systemctl status hanseongge-web
EOF
chmod 0440 /etc/sudoers.d/hanseongge
visudo -cf /etc/sudoers.d/hanseongge

# ---------------------------------------------------------------------
# 12. systemd 유닛
# ---------------------------------------------------------------------
log "systemd 유닛"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UNIT_SRC="$SCRIPT_DIR/../systemd"
if [ -d "$UNIT_SRC" ]; then
  install -m 0644 "$UNIT_SRC/hanseongge-api.service" /etc/systemd/system/
  install -m 0644 "$UNIT_SRC/hanseongge-web.service" /etc/systemd/system/
  systemctl daemon-reload
  # enable 만 한다. 아직 배포된 산출물이 없어 start 하면 실패한다.
  systemctl enable hanseongge-api hanseongge-web >/dev/null
  echo "  등록 완료 (enable). 첫 배포 후 자동으로 뜬다"
else
  echo "  경고: $UNIT_SRC 를 찾지 못했다. 저장소를 통째로 올린 뒤 다시 실행할 것" >&2
fi

# ---------------------------------------------------------------------
# 13. Nginx
# ---------------------------------------------------------------------
log "Nginx"
apt-get install -y -qq nginx
NGINX_SRC="$SCRIPT_DIR/../nginx/hanseongge.conf"
if [ -f "$NGINX_SRC" ]; then
  install -m 0644 "$NGINX_SRC" /etc/nginx/sites-available/hanseongge.conf
  ln -sfn /etc/nginx/sites-available/hanseongge.conf /etc/nginx/sites-enabled/hanseongge.conf
  rm -f /etc/nginx/sites-enabled/default
  echo "  설정 배치 완료 — SSL 인증서 발급 전까지는 nginx -t 가 실패한다 (정상)"
fi

# ---------------------------------------------------------------------
# 14. certbot
# ---------------------------------------------------------------------
log "certbot"
apt-get install -y -qq certbot python3-certbot-nginx

# ---------------------------------------------------------------------
# 15. 방화벽
#
# DB(5432)는 열지 않는다 — PostgreSQL 이 이 서버 안에 있고 localhost 에만 바인딩되어
# 있어 네트워크를 타지 않는다. NCP ACG 에도 5432 를 넣지 않는다.
#
# (2026-09-23 이전: DB 가 Private Subnet 의 Cloud DB 였고 ACG 로 앱 서버만 허용했다.
#  그때도 인바운드 규칙은 필요 없었다 — 나가는 연결이었기 때문이다.)
# ---------------------------------------------------------------------
log "방화벽 (ufw)"
ufw allow OpenSSH >/dev/null
ufw allow 'Nginx Full' >/dev/null
ufw --force enable >/dev/null
ufw status numbered

# ---------------------------------------------------------------------
# 끝 — 사람이 해야 할 것
# ---------------------------------------------------------------------
cat <<NEXT

$(log "서버 준비 완료. 남은 것은 사람이 해야 한다")

1. 배포용 SSH 공개키를 넣는다 (개발 PC 에서)
     ssh-copy-id -i ~/.ssh/id_ed25519.pub $APP_USER@<서버IP>
   또는 서버에서 직접:
     vi /home/$APP_USER/.ssh/authorized_keys

2. DB 비밀번호를 정하고, **양쪽에 같은 값**을 넣는다
     sudo -u postgres psql -c "ALTER ROLE $APP_USER PASSWORD '<정한 값>'"
     vi /etc/hanseongge/api.env      # DB_PASSWORD 에 같은 값

   나머지 빈 값도 채운다 — JWT_SECRET · ADMIN_INIT_* 는 반드시 필요하다.
   소셜 로그인 값은 나중에 채워도 기동은 된다.

   ⚠️ 옮겨 온 서버라면 **덤프를 먼저 넣는다.** 빈 DB 로 앱을 띄우면 Flyway 가
      스키마만 새로 만들어 버리고, 데이터가 없다는 것을 한참 뒤에 안다.
        pg_restore --no-owner --role=$APP_USER -h localhost -U $APP_USER \\
          -d $APP_USER <덤프파일>

3. DNS 가 이 서버를 가리키는지 확인한 뒤 SSL 을 발급한다
     dig +short $DOMAIN            # 서버 IP 가 나와야 한다
     dig +short api.$DOMAIN

     certbot certonly --webroot -w /var/www/certbot \\
       -d $DOMAIN -d www.$DOMAIN
     certbot certonly --webroot -w /var/www/certbot \\
       -d api.$DOMAIN

   ⚠️ 인증서가 둘로 나뉜다. nginx 설정이 www 와 api 에 서로 다른 인증서 경로를
      쓰기 때문이다. 한 장으로 묶으면 경로가 맞지 않아 nginx 가 뜨지 않는다.

     nginx -t && systemctl reload nginx

4. 개발 PC 에서 배포한다
     export REMOTE_HOST=<서버IP>
     ./infra/deploy/deploy.sh all

NEXT
