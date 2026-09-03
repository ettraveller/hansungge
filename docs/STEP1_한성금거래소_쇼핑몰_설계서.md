# 한성금거래소 쇼핑몰 — STEP 1 시스템 설계서

- 문서 버전: v0.2 (확정본)
- 작성일: 2026-08-19
- 대상 도메인: https://hanseongge.com
- 상태: **설계 확정 — STEP 2(DB Migration) 진행**
- 목표 오픈: 2026-10 중순 (D-2개월)

---

## 0. 확정된 핵심 결정사항

| # | 항목 | 확정 내용 |
|---|---|---|
| 1 | ERP 연동 | **전면 제외** (ERP 개발은 최소 1년 후). 쇼핑몰은 완전 독립 시스템, PostgreSQL 단독. ERP 연동은 Phase 2 이후 과제 |
| 2 | 시세 소스 | 관리자 수동 고시(주력) + KRX 자동 수집(10:00~18:00, 30분 간격, cron 설정값). 유효 시세 부재/노후 시 **"시세문의" 표시 + 시세연동 상품 구매 차단**. KRX 수집 방식의 약관 확인은 TODO B-1 |
| 3 | 프론트엔드 | Next.js (React, TypeScript, SSR) — 쇼핑몰 SSR로 네이버 SEO 확보, 관리자는 동일 프로젝트 `/admin` (noindex) |
| 4 | 인증 | 사용자: OAuth2(G/N/K) + JWT Access(30분) + Refresh(HttpOnly Cookie, DB 저장). 관리자: ID/PW 별도 로그인, 별도 토큰 audience |
| 5 | 결제 | **PG사 1곳 통합 결제창** (카드+네이버페이+카카오페이). PG사 선정·계약은 TODO A-3 (즉시 착수) |
| 6 | 매입(BUY) 시세 | **시세표 게시만**. 온라인 매입 접수는 범위 제외 |
| 7 | 배포 | **Docker 미사용.** systemd 서비스 2개(Spring Boot jar, Next.js standalone) + Nginx |
| 8 | 보류 항목 | 부가세 정책, 반올림 정책 등은 `TODO_보류항목.md`에서 관리. STEP 7 착수 전 확정 필요 |

## 1. 전체 시스템 아키텍처

```
[사용자 브라우저]                       [관리자 브라우저]
      │ HTTPS                                │ HTTPS
      ▼                                      ▼
┌─────────────────────────────────────────────────┐
│  Nginx (TLS 종단, 정적자산, /api 리버스프록시)      │
└───────────────┬─────────────────┬───────────────┘
                │                 │
      ┌─────────▼──────┐  ┌───────▼────────────────┐
      │ Next.js (SSR)  │  │ Spring Boot REST API   │
      │ shop + /admin  │  │ (api.hanseongge.com)   │
      └─────────┬──────┘  └───────┬──────────┬─────┘
                │ 서버측 API 호출   │          │
                └────────────────►│          │
                                  ▼          ▼
                        ┌──────────────┐ ┌──────────────────┐
                        │ PostgreSQL   │ │ 외부 연동          │
                        │ (Private)    │                         └──────────────┘ │ - PG (네이버/카카오/카드)│
                                         │ - OAuth (G/N/K)   │
                                         │ - Object Storage  │
                                         └──────────────────┘
```

- FE/BE 완전 분리. JSP/모놀리식 금지 (지시서 2항 준수).
- 가격의 최종 판단은 항상 Backend. FE는 표시만 담당 (지시서 14항).
- 이미지: NCP Object Storage, DB에는 object key만 저장 (지시서 33항).

## 2. 디렉터리 구조

### 2.1 저장소 구성 (모노레포)

```
hanseongge/
├── backend/          # Spring Boot
├── frontend/         # Next.js (shop + admin)
├── docs/             # 설계서, ERD, 운영문서
└── infra/            # nginx conf, 배포 스크립트, (선택) Dockerfile
```

### 2.2 Backend (Spring Boot, Gradle, Java 21)

```
backend/src/main/java/com/hanseongge/
├── HanseonggeApplication.java
├── global/
│   ├── config/            # Security, CORS, Swagger, JPA, QueryDSL
│   ├── auth/              # JWT, OAuth2 핸들러, 관리자 인증
│   ├── error/             # 공통 예외, ErrorCode, GlobalExceptionHandler
│   ├── audit/             # AuditLog AOP/서비스
│   └── util/              # Money/Rounding 유틸 (BigDecimal 전용)
├── domain/
│   ├── user/              # 회원, social_accounts, roles
│   ├── admin/             # 관리자 계정/권한
│   ├── marketprice/       # ★ 시세 (raw/policy/effective/provider)
│   │   ├── provider/      #    MarketPriceProvider 구현체들
│   │   └── ...
│   ├── pricing/           # ★ PriceCalculationService, 가격정책
│   ├── product/           # 상품/옵션/카테고리/이미지
│   ├── cart/
│   ├── order/             # 주문 + OrderItem Snapshot
│   ├── payment/           # PaymentProvider 추상화
│   ├── shipment/
│   ├── board/             # 공지/뉴스/FAQ/QnA
│   ├── promotion/         # 쿠폰/이벤트
│   └── setting/           # system_settings
└── (각 domain 내부: controller / dto / service / repository / entity)
```

계층 원칙: Controller → Service → Repository, Entity 직접 반환 금지(DTO), 비즈니스/가격 로직은 Service 계층 전용.

### 2.3 Frontend (Next.js, TypeScript)

```
frontend/src/
├── app/
│   ├── (shop)/            # /, /shop, /shop/product/[id], /price, /cart,
│   │   ...                # /order, /mypage, /login, /notice, /news,
│   │                      # /store, /purchase-guide
│   └── admin/             # /admin/*  (noindex, 인증 가드)
├── components/            # 공통 UI (기존 프로토타입 HTML 톤 이관: --gold 등)
├── features/              # 도메인별 화면 로직 (price, product, cart, order…)
├── lib/api/               # API 클라이언트 (typed)
└── styles/
```

디자인 토큰은 기존 프로토타입(`claude_hansung_gold_main.html`)의 CSS 변수 체계(`--gold:#c9a227` 등)를 그대로 이관하되, 브랜드 확정 컬러(#D5BB41)와의 최종 조율은 별도 결정.

## 3. DB ERD (핵심 영역)

### 3.1 시세 영역 (★ 최우선 검토 대상)

```
metal_type (ENUM/코드): GOLD_24K, GOLD_18K, GOLD_14K, SILVER, PLATINUM
price_side (ENUM): SELL(판매), BUY(매입)
기준 단위: 모든 시세 = KRW / 1g, 모든 중량 = gram (NUMERIC)

market_price_sources            ─ 시세 소스 레지스트리
  id, code(ERP/KRX/MANUAL/…), name, active, priority

market_price_raw                ─ 수집 원본 (append-only)
  id, source_id(FK), metal_type, price_per_gram NUMERIC(15,2),
  collected_at, status(NORMAL|ANOMALY|FAILED), raw_payload JSONB

market_price_policies           ─ 관리자 보정 정책 (metal×side당 1행, 현재값)
  id, metal_type, price_side,
  mode(AUTO|FIXED_ADJ|PERCENT_ADJ|MANUAL),
  adj_amount NUMERIC(15,2), adj_percent NUMERIC(7,4),
  manual_price NUMERIC(15,2),
  max_change_percent NUMERIC(5,2),      -- 안전장치 임계값
  updated_by, updated_at

market_prices_effective         ─ 최종 적용 시세 (append-only = 이력 겸용)
  id, metal_type, price_side,
  price_per_gram NUMERIC(15,2),
  base_raw_id(FK nullable),             -- 근거 원본
  policy_mode, policy_snapshot JSONB,   -- 적용 당시 정책 스냅샷
  effective_from, effective_to,         -- 현재값: effective_to IS NULL
  created_by, created_at, change_reason
  UNIQUE 부분 인덱스: (metal_type, price_side) WHERE effective_to IS NULL

market_price_pending            ─ 이상 변동 승인 대기 큐
  id, raw_id(FK), calc_price, prev_price, change_percent,
  status(PENDING|APPROVED|REJECTED), decided_by, decided_at
```

시세 변경 = `effective` 기존 행의 `effective_to` 마감 + 새 행 INSERT. **UPDATE로 가격을 덮어쓰는 일이 구조적으로 불가능**하게 한다 (지시서 10, 45항).

### 3.2 상품/가격 영역

```
categories        id, parent_id, name, sort, active   (관리자 CRUD — 지시서 4항)
products          id, category_id, name, description, thumbnail_key,
                  metal_type(nullable), price_type(FIXED|MARKET_LINKED|
                  MARKET_PLUS|MARKET_MULTIPLIER|MANUAL),
                  weight_g NUMERIC(12,4), purity NUMERIC(6,4),
                  labor_cost NUMERIC(15,2), multiplier NUMERIC(8,4),
                  fixed_price NUMERIC(15,2), status, active, audit컬럼
product_options   id, product_id, name, weight_g, additional_labor_cost,
                  stock, active
product_images    id, product_id, object_key, sort
price_calculation_logs  (선택) 계산 감사용
```

### 3.3 회원/인증

```
users             id, email, name, phone, status, created_at …
social_accounts   id, user_id, provider(GOOGLE|NAVER|KAKAO),
                  provider_user_id, UNIQUE(provider, provider_user_id)
roles / user_roles
admin_users       id, login_id, password_hash, name, role, totp_secret?, status
refresh_tokens    id, user_id/admin_id, token_hash, expires_at, revoked
```

동일 이메일 정책(지시서 21항): 이메일이 같아도 provider가 다르면 **별도 계정 생성 후 마이페이지에서 수동 연동** 방식 제안 (자동 병합은 계정 탈취 위험).

### 3.4 장바구니/주문/결제

```
carts             id, user_id
cart_items        id, cart_id, product_id, option_id, quantity,
                  price_at_add NUMERIC(15,2), added_at        -- 시세변동 비교용

orders            id, order_no, user_id, status, total_amount,
                  receiver/주소/요청사항, ordered_at
order_items       id, order_id,
                  -- ★ Snapshot (지시서 15항 필수 필드 전부)
                  product_id, product_name, option_name,
                  weight_g, metal_type,
                  market_price, adjustment, labor_cost,
                  discount, tax, final_unit_price,
                  quantity, final_amount,
                  effective_price_id(FK)                       -- 근거 시세

payments          id, order_id, provider(NAVERPAY|KAKAOPAY|CARD),
                  status, amount, approved_at
payment_transactions  id, payment_id, tx_type(READY|APPROVE|CANCEL|REFUND),
                  pg_tid, request/response JSONB, created_at

shipments         id, order_id, carrier, tracking_no, status, shipped_at
```

### 3.5 기타

```
coupons, coupon_issues, events
notices, news, faqs, qnas
audit_logs        id, actor_type(ADMIN|SYSTEM), actor, action, target_type,
                  target_id, before_value JSONB, after_value JSONB,
                  ip, user_agent, created_at
system_settings   key, value, description, updated_by, updated_at
                  (부가세 정책, 반올림 정책, 배송비, MAX_PRICE_CHANGE_PERCENT 등)
```

## 4. 주요 Entity 목록 (요약)

| 영역 | Entity |
|---|---|
| 시세 | MarketPriceSource, MarketPriceRaw, MarketPricePolicy, MarketPriceEffective, MarketPricePending |
| 상품 | Category, Product, ProductOption, ProductImage |
| 가격 | PricePolicy(Product 내장 or 분리), PriceCalculationLog |
| 회원 | User, SocialAccount, Role, RefreshToken |
| 관리자 | AdminUser, AuditLog |
| 거래 | Cart, CartItem, Order, OrderItem, Payment, PaymentTransaction, Shipment |
| 콘텐츠 | Notice, News, Faq, Qna, Coupon, Event |
| 설정 | SystemSetting |

## 5. API 목록 (엔드포인트 그룹)

### 공개 (Public)
```
GET  /api/v1/prices/current                # 금속별 현재 시세 (SELL/BUY)
GET  /api/v1/prices/history?metal=&days=   # 시세 차트용
GET  /api/v1/categories
GET  /api/v1/products?category=&q=&page=
GET  /api/v1/products/{id}                 # 서버 계산 현재가 포함
GET  /api/v1/notices, /news, /faqs, /stores, /events
```

### 사용자 (JWT)
```
POST /api/v1/auth/oauth/{provider}/callback
POST /api/v1/auth/refresh | /logout
GET|POST|PATCH|DELETE /api/v1/cart/items   # 조회 시 현재가 vs 담은가격 비교 플래그
POST /api/v1/orders                        # ★ 서버 가격 재계산 + Snapshot 저장
GET  /api/v1/orders, /orders/{id}
POST /api/v1/payments/{orderId}/ready      # PG 결제 준비
POST /api/v1/payments/callback/{provider}  # PG 승인 콜백 → 서버 검증
GET|PATCH /api/v1/mypage/**
POST /api/v1/qnas
```

### 관리자 (ROLE_ADMIN, /api/admin/**)
```
POST /api/admin/auth/login
GET  /api/admin/dashboard                  # 지시서 23항 지표
GET|PUT /api/admin/prices/policies         # 보정 방식/값 설정
POST /api/admin/prices/apply               # 최종 시세 적용 (사유 필수)
GET  /api/admin/prices/history
GET|POST /api/admin/prices/pending/{id}/approve|reject
CRUD /api/admin/products, /categories, /coupons, /events
GET|PATCH /api/admin/orders, /shipments, /payments(취소/환불)
GET|PATCH /api/admin/users
CRUD /api/admin/notices, /news, /faqs, /qnas(답변)
CRUD /api/admin/admins, /roles
GET  /api/admin/audit-logs
GET|PUT /api/admin/settings
```

문서화: springdoc-openapi (Swagger UI, dev 환경만 노출).

## 6. 시세 시스템 구조 (상세)

### 6.1 데이터 흐름

```
[Provider 수집/입력]
  ManualProvider(관리자 고시) ─┬──► market_price_raw (원본, append-only)
  KrxProvider(자동 수집)      ─┘         │
                                    ▼
                       [Validation + 이상변동 검사]
                        |Δ%| > MAX_PRICE_CHANGE_PERCENT ?
                          ├─ YES → market_price_pending (관리자 승인 대기)
                          └─ NO  ↓
                       [market_price_policies 적용]
                        AUTO / FIXED_ADJ / PERCENT_ADJ / MANUAL
                        (SELL / BUY 각각)
                                    ▼
                       market_prices_effective (신규 행 INSERT)
                                    ▼
                       MarketPriceChangedEvent (Spring in-process 이벤트)
                          ├─ 상품 표시가 캐시 무효화
                          └─ audit_logs 기록
```

### 6.2 Provider 인터페이스

```java
public interface MarketPriceProvider {
    String sourceCode();
    Optional<RawPrice> fetch(MetalType metal);   // 실패 시 empty + 로그
}
```
- 구현체: `ManualMarketPriceProvider`(관리자 고시, 주력), `KrxMarketPriceProvider`(자동 수집, 약관 확인 후 활성화).
- 스케줄러: cron은 system_settings 설정값 (기본 10:00~18:00, 30분 간격. KRX 금시장 정규장은 09:00~15:30이므로 15:30 이후는 종가 고정). 실패 시 **기존 effective 유지**, 대시보드에 수집상태/최종 성공시각 표시, 재시도 백오프. 실패가 시세를 0원으로 만드는 경로는 없음 (수집 실패 = raw 미생성 = effective 불변).
- **"시세문의" 폴백**: 유효 effective가 없거나 `PRICE_STALE_MINUTES` 초과 노후 시, 시세연동 상품은 가격 대신 "시세문의"를 표시하고 장바구니/주문을 차단한다.

### 6.3 안전장치 (지시서 20항)

1. `MAX_PRICE_CHANGE_PERCENT` (system_settings, 기본 5%) 초과 → pending 큐, 자동 적용 중지, 대시보드 경고.
2. 가격 ≤ 0, NULL, 단위 오류(1돈가가 g가로 들어옴 등 범위 검증) → FAILED 처리.
3. effective 테이블 append-only + 부분 UNIQUE 인덱스로 "현재값 중복" 방지.
4. 모든 적용에 change_reason + created_by 필수.

## 7. 상품 가격 계산 구조 (상세)

### 7.1 PriceCalculationService

```java
PriceResult calculateProductPrice(Long productId, Long optionId, LocalDateTime at);
```

계산 순서 (모두 BigDecimal, 반올림 정책은 system_settings):

```
1. 상품 price_type 확인
2. FIXED/MANUAL → fixed_price 반환 (시세 미참조)
3. MARKET_* → metal_type의 현재 effective(SELL) 조회
4. 기본금액 = effective_price × weight_g          (option weight 우선)
5. price_type별:
     MARKET_LINKED     : 기본금액 + labor_cost
     MARKET_PLUS       : 기본금액 + labor_cost (+ option 추가공임)
     MARKET_MULTIPLIER : 기본금액 × multiplier + labor_cost
6. 할인/쿠폰 적용 (주문 시점에만)
7. 부가세 정책 적용 (system_settings: 포함가/별도 — 관리자 설정)
8. 반올림 정책 적용 (예: 백원 단위 ROUNDDOWN — 관리자 설정, ERP 정산 로직의
   천원 반올림 관행과 통일 여부는 클라이언트 확인 필요)
9. PriceResult { marketPrice, adjustment, laborCost, discount, tax,
                 finalUnitPrice, effectivePriceId } 반환
```

### 7.2 사용 지점 (서버 재계산 원칙 — 지시서 14, 16항)

| 시점 | 동작 |
|---|---|
| 상품 목록/상세 조회 | 현재 시세로 계산해 표시 (캐시 가능, 시세변경 이벤트로 무효화) |
| 장바구니 조회 | 현재가 재계산, `price_at_add`와 다르면 "시세가 변경되었습니다" 플래그 |
| 주문 생성 | **서버 재계산 → OrderItem Snapshot 저장** (FE 가격 절대 신뢰 금지) |
| 결제 승인 직전 | Snapshot 금액 == PG 승인금액 검증, 불일치 시 결제 중단 |
| 결제 완료 후 시세 변경 | 기존 주문 불변 (Snapshot이므로 구조적으로 불변) |

### 7.3 캐싱

초기: 애플리케이션 인메모리 캐시(Caffeine) + `MarketPriceChangedEvent`로 무효화. Redis는 트래픽 발생 후 도입 (지시서 41항 — 처음부터 넣지 않음).

## 8. 관리자 메뉴 구조

```
/admin
├── 대시보드          (주문/매출/결제대기/배송/회원/재고/현재시세/수집상태/최근변경)
├── 시세관리
│   ├── 오늘의 시세    (금속×매입/판매별: 원본·출처·수집시각·보정방식·최종가·저장)
│   ├── 보정정책 설정
│   ├── 승인 대기      (이상변동)
│   └── 변경 이력
├── 상품관리 / 카테고리관리
├── 주문관리 / 배송관리 / 결제관리(취소·환불)
├── 회원관리
├── 쿠폰·이벤트
├── 콘텐츠 (공지 / 뉴스 / FAQ / QnA / 매입절차 / 지점안내)
├── 시스템 (관리자계정 / 권한 / 감사로그 / 설정)
```

## 9. 사용자 메뉴 구조

```
/                  메인 (시세 위젯, 대표상품, 공지) — 기존 프로토타입 기반
/price             금·은 시세 (차트 + 매입/판매가 표)
/shop, /shop/{category}, /shop/product/{id}
/cart → /order → 결제 → /order/complete
/mypage            주문내역, 배송조회, 정보수정, 소셜계정 연동, 쿠폰
/login             Google / Naver / Kakao
/notice /news /event /faq /qna
/purchase-guide    매입절차
/store             지점안내
```

## 10. 인증 구조

```
사용자: OAuth2 (Spring Security OAuth2 Client)
  Google/Naver/Kakao → provider_user_id로 social_accounts 매칭
  → 없으면 users + social_accounts 생성
  → JWT Access(30m) 발급 + Refresh(14d, HttpOnly Secure Cookie, DB hash 저장)
  동일 이메일·타 provider = 별도 계정, 마이페이지에서 수동 연동

관리자: /api/admin/auth/login (ID/PW, BCrypt)
  별도 토큰 audience=admin, ROLE_ADMIN → 확장: SUPER_ADMIN/MANAGER/OPERATOR
  로그인/로그아웃/실패 모두 audit_logs
  /admin 화면은 robots noindex + 인증 가드 (일반 사용자 비노출)
```

보안 체크리스트(지시서 37항): HTTPS 전 구간, BCrypt, CSRF(쿠키 사용 지점), CORS 화이트리스트, Bean Validation, JPA 파라미터 바인딩(SQLi), 출력 이스케이프(XSS), Bucket4j Rate Limit(로그인·결제), 민감정보 컬럼 암호화(전화번호 등), 로그 마스킹, DB Private Subnet.

## 11. 결제 구조

```java
public interface PaymentProvider {
    ReadyResult ready(Order order);          // 결제창 준비
    ApproveResult approve(ApproveRequest r); // PG 승인 → 서버 검증
    CancelResult cancel(Payment p, BigDecimal amount, String reason);
}
// NaverPayProvider / KakaoPayProvider / CardPaymentProvider(PG사 확정 필요)
```

승인 흐름 (지시서 24항):

```
주문 생성(Snapshot) → PAYMENT_PENDING
→ ready() → 사용자 PG 결제창
→ PG 콜백 → 서버 approve() 호출
→ ★ 승인금액 == Snapshot 금액 검증
→ 일치: PAID + payment_transactions 기록 / 불일치: 승인취소 + 오류
```

- 프론트 "성공" 신호만으로 상태 변경 절대 금지.
- 카드 일반결제 PG사(토스페이먼츠/KG이니시스 등)는 클라이언트 계약 후 확정 — Provider 추상화로 코드 영향 최소화.
- 주문 상태: 지시서 25항의 10개 상태 그대로 채택.

## 12. NCP 배포 구조

```
Internet
  ▼
NCP Load Balancer / 공인 IP + Nginx (TLS, WAF 성격의 기본 필터)
  ├─ hanseongge.com      → Next.js (Node, systemd 또는 Docker)
  └─ api.hanseongge.com  → Spring Boot (systemd 또는 Docker)
  ▼
NCP Cloud DB for PostgreSQL (Private Subnet, 외부 비노출)
NCP Object Storage (상품 이미지, CDN+ 연계 가능)
```

- 개발: 로컬 개발 → NCP dev DB (VPN 접속 패턴 재사용).
- 배포: **Docker 미사용 확정.** systemd 서비스 2개(spring-boot jar, next standalone) + Nginx. 배포 스크립트 = scp + systemctl restart, 롤백 = 이전 jar 보관.
- 공인망 노출은 Nginx 443 단일. 8080/3000은 localhost 바인딩, DB는 Private Subnet(앱 서버만 허용), /api/admin은 Nginx에서 고정 IP 제한, PG 콜백은 서명+IP 허용목록+금액 대조 3중 검증, Swagger/Actuator prod 비노출.
- 무중단은 초기 불필요, 단일 인스턴스로 시작.

## 13. 개발 Roadmap (지시서 43항 18 STEP 기준)

| 구간 | STEP | 내용 | 산출물 |
|---|---|---|---|
| M1 기반 | 1–3 | 본 설계 확정, 프로젝트 스캐폴딩, Flyway V1(전 테이블), 공통 모듈 | 실행 가능한 빈 앱 + ERD 확정 |
| M2 인증 | 4–5 | OAuth 로그인, 관리자 인증/권한, audit 기반 | 로그인 동작 |
| M3 시세·가격 ★ | 6–7 | 시세 3층 구조, Provider, 안전장치, PriceCalculationService, **단위테스트(시나리오 1–6)** | 시세관리 화면 + 가격엔진 |
| M4 상품·쇼핑몰 | 8–10 | 상품/카테고리/옵션, 쇼핑몰 화면(프로토타입 이관), 장바구니 | 상품 노출·가격 연동 |
| M5 주문·결제 | 11–13 | 주문 Snapshot, PG 연동, 마이페이지 | 결제 E2E |
| M6 운영 | 14–16 | 관리자 잔여 메뉴, 게시판/뉴스, SEO | 운영 가능 수준 |
| M7 출시 | 17–18 | 통합테스트, NCP 배포, 도메인/SSL | 오픈 |

테스트 필수 시나리오(지시서 39항)는 M3에서 **가격 엔진 구현과 동시에** JUnit으로 고정한다. 특히:
Snapshot 불변(S3), API 실패 시 기존 시세 유지(S4), 이상변동 승인(S5), 시세변경 전파(S2/S6).

---

## 부록 A. 지시서 대비 설계 반영 매핑

| 지시서 항목 | 반영 위치 |
|---|---|
| 5–6 시세 3층 분리 | §3.1, §6.1 (raw / policy / effective) |
| 7 시세종류·단위 | metal_type + price_side, 내부 1g 통일 |
| 8–9 시세관리 화면·보정방식 | §8, market_price_policies.mode |
| 10 변경이력 | effective append-only + audit_logs |
| 11–13 가격 자동계산·정책·엔진 | §7 PriceCalculationService |
| 14 서버 재계산 | §7.2 |
| 15–17 Snapshot·장바구니 | §3.4, §7.2 |
| 18–20 Provider·안전장치 | §6.2–6.3 |
| 21–22 로그인 분리 | §10 |
| 24 결제 추상화 | §11 |
| 29 BigDecimal | 전 금액/중량 NUMERIC + BigDecimal |
| 35 Flyway | M1 |
| 36 감사로그 | audit_logs + AOP |
| 40 GOOD 구조 | 상품에 가격 저장 안 함, 정책+시세+중량 계산 |

## 부록 B. 보류/확인 항목

전 항목을 `TODO_보류항목.md`로 이관하여 관리한다. (부가세 A-1, 반올림 A-2, PG 계약 A-3, OAuth 검수 A-4, KRX 약관 B-1 등)
