# 한성금거래소 쇼핑몰

금·은·귀금속 온라인 쇼핑몰. 시세 3층 구조(원본 / 관리자 보정 / 최종 적용)와 주문 시점 가격 Snapshot 위에 올라갑니다.

- 도메인: https://hanseongge.com
- 설계 문서: [`docs/한성금거래소_전체개발계획_v1.0.md`](docs/한성금거래소_전체개발계획_v1.0.md)
- 현재 단계: **STEP 8 완료 — 상품 · 카테고리 · 옵션 관리**

---

## 기술 스택

| 영역 | 채택 | 비고 |
|---|---|---|
| Backend | Java 21, Spring Boot 3.5.3, Gradle 9.5.1 (Kotlin DSL) | |
| | Spring Web MVC / Security / Data JPA / Validation | |
| | Flyway, PostgreSQL Driver, QueryDSL 5.1.0 (jakarta) | 스키마 소유자는 Flyway |
| | Caffeine (설정·시세 캐시), Lombok, springdoc-openapi 2.8.6 | Redis 는 트래픽 발생 후 |
| | jjwt 0.12.6 (관리자 JWT), Bucket4j 8.14 (로그인 Rate Limit), Spring AOP (감사로그) | STEP 5 |
| | Spring Scheduling (동적 cron 시세 수집), Spring Events (AFTER_COMMIT 캐시 무효화) | STEP 6 |
| | BigDecimal 값 객체 (`Money` · `PricePerGram` · `Weight`) 기반 가격 엔진 | STEP 7 |
| | QueryDSL 동적 상품 검색 | STEP 8 |
| Frontend | TypeScript 5, Next.js 16 (App Router), React 19 | SSR — 네이버/구글 SEO |
| | TanStack Query, Zustand, CSS Modules | |
| Database | PostgreSQL 16 | |
| Infra | NCP, Linux, Nginx, systemd | **Docker 미사용** |

### 프론트엔드를 React(Next.js)로 정한 이유

1. **SEO 가 매출에 직결된다.** 상품·시세 페이지가 네이버/구글에 노출되어야 하고, 그러려면 SSR 이 필수다. Next.js App Router 는 SSR/ISR 이 기본값이다.
2. **쇼핑몰과 관리자를 한 프로젝트로 운영한다.** `(shop)` 과 `admin` 을 라우트 그룹으로 나누되 빌드·배포는 하나다. 인력 1인 기준에서 프로젝트 2개는 관리 비용이 두 배다.
3. **국내 이커머스 생태계 레퍼런스가 React 쪽에 압도적으로 많다.** PG 결제창 연동, 소셜 로그인, 주소 검색(다음 우편번호), 에디터, 차트 모두 React 예제가 먼저 나온다. 8주 일정에서 이 차이는 크다.
4. **인수인계·채용 풀이 넓다.** Vue(Nuxt)도 SSR 은 되지만, 위 세 가지에서 우위가 없다.

---

## 프로젝트 구조

```
backend/                     Spring Boot API
frontend/                    Next.js (쇼핑몰 + /admin)
infra/                       nginx / systemd / 배포 스크립트
docs/                        설계서, 개발계획, 보류항목
```

`backend` 와 `frontend` 는 **형제로 최상위에 둔다.** 프론트를 백엔드 하위에 넣으면
빌드 산출물이 섞이고 배포할 때 둘을 따로 패키징할 수 없다.

자세한 계층 구조는 [`docs/한성금거래소_전체개발계획_v1.0.md`](docs/한성금거래소_전체개발계획_v1.0.md) §3 참조.

---

## 실행 방법

### 사전 준비

| 도구 | 버전 | 확인 |
|---|---|---|
| JDK | **21** | `java -version` |
| Node.js | 20 이상 | `node -v` |
| PostgreSQL | 16 | `psql --version` |

Gradle 은 설치할 필요가 없습니다 (Wrapper 포함).

JDK 21 이 `JAVA_HOME`·`PATH`·`~/.jdks` 중 어디에도 없으면 툴체인 해석이 실패합니다.
설치 위치가 특이하면 `backend/gradle.properties` 에 다음을 추가하세요.

```properties
org.gradle.java.installations.paths=C:/path/to/jdk-21
```

**테스트는 PostgreSQL 설치 없이 돌아갑니다.** 통합테스트가 임베디드 PostgreSQL 16
(zonky, 실제 바이너리 · Docker 불필요)을 띄웁니다. `bootRun` 으로 직접 띄울 때만
로컬 PostgreSQL 이 필요합니다.

### 1. 데이터베이스 준비

```sql
CREATE USER hanseongge WITH PASSWORD '<개발용 비밀번호>';
CREATE DATABASE hanseongge OWNER hanseongge;
```

**DB 를 `OWNER hanseongge` 로 만들어야 합니다.** PostgreSQL 15 부터 `public` 스키마의
CREATE 권한이 PUBLIC 에서 회수되어, `GRANT ALL PRIVILEGES ON DATABASE` 만으로는
Flyway 가 V1 에서 `permission denied for schema public` 으로 실패합니다.
소유자로 지정하면 `pg_database_owner` 를 통해 `public` 스키마를 소유하게 됩니다.

Flyway 가 기동 시 `V1~V7` 마이그레이션을 자동 적용합니다 (33테이블).

### 2. 백엔드

```bash
cd backend
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
cp .env.example .env          # DB_PASSWORD 등 채우기

# --- macOS / Linux ---
export DB_PASSWORD='<비밀번호>'
export ADMIN_INIT_ID=admin ADMIN_INIT_PASSWORD='<강한 비밀번호>'   # 최초 1회만
./gradlew bootRun

# --- Windows PowerShell  (-D 인자는 PowerShell 이 분해하므로 환경변수를 쓸 것) ---
$env:DB_PASSWORD="<비밀번호>"
$env:JWT_SECRET="<32바이트 이상 랜덤>"                              # 생략 시 1회용 랜덤 키 + WARN
$env:ADMIN_INIT_ID="admin"; $env:ADMIN_INIT_PASSWORD="<강한 비밀번호>"
.\gradlew.bat bootRun

# 프로파일 변경
$env:SPRING_PROFILES_ACTIVE="dev"; .\gradlew.bat bootRun
```

`application-local.yml` 은 `.gitignore` 대상입니다. 커밋되는 것은 `.example` 템플릿뿐입니다.

| 주소 | 설명 |
|---|---|
| http://localhost:8080/api/v1/health | 상태 확인 (기동·DB·Flyway·부가세·반올림 정책) |
| http://localhost:8080/swagger-ui.html | API 문서 (**local/dev 전용**) |
| http://localhost:8080/api-docs | OpenAPI JSON (**local/dev 전용**) |

`JWT_SECRET` 을 비워 두면 기동할 때마다 1회용 랜덤 서명키를 만들고 WARN 을 남깁니다
(재기동하면 발급된 관리자 토큰이 전부 무효). `prod` 프로파일에는 기본값이 없으므로
환경변수를 빠뜨리면 **기동에 실패합니다** — 이것이 의도된 동작입니다.

### 3. 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

| 주소 | 설명 |
|---|---|
| http://localhost:3000 | 쇼핑몰 |
| http://localhost:3000/admin | 관리자 (noindex) |

### 4. 테스트 / 빌드

```bash
cd backend  && ./gradlew test      # 단위 + 통합 테스트 (임베디드 PostgreSQL, 132건)
cd frontend && npm run typecheck   # 타입 검사
cd frontend && npm run build       # 프로덕션 빌드 (standalone)
```

---

## 환경 설정

### 백엔드 프로파일

| 프로파일 | 파일 | 커밋 | 용도 |
|---|---|---|---|
| `local` (기본) | `application-local.yml` | **X** (템플릿만) | 개발자 PC + 로컬 PostgreSQL |
| `dev` | `application-dev.yml` | O | NCP 개발 서버 |
| `prod` | `application-prod.yml` | O | NCP 운영 |
| `test` | `src/test/resources/application-test.yml` | O | 자동화 테스트 (임베디드 PostgreSQL) |

API 문서는 **기본이 차단**이고 `local`/`dev` 에서만 열립니다. `SwaggerConfig` 의
`@Profile` 만으로는 springdoc 자동설정이 `/api-docs` 를 계속 서빙하므로,
`application.yml` 에서 `springdoc.*.enabled: false` 를 기본값으로 두었습니다.

### 비밀값 규칙

- **비밀값을 코드나 yml 에 적지 않는다.** 전부 환경변수로 주입한다.
- `prod` 프로파일에는 기본값(`:-`)을 두지 않는다 — 환경변수를 빠뜨리면 조용히 잘못된 값으로 뜨는 대신 **기동에 실패해야 한다**.
- 운영 주입: `/etc/hanseongge/api.env` (권한 640) → systemd `EnvironmentFile`
- 프론트엔드의 `NEXT_PUBLIC_*` 값은 **브라우저 번들에 그대로 노출**된다. 비밀값에는 이 접두사를 붙이지 않는다.

| 파일 | 커밋 | 내용 |
|---|---|---|
| `backend/.env.example` | O | 템플릿 (값 없음) |
| `backend/.env` | **X** | 실제 값 |
| `backend/.../application-local.yml.example` | O | 템플릿 |
| `backend/.../application-local.yml` | **X** | 개발자별 로컬 설정 |
| `frontend/.env.development` | O | 비밀 아닌 개발 값 |
| `frontend/.env.production` | O | 비밀 아닌 운영 값 |
| `frontend/.env.local` | **X** | 실제 비밀값 |

### 최초 관리자 계정

`admin_users` 가 비어 있을 때만 `ADMIN_INIT_ID` / `ADMIN_INIT_PASSWORD` 로 SUPER_ADMIN
1개를 BCrypt(cost 12) 해시로 생성합니다. 환경변수가 없으면 **생성하지 않고 WARN 만** 남깁니다.
마이그레이션이나 소스에 고정 계정을 두지 않는 이유는, 저장소를 읽을 수 있는 누구나
운영 관리자 자격을 얻게 되기 때문입니다.

### 관리자 인증 (STEP 5)

```
POST   /api/admin/auth/login        아이디+비밀번호 → Access(30분) / Refresh(8시간)
POST   /api/admin/auth/refresh      Refresh → Access 재발급
POST   /api/admin/auth/logout       토큰 세대 증가 = 발급된 토큰 즉시 무효화
GET    /api/admin/auth/me           내 정보
PATCH  /api/admin/auth/password     내 비밀번호 변경 (현재 비밀번호 확인)

GET    /api/admin/admins            SUPER_ADMIN 전용 계정 CRUD
POST   /api/admin/admins
PUT    /api/admin/admins/{id}
PATCH  /api/admin/admins/{id}/status      잠금 해제·정지 (사유 필수)
PATCH  /api/admin/admins/{id}/password    비밀번호 초기화 (사유 필수)

GET    /api/admin/audit-logs        감사로그 검색 (ADMIN 이상)
```

호출은 `Authorization: Bearer <accessToken>` 입니다.

| 규칙 | 내용 |
|---|---|
| 토큰 분리 | 관리자 토큰은 `aud="admin"`. 사용자 토큰(STEP 6, `aud="user"`)으로는 `/api/admin/**` 에 접근할 수 없다 |
| 계정 잠금 | 연속 **5회** 실패 시 `status=LOCKED`. 잠긴 뒤에는 비밀번호가 맞아도 거부 |
| Rate Limit | 로그인 엔드포인트 **IP당 분당 10회** (Bucket4j). 초과 시 429 + `Retry-After` |
| 토큰 회수 | 로그아웃·비밀번호 변경·권한/상태 변경이 `token_version` 을 올려 기존 토큰을 즉시 무효화 |
| 권한 계층 | `SUPER_ADMIN > ADMIN > MANAGER > OPERATOR`. 각 엔드포인트는 **최소 등급**만 선언한다 |
| 감사 | 로그인 성공·실패·차단·로그아웃, 계정 CRUD 전부 `audit_logs` 에 남는다 |

계정 관리에 **DELETE 는 없습니다.** 계정을 지우면 그 계정이 남긴 감사로그의 주체가
사라집니다. 퇴사·권한 회수는 `PATCH /{id}/status` 로 `DISABLED` 처리합니다.

감사 기록은 `@AuditLog` 어노테이션(AOP)으로 붙입니다. 행위자·IP·User-Agent·시각은
자동으로 채워지고, 변경 전 값은 서비스가 `AuditContext.before(...)` 로 넘깁니다.

```java
@AuditLog(action = AuditActions.ADMIN_CREATE,
          targetType = AuditActions.TARGET_ADMIN_USER,
          targetId = "#result.id()")
public AdminUserResponse create(AdminCreateRequest request) { ... }
```

비밀번호·해시·토큰이 들어간 키는 JSON 직렬화 시점에 `***` 로 마스킹됩니다 —
감사로그는 지우지 않는 테이블이라, 한 번 새면 영구히 남습니다.

### 시세 구조 (STEP 6) ★

이 시스템의 핵심입니다. **원본과 관리자 보정 시세를 하나의 값으로 덮어쓰지 않는다**는
요구사항이 3개의 물리적으로 다른 레이어로 구현되어 있습니다.

```
L1 원본   market_price_raw            append-only. 저장된 뒤 절대 변하지 않는다
             │                        KRX 자동수집 / 관리자 수동고시
             ▼
L2 정책   market_price_policies       metal × side 당 1행 (현재값)
             │                        AUTO / FIXED_ADJ / PERCENT_ADJ / MANUAL
             ▼   [변동률 검사]  |Δ%| > MAX_PRICE_CHANGE_PERCENT ?
             │      └─ YES ──► L4 승인대기  market_price_pending  (발행 없음)
             ▼ NO
L3 최종   market_prices_effective     append-only = 이력 겸용
             │                        현재값 = effective_to IS NULL (metal×side당 1행)
             ▼
        MarketPriceChangedEvent (AFTER_COMMIT) → 가격 캐시 무효화
```

예시 — 원본 200,000 에 정액 +10,000 을 보정한 뒤, 260,000 고시를 시도해 승인한 상태:

| 테이블 | 행 | 값 |
|---|---|---|
| `market_price_raw` | 1 | 200,000 (NORMAL) |
| `market_price_raw` | 2 | **260,000 (ANOMALY)** — 이상 판정이어도 원본은 그대로 |
| `market_prices_effective` | 1 | 210,000, `effective_to` 마감됨 |
| `market_prices_effective` | 2 | **270,000**, 현재값 |

원본 260,000 과 최종 270,000 이 각자의 자리에 남습니다. 어느 값도 덮어써지지 않습니다.

#### API

```
GET  /api/v1/prices/current                    공개 시세표 (시세문의 폴백 포함)
GET  /api/v1/prices/history?metal=&side=&days= 공개 이력 (차트용, 최대 365일)

GET  /api/admin/prices/current                 원본·보정·최종 3값 + 노후 여부
GET  /api/admin/prices/history?metal=&side=    변경 이력 (페이징)
GET  /api/admin/prices/collection-status       수집 상태 / 마지막 성공 시각
GET  /api/admin/prices/policies                보정 정책 목록
GET  /api/admin/prices/pending                 승인 대기 목록
PUT  /api/admin/prices/policies                보정 정책 변경        (ADMIN 이상)
POST /api/admin/prices/apply                   수동 고시 (사유 필수)  (ADMIN 이상)
POST /api/admin/prices/pending/{id}/approve    승인 → 발행           (ADMIN 이상)
POST /api/admin/prices/pending/{id}/reject     반려 → 시세 불변      (ADMIN 이상)
```

조회는 OPERATOR 이상, **시세를 바꾸는 조작만 ADMIN 이상**입니다 — 가격에 직결되는
유일한 조작이기 때문입니다 (설계서 §7.4).

#### 안전장치

| 상황 | 동작 | 가격이 0이 되지 않는 이유 |
|---|---|---|
| API 오류 / 타임아웃 | `market_price_fetch_logs` 기록, 3회 백오프 재시도 | 원본 미생성 → 발행 없음 → **기존 시세 유지** |
| 파싱 실패 / 범위 이탈 | 같은 로그, **재시도하지 않음** (다시 불러도 같다) | 동일 |
| 단위 오입력 (1돈 값을 1g 칸에) | 직전값의 3.75배 ±5% 감지 → 거부 | 동일 |
| 급격한 변동 | `\|Δ%\| > 5%`(설정값) → 승인 대기, 원본은 `ANOMALY` 로 보존 | 기존 값이 계속 현재값 |
| 유효 시세 없음 / 노후 | `PRICE_STALE_MINUTES`(기본 240) 초과 → **"시세문의"** | 가격 자리가 `0` 이 아니라 `null` |

`PricePerGram` 값 객체가 0 이하 값의 **생성 자체를 거부**하고, DB 에는
`price_per_gram > 0` CHECK 이 걸려 있습니다. 0원이 되려면 누군가 0을 명시적으로
INSERT 해야 하는데 그 자체가 DB 에서 거부됩니다.

#### 자동 수집 (TODO B-1)

`KrxMarketPriceProvider` 는 **인터페이스만 구현한 껍데기**이고 기본 비활성입니다.
공공데이터포털 API 의 상업적 이용 조건과 크롤링 약관 저촉 여부가 미확인이라(TODO B-1),
동작하는 수집기를 넣으면 켜는 순간 약관 위반이 될 수 있기 때문입니다. 현재 시세는
**관리자 수동 고시가 주력 경로**이며, 그 경로도 L1→L2→L3 를 그대로 통과합니다.

수집 주기는 `system_settings.MARKET_FETCH_CRON` 입니다. 어노테이션 상수가 아니라
DB 설정값이므로 **재배포 없이** 바꿀 수 있고, 매 실행마다 다시 읽습니다.

### 가격 계산 (STEP 7) ★

**가격은 저장된 값이 아니라 계산식입니다.** 상품 테이블에 판매가 컬럼이 없고,
`PriceCalculationService` 가 조회할 때마다 산출합니다. 그래서 시세를 바꾸면 상품 가격이
자동으로 바뀌고, **상품을 일괄 UPDATE 하는 배치는 존재하지 않습니다.**

```
1. 상품·옵션 로드 → 노출/판매 가능 여부
2. 가격 모델 정규화 (price_type → 구성요소 목록)
3. METAL_BASE: 최종 시세 조회
      없음 / 노후 → INQUIRY_ONLY 로 종료   (예외 아님, 0원 아님)
4. sort 순 누적 → 소계          ← 구성요소 합계 = 소계 (불변식)
5. 할인·쿠폰                     (STEP 11~13 에서 추가)
6. 부가세 정책 적용   [A-1 미확정 → 임시 NONE]
7. 반올림 정책 적용   [A-2 확정 = CEIL_100]   ← 마지막에 단 한 번
8. PriceQuote 반환
```

#### price_type 별 산식 (설계서 §7.1)

| `price_type` | 산식 |
|---|---|
| `FIXED` / `MANUAL` | `fixed_price` (시세 미참조 — 시세가 없어도 판매된다) |
| `MARKET_LINKED` | 시세 × 중량 + `labor_cost` |
| `MARKET_PLUS` | 시세 × 중량 + `labor_cost` + **옵션 추가공임** |
| `MARKET_MULTIPLIER` | (시세 × 중량) × `multiplier` + `labor_cost` |

중량은 **옵션 중량이 상품 중량보다 우선**합니다("3.75g / 7.5g" 같은 변형이 기본 형태).

#### 요구사항 예시

```
GET /api/v1/products/1/price

순금 24K 시세   210,000원/g × 3.7500g  =  787,500
공임            FIXED_AMOUNT           =   50,000
──────────────────────────────────────────────────
소계                                      837,500
부가세 (NONE — A-1 미확정)                       0
반올림 (CEIL_100)                                0
──────────────────────────────────────────────────
최종 판매 단가                            837,500원
```

시세를 205,000 으로 바꾸면(보정 +10,000 → 215,000) 같은 상품이 다음 조회부터
**856,300원**이 됩니다 — `215,000 × 3.75 + 50,000 = 856,250` 에 백원 올림.
상품 행은 손대지 않습니다.

#### API

```
GET /api/v1/products/{id}/price?optionId=          공개 가격 (마진 비노출)
GET /api/v1/products/{id}/prices                   옵션별 가격

GET /api/admin/products/{id}/price-preview         원본시세·보정액·정책까지 (OPERATOR 이상)
GET /api/admin/products/{id}/price-preview/options 옵션별 미리보기
```

관리자 미리보기는 **숨김(HIDDEN) 상품도 계산하고 캐시를 타지 않습니다.** 그 기능이 없으면
운영자가 가격 확인을 위해 일단 공개했다가 되돌리는 위험한 절차를 쓰게 됩니다.

#### 정책 두 가지

| 정책 | 설정 키 | 현재 | 미확정 시 동작 |
|---|---|---|---|
| 부가세 | `VAT_MODE` | **TBD (A-1)** | 임시 `NONE`(면세) + WARN 1회. `최종 = 소계`, 세액 0 |
| 반올림 | `PRICE_ROUNDING_MODE` | `CEIL_100` (확정) | 임시 `WON_FLOOR` + WARN 1회 |

부가세 임시값이 `NONE` 인 이유는 **최종 금액을 바꾸지 않는 유일한 값**이기 때문입니다.
`EXCLUDED` 로 두면 구성요소 합계보다 10% 비싼 가격이, `INCLUDED` 로 두면 면세 상품에
세액이 있다고 표시됩니다. 현재 적용 상태는 `/api/v1/health` 의 `vatMode` ·
`vatPolicyTemporary` 로 확인할 수 있습니다.

#### 반올림은 마지막에 한 번뿐

단계마다 반올림하면 **구성요소 합계와 최종가가 어긋나** 주문 명세서가 맞지 않습니다.
각 구성요소는 전(錢) 단위로 확정되어 합이 정확히 소계가 되고, 원/백원 반올림은
합계에만 적용됩니다. 조정된 금액은 `roundingDelta` 로 확인할 수 있습니다.

### 상품 관리 (STEP 8)

```
GET    /api/v1/categories                        활성 카테고리
GET    /api/v1/products?categoryId=&keyword=     공개 목록 (대표가 = 최저 옵션가)
GET    /api/v1/products/{id}                     공개 상세 (옵션별 가격 포함)

GET    /api/admin/categories                     조회는 OPERATOR 이상
POST   /api/admin/categories                     변경은 MANAGER 이상
PUT    /api/admin/categories/{id}
DELETE /api/admin/categories/{id}                상품/하위가 있으면 409

GET    /api/admin/products                       숨김·판매종료 포함
POST   /api/admin/products                       HIDDEN 으로 생성 + 기본 옵션 자동 생성
PUT    /api/admin/products/{id}
PATCH  /api/admin/products/{id}/status           판매 시작/중단 (사유 필수)
POST   /api/admin/products/{id}/options
PUT    /api/admin/products/{id}/options/{oid}
PATCH  /api/admin/products/{id}/options/{oid}/stock   재고 실사 (절대 수량, 사유 필수)
PUT    /api/admin/products/{id}/images           목록 통째 교체
```

#### 등록 흐름

```
1. POST /api/admin/products          → HIDDEN + 기본 옵션 1개 자동 생성
2. GET  .../price-preview            → 가격 확인 (숨김 상태에서도 계산된다)
3. PATCH .../options/{oid}/stock     → 재고 반영
4. PATCH .../status {ON_SALE}        → 판매 시작 (활성 옵션이 없으면 409)
```

**등록 직후 노출되지 않는 것이 의도된 동작입니다.** 그 시점에는 재고·이미지가 없고
시세 연동이면 가격도 확인되지 않았습니다. 그대로 공개되면 고객이 재고 없는 상품을
주문하거나 잘못된 가격을 봅니다.

#### 재고는 항상 옵션에만 있습니다

옵션 없이 상품을 등록하면 **서버가 기본 옵션 1개를 자동 생성**하고, 마지막 활성 옵션은
비활성화할 수 없습니다. "옵션 없는 상품" 개념을 없애면 재고 소스가 한 곳뿐이라
주문·취소·환불의 재고 경로가 분기 없이 단일해집니다 (개발계획 §9.3, 결함 D-7).

재고 조정은 **증감이 아니라 절대 수량**입니다. 실사는 "세어 보니 7개였다"이지
"3개 줄여라"가 아니고, 증감이면 요청이 중복 도착할 때 두 번 깎입니다.

#### 공개 API 가 지키는 것

| 규칙 | 내용 |
|---|---|
| 노출 조건 | 서버가 강제한다. `?status=HIDDEN` 을 보내도 무시된다 |
| 숨김 상품 상세 | **403 이 아니라 404** — 403 이면 "여기 뭔가 있다"가 새어 나간다 |
| 재고 수량 | 노출하지 않는다. `inStock`(불리언)만 |
| 원가 재료 | 목록에 공임·배수를 담지 않는다 |
| 가격 | 응답에 **이미 계산되어** 담긴다. 프론트엔드가 계산할 일이 없다 (P1) |
| 산출 실패 | 그 상품만 "시세문의"로 표시된다. 목록 전체가 500 이 되지 않는다 |

#### `purity` 는 표시 전용입니다

`metal_type=GOLD_18K` 자체가 순도를 내포하고 18K 시세가 별도로 고시되므로,
여기에 0.75 를 곱하면 **순도가 이중 적용**됩니다. `purity` 는 상세페이지 표기에만 쓰이고
어떤 계산식에도 들어가지 않습니다 (개발계획 §9.2).

### API 응답 계약

성공 응답은 DTO 를 그대로 돌려주고, 실패 응답만 표준 형태를 갖습니다.

```json
{ "code": "PRICE_UNAVAILABLE",
  "message": "현재 시세를 확인할 수 없습니다. 시세문의 후 주문해 주세요.",
  "timestamp": "2026-08-21T14:00:00+09:00" }
```

입력값 오류일 때만 `errors` 에 필드별 메시지가 추가됩니다. 스택트레이스·SQL·테이블명은
어떤 경우에도 응답에 담기지 않습니다.

---

## 코딩 규칙

### 절대 규칙

| # | 규칙 |
|---|---|
| P1 | **가격의 최종 판단은 항상 Backend.** Frontend 가 보낸 금액은 어떤 경우에도 신뢰하지 않는다 |
| P2 | **시세·주문가격 테이블은 append-only.** UPDATE 로 값을 덮어쓰는 경로를 DB 트리거로 봉쇄한다 |
| P3 | **모든 금액·중량은 `BigDecimal` / `NUMERIC`.** `double`·`float` 사용 금지 |
| P4 | Controller → Service → Repository. **Entity 를 API 로 반환하지 않는다** (DTO 사용) |
| P5 | 외부 연동(시세·PG·스토리지)은 인터페이스 뒤에 둔다 |
| P6 | 외부 시스템 실패가 **가격을 0·NULL 로 만드는 경로는 존재하지 않는다.** 실패 = 기존 값 유지 |

### 계층

```
com.hanseongge/
├── global/{config,auth,error,audit,util,health,dto}/
└── domain/{user,admin,audit,marketprice,pricing,product,cart,   ← marketprice = STEP 6
           order,payment,shipment,board,promotion,setting}/
       └── {controller,service,repository,entity,dto}/
```

```
Controller  DTO 입출력, 검증, 권한        — 비즈니스 로직 금지
    ↓
Service     비즈니스 로직, @Transactional  — 이 프로젝트의 유일한 계산 장소
    ↓
Repository  Spring Data JPA + QueryDSL
    ↓
Entity      도메인 모델                    — API 응답으로 직접 반환 금지
```

참조 구현: `domain/marketprice/` (**핵심** — 3층 파이프라인·Provider 추상화·이벤트),
`domain/pricing/` (가격 엔진 — 구성요소 정규화·정책 적용),
`domain/product/` (CRUD·검색·공개 조회 — 관리/조회 서비스 분리),
`domain/admin/` (인증·권한·계정 CRUD), `domain/audit/` (QueryDSL 동적검색·페이징),
`domain/setting/` (조회·캐시·갱신), `global/health/` (Controller → Service → DTO).

프론트엔드도 같은 원칙입니다.

```
views/  →  hooks/  →  api/  →  api/client.ts
```

화면 컴포넌트가 `fetch` 를 직접 부르지 않습니다. 인증 헤더·오류 포맷·베이스 URL 이 한 곳에서만 관리됩니다.

---

## 배포 (Docker 미사용)

```bash
export REMOTE_HOST=<NCP 서버 IP>
./infra/deploy/deploy.sh all      # api + web
./infra/deploy/rollback.sh api    # 직전 릴리스로 복귀
```

릴리스는 타임스탬프 디렉터리에 풀고 symlink 를 교체합니다. 직전 3개를 보관하므로 롤백은 symlink 를 되돌리는 것으로 끝납니다.

---

## 다음 단계

**STEP 9~10 — 쇼핑몰 화면 + 장바구니.** 프론트엔드가 지금의 공개 API 위에 올라가고,
`cart_items` 에 담은 시점 가격(`price_at_add`)과 현재가를 비교해 "시세가 변경되었습니다"를
표시하는 흐름이 붙습니다. 이미지 업로드(NCP Object Storage)도 이 구간입니다.

**V8 마이그레이션** — 설계 검증에서 나온 스키마 결함 중 남은 11건.
그중 **D-1(`product_price_components`)** 이 들어오면 주얼리(금+공임+부자재+스톤+마진)
가격이 표현됩니다. 가격 엔진은 이미 구성요소 기반이라 <b>불러오는 곳만</b> 바뀝니다.
상세는 [`docs/TODO_보류항목.md`](docs/TODO_보류항목.md) C-1 표 참조.
(V6 은 STEP 5 의 관리자 인증 컬럼이, V7 은 STEP 6 의 수집 로그가 가져갔습니다.)
