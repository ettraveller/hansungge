# 한성금거래소 쇼핑몰 — TODO / 보류 항목

- 갱신일: 2026-08-20
- 목표 오픈: **2026-10 중순 (D-2개월)**
- 규칙: "나중에 알려줌"으로 보류된 항목은 전부 여기에 기록하고, 확정 시 설계서에 반영 후 완료 처리한다.

---

## A. 발주자(한성금거래소) 확정 대기

| # | 항목 | 내용 | 막히는 작업 | 기한 권장 |
|---|---|---|---|---|
| A-1 | 부가세 정책 | 판매가 부가세 포함/별도 표시. 골드바 면세·과세 구분은 세무사 확인 권장. **STEP 7 은 미확정 상태로 진행됨** — `VAT_MODE=TBD` 이면 임시 `NONE`(면세, 최종=소계) + WARN 으로 동작한다. 확정 시 `system_settings` 값만 변경 | ~~가격 표시 문구~~, 주문 tax 필드 계산(STEP 13) | **주문 기능(STEP 13) 착수 전** |
| ~~A-2~~ | ~~반올림 정책~~ | **확정(2026-08-24): 최종 판매가 백원 단위 올림(`CEIL_100`).** 예) 51,232.6 → 51,300 · 512,326 → 512,400. 적용은 `system_settings.PRICE_ROUNDING_MODE` 행 (코드 상수 아님) | — | ✅ 완료 |
| A-3 | PG사 확정 + 계약 | 통합 결제창 1곳 (토스페이먼츠/KG이니시스 등). **계약·가맹심사는 즉시 착수 필요 (심사 1~3주 소요)** | STEP 12 결제 연동 | 즉시 |
| A-4 | OAuth 앱 등록 | Naver/Kakao 사업자 명의 등록 + 검수. 사업자등록증, 개인정보처리방침 URL 필요 | STEP 4 소셜 로그인 실서비스 전환 | 즉시 (검수 수일~수주) |
| A-5 | 사업자 도메인/SSL | hanseongge.com 소유 확인, NCP 연결 | STEP 18 배포 | 오픈 3주 전 |
| A-6 | 개인정보처리방침·이용약관 | 법무 검토 문서 | 회원가입 오픈 | 오픈 2주 전 |
| A-7 | **금속별 파생 비율 + 매입/판매 마진 정책** | 18K/14K를 24K에서 자동 환산할 비율, 매입가/판매가 마진(레거시 크롤러 상수 +5%/-7%의 실제 영업정책 부합 여부 검증). 확정값은 코드가 아니라 `market_price_policies` 행으로 관리 | STEP 7 시세엔진 (W3) — 파생 시세(`mode=DERIVED`) 설정 | W3 착수 전 |
| ~~A-8~~ | ~~Docker 운영 사용 여부~~ | **확정(2026-08-20): Docker 전면 미사용.** 개발·CI·운영 모두 systemd/로컬 설치 방식 | — | ✅ 완료 |

## B. 개발자(주인) 확인/조사 대기

| # | 항목 | 내용 |
|---|---|---|
| B-1 | KRX 시세 수집 방식 최종 확정 | 공공데이터포털 KRX 금시세 API(일별)의 상업적 이용 조건 확인. 장중 30분 간격 데이터가 필요하면 KRX 정보데이터시스템 크롤링의 약관 저촉 여부 확인 필요. 저촉 시 → 수동 고시 주력 + 일별 API 참고용 |
| B-2 | KRX 장 시간 반영 | 금시장 정규장은 09:00~15:30. 크롤링 스케줄 10:00~18:00 중 15:30 이후 구간은 종가 고정이므로 스케줄 설정값(cron)으로 관리하고 낭비 호출 최소화 |
| B-3 | 재고-매장 관계 | ERP 부재 확정에 따라 쇼핑몰 stock은 자체 관리. 실물 재고와의 대사 절차(수동) 운영안 정리 |

## C. 확정된 사항 (설계서 v0.2 반영 예정)

- [x] ERP 연동 전면 제외 (최소 1년 후) — 쇼핑몰 완전 독립, PostgreSQL 단독
- [x] 시세: 크롤링/수집 10:00~18:00 30분 간격(설정값) + 관리자 수동 고시 + 유효 시세 부재 시 **"시세문의" 표시 및 시세연동 상품 구매 차단**
- [x] 매입(BUY) 시세: **시세표 게시만** (온라인 매입 접수 없음 → Phase 범위 축소)
- [x] 결제: PG사 1곳 통합 결제창 (카드+네이버페이+카카오페이)
- [x] Docker 미사용 — systemd 직접 배포 (Nginx + Spring Boot jar + Next.js standalone)
- [x] FE: Next.js(TypeScript, SSR) / 인증: JWT + Refresh(HttpOnly Cookie)
- [x] 설계서 v0.2 확정 (2026-08-19)
- [x] STEP 2 완료: Flyway V1~V5 스키마 작성 + PostgreSQL 16 실행 검증 (32테이블, append-only 트리거·정합성 제약 동작 확인)
- [x] 전체개발계획 v1.0 작성 (2026-08-20) — 기존 스키마 전수 검증 결과 **결함 12건(치명 4건)** 도출, V6 마이그레이션 설계 완료
- [x] **STEP 1 완료 (2026-08-20): 프로젝트 골격 생성.** 모노레포(backend/frontend/infra/docs), 프론트엔드 프로덕션 빌드 통과, FE↔BE 연동 확인
- [x] **STEP 3 완료 (2026-08-21): 백엔드 스캐폴딩 재구성.** Kotlin DSL + Java 21 + Boot 3.5.3, 설계서 §2.2 패키지 구조(global/domain), 프로파일 local·dev·prod 분리, 공통 모듈(ErrorCode·GlobalExceptionHandler·BaseTimeEntity·Money), SystemSettingService(Caffeine), 최초 관리자 부트스트랩, 헬스체크. **실제 PostgreSQL 16 에서 Flyway V1~V5 적용 + 32테이블 생성 + HTTP 헬스체크 검증 완료 (테스트 22건 전원 통과)**
- [x] **STEP 5 완료 (2026-08-24): 관리자 인증·권한·감사로그.** `POST /api/admin/auth/login`(BCrypt cost 12 → aud=admin JWT, Access 30분/Refresh 8시간), 연속 5회 실패 시 계정 LOCKED, 로그인 성공·실패·차단·로그아웃 전량 `audit_logs` 기록, 권한 계층(SUPER_ADMIN>ADMIN>MANAGER>OPERATOR) + `@PreAuthorize`, `@AuditLog` AOP(actor·IP·UA·target·before/after JSONB 자동), 관리자 계정 CRUD(`/api/admin/admins`, SUPER_ADMIN 전용), 감사로그 검색(`GET /api/admin/audit-logs`, QueryDSL 동적조건+페이징), 로그인 Bucket4j Rate Limit(IP당 분당 10회). **V6 마이그레이션 적용 + 통합테스트 44건 전원 통과**
- [x] **STEP 6 완료 (2026-08-26): 시세 3층 구조 + 안전장치.** L1 원본(`market_price_raw`) → L2 정책(`market_price_policies`, AUTO/FIXED_ADJ/PERCENT_ADJ/MANUAL) → L3 최종(`market_prices_effective`, append-only) → L4 승인대기(`market_price_pending`) 파이프라인, `MarketPriceProvider` 인터페이스 + `ManualMarketPriceProvider`(주력) + `KrxMarketPriceProvider`(껍데기, 기본 비활성), 변동률 임계 초과 시 발행 중단 + pending, advisory lock 기반 마감·발행, `MarketPriceChangedEvent`(AFTER_COMMIT) → 가격 캐시 무효화, `MARKET_FETCH_CRON` 기반 동적 스케줄러 + `market_price_fetch_logs`(V7), "시세문의" 폴백, 관리자/공개 API. **테스트 78건 전원 통과 (시나리오 S1·S2·S4·S5·S6 포함)**
- [x] **STEP 7 완료 (2026-08-26): 상품 가격 계산 엔진.** `PriceCalculationService` — 상품·옵션 로드 → 가격모델 정규화(SIMPLE → 구성요소) → 시세 조회(없음/노후 시 INQUIRY_ONLY) → 구성요소 누적 → 부가세 → **반올림 1회**. `price_type` 4방식(FIXED/MANUAL·MARKET_LINKED·MARKET_PLUS·MARKET_MULTIPLIER), 옵션 중량 우선, 상품 도메인 엔티티(Category/Product/ProductOption), `VatMode`+`VatPolicyResolver`(A-1 미확정 → 임시 NONE + WARN), 헬스체크에 부가세 정책 노출, 관리자 미리보기 API + 공개 가격 API, PRODUCT_PRICES 캐시 + 시세변경 이벤트 무효화. **요구사항 예시(210,000×3.75+50,000=837,500) 실 DB 검증, 테스트 109건 전원 통과**
- [x] **STEP 8 완료 (2026-08-26): 상품·카테고리·옵션 관리.** 상품 도메인 CRUD(`Product`·`Category`·`ProductOption`·`ProductImage` 엔티티에 생성/변경 메서드), `ProductPricing` 값 객체가 `price_type` 별 필수값 정합성(V3 `chk_price_type_fields`)을 400 으로 검증, **등록 시 기본 옵션 자동 생성**(D-7 응용 계층 해결), 상품은 `HIDDEN` 으로 시작하고 판매 시작 시 활성 옵션 존재 확인, 재고 실사 조정(절대 수량), 이미지 목록 교체, 카테고리 순환 참조·사용 중 삭제 차단, QueryDSL 동적 상품 검색, 공개 목록(대표가=최저 옵션가)/상세(옵션별 가격, 재고 비공개), 전 변경 지점 `@AuditLog`. **테스트 132건 전원 통과**
- [x] **관리자 화면 + V8 마이그레이션 완료 (2026-08-27).** ① 관리자 프론트엔드 전체 — 로그인(sessionStorage 토큰 + 401 재발급 재시도), 대시보드, 시세관리(현재시세 3단·수집상태·수동고시·승인대기·보정정책·이력), 상품관리(목록·등록·상세·옵션/재고·가격 미리보기), 카테고리 트리, 감사로그(before/after JSONB), 관리자 계정(SUPER_ADMIN), 내 계정. ② **V8 마이그레이션 — 잔여 결함 11건 전건 해소** (C-1 참조). 신규 7테이블(누적 40), 불변 트리거 4종 추가, 기존 데이터 백필 검증. **테스트 152건 전원 통과 (신규 `SchemaGuardIntegrationTest` 19건은 DB 가 실제로 거절하는지를 확인한다)**
- [x] **STEP 9 회원 인증 백엔드 완료 (2026-08-27).** OAuth2 3사(구글·네이버·카카오)를 `OAuthProvider` 인터페이스 뒤에 두고 제공자별 응답 차이(네이버 `response` 래핑·`resultcode`, 카카오 이메일 선택동의)를 구현체가 흡수. **약관 동의 전에는 계정을 만들지 않는다** — 신규 소셜 신원은 DB 가 아니라 서명된 `signUpToken` 에 담아 두고, 필수 약관(이용약관·개인정보·만14세) 동의가 끝나는 순간 회원·소셜연동·동의이력을 한 트랜잭션으로 생성한다. 회원 JWT(aud=user, 30분) + Refresh(무작위 32바이트, 14일, SHA-256 해시로 DB 저장, HttpOnly·SameSite=Lax 쿠키, 재발급 시 회전, **폐기 토큰 재사용 시 전 세션 폐기**). 이메일로 계정을 병합하지 않고 `(provider, provider_user_id)` 로만 조회(§7.2), 계정 통합은 마이페이지 수동 연동. 마이페이지(내 정보·약관 현황/철회·소셜 연동·탈퇴), V9 약관 버전 설정. **테스트 178건 전원 통과 (신규 26건)** — 실제 소셜 서버 없이 `StubOAuthProvider` 로 흐름 전체를 검증한다
- [x] **STEP 9 회원 인증 프론트엔드 완료 (2026-08-27).** `/login`(사용 가능한 제공자만 버튼으로 노출), `/auth/callback/[provider]`(state 대조 → 인가코드 교환 → 로그인 또는 약관 화면), `/signup`(약관 동의 후 가입 완료), `/mypage`(내 정보·약관 현황/철회·소셜 연동·탈퇴), 헤더 로그인 영역(`HeaderAuth`). **Access 토큰을 저장소에 두지 않는다** — 메모리에만 보관하고 새로고침 시 HttpOnly Refresh 쿠키로 복구(`SessionBootstrap`). 401 시 재발급 후 1회 재시도. lint/typecheck/build 통과
- [x] **STEP 10 장바구니(백엔드) 완료 (2026-08-27).** `GET/POST/PATCH/DELETE /api/v1/cart` — **담을 때 서버가 계산하고, 볼 때 다시 계산한다** (개발계획 §13.6). 프론트가 보내는 것은 상품·옵션·수량뿐이며 금액 필드는 어디서도 읽지 않는다. 담기 시점에 시세문의·판매중지·재고부족을 차단하고(S6), 담긴 뒤 상황이 바뀌면 조회 시 줄마다 `orderable`·`priceChanged`·`priceDelta`·`stockShortage`를 실어 보낸다. 합계에는 **주문 가능한 줄만** 포함한다. V10(`option_id` NOT NULL + `effective_price_id_at_add` + 인덱스), 상품당 50줄·수량 99개 상한. **테스트 198건 전원 통과 (신규 20건)**
- [x] **STEP 10 장바구니(프론트엔드) 완료 (2026-08-27).** `/cart` 화면, 헤더 장바구니 배지(`HeaderCartBadge`), 상품 상세 "장바구니 담기". **화면이 금액을 하나도 계산하지 않는다** — 단가·줄 합계·차액·총합이 전부 서버가 내려준 값이고, 수량을 바꾸면 서버가 다시 계산한 장바구니 전체를 받아 그대로 그린다. 담긴 뒤 주문 불가가 된 줄은 금액 자리에 "시세문의"가 들어가고(0원 아님) 합계에서 빠지며, 그 사실을 상단과 요약 양쪽에 알린다. 비회원이 담기를 누르면 `next` 를 실어 로그인으로 보낸다. 서버가 장바구니를 소유하므로 **로컬 `useCartStore` 는 삭제**했다. `authedClient` 로 401 재발급·재시도를 auth/cart 가 공유한다
- [x] **~~A-2 반올림 정책 미반영~~ 해소 (2026-08-27, V11).** A-2 는 2026-08-24 에 확정(CEIL_100)됐는데 V1 시드가 `TBD` 인 채로 남아 있었다. 새로 올린 환경은 설정을 찾지 못해 **임시 기본값(WON_FLOOR)으로 계산**하고 경고 로그 한 줄만 남기므로, 아무도 눈치채지 못한 채 확정 정책과 다른 금액이 고객에게 나갈 수 있었다. 장바구니 통합테스트가 "601,300 이어야 하는데 601,250 이 나온다"로 잡아냈다. V11 이 `TBD` 인 행만 `CEIL_100` 으로 바꾼다(운영자가 의도적으로 넣은 값은 덮지 않는다). 헬스체크의 `roundingPolicyTemporary` 가 이제 false 다
- [x] Docker 전면 미사용 확정 / QueryDSL 단독 채택(MyBatis 미도입) / 프론트엔드 Next.js(React) 확정

### C-2. 확정 스택 (설계서 v0.2 대비)

| 항목 | 설계서 v0.2 | 확정 | 비고 |
|---|---|---|---|
| Java | 21 | **21** | Temurin 21.0.12 를 `~/.jdks` 에 설치하고 Gradle 툴체인이 자동 탐지 |
| Spring Boot | 3.3 | **3.5.3** | 3.3 은 EOL. 3.x 계열 최신인 3.5.3 채택 |
| Gradle | Groovy DSL | **Kotlin DSL** (`build.gradle.kts`) | |
| QueryDSL | 5.x | **com.querydsl 5.1.0 (jakarta)** | Boot 3.5 / Hibernate 6.6 조합에서 정상 동작 |
| 캐시 | Caffeine | **Caffeine** | 설정·시세·상품가 3개 캐시. Redis 는 트래픽 발생 후 |

> **`foojay-resolver-convention` 플러그인은 쓰지 않는다.** Gradle 9.5 에서 벤더 열거값
> (`IBM_SEMERU`) 불일치로 기동 실패한다. JDK 21 을 직접 설치하고 Gradle 자동 탐지에 맡긴다.

### C-3. STEP 3 에서 내린 설계 결정

| 항목 | 결정 | 사유 |
|---|---|---|
| **에러 응답 포맷** | `{code, message, timestamp}` (+입력오류 시 `errors`). 성공 응답은 DTO 직접 반환 | STEP 3 지시 사항. **설계서 §6.1 의 `{success, data}` 봉투 방식과 다르다** — 프론트엔드 `api/client.ts` 를 이 계약에 맞춰 수정 완료. 설계서 §6.1 을 갱신할지 검토 필요 |
| **테스트 DB** | zonky embedded-postgres (실제 PostgreSQL 16 바이너리) | Docker 미사용 확정으로 Testcontainers 불가, 로컬 PostgreSQL 미설치. V1~V5 가 JSONB·plpgsql 트리거·부분 UNIQUE 인덱스를 쓰므로 H2 대체 불가 |
| **Swagger 차단 방식** | `application.yml` 에서 `springdoc.*.enabled: false` 기본값 + local/dev 에서만 활성 | `SwaggerConfig` 의 `@Profile` 만으로는 부족하다. springdoc 자동설정이 `/api-docs` 를 계속 서빙한다 (통합테스트로 확인) |
| **미확정 경로 응답** | `/api/v1/**` 는 존재하지 않는 경로도 404 가 아니라 **401** | 404/401 을 구분해 주면 미인증 사용자가 엔드포인트를 열거할 수 있다 |
| **관리자 부트스트랩** | 환경변수 없으면 생성 스킵 + WARN | 고정 계정을 코드·마이그레이션에 두지 않는다 |
| **반올림 미확정(A-2)** | 예외 대신 `WON_FLOOR`(원단위 절사) 임시 적용 + WARN 1회 | 정책 미확정으로 개발·검수가 멈추면 안 된다. 확정 시 `system_settings` 값만 변경하면 재배포 불필요 |
| **반올림 확정(A-2)** | `CEIL_100`(백원 단위 올림). 2026-08-24 `system_settings.PRICE_ROUNDING_MODE` 갱신 | 위 설계대로 재배포 없이 설정 변경만으로 반영됐다. **단 `V1__init.sql` 의 시드는 아직 `'TBD'`** — 새 DB 를 만들면 다시 임시값으로 뜬다. 시드 변경은 V8 에 포함할 것 |

### C-4. STEP 5 에서 내린 설계 결정

| 항목 | 결정 | 사유 |
|---|---|---|
| **토큰 세대(`token_version`)** | `admin_users` 에 컬럼을 두고 JWT 의 `tv` 클레임과 매 요청 대조. 로그아웃·비밀번호 변경·권한/상태 변경 시 +1 | 무상태 JWT 는 발급 후 회수 수단이 없다. 관리자 토큰은 탈취 시 피해가 커서 그 상태를 두지 않는다. 세션 테이블 없이 회수 가능성을 얻는다 |
| **필터에서 매 요청 DB 조회 1건** | 계정 상태(LOCKED/DISABLED)와 `token_version` 을 매번 확인 | 토큰만 믿으면 방금 잠근 계정이 최대 30분 더 일한다. 관리자 트래픽이 초당 수 건이라 이 비용이 문제되지 않는다. 규모가 커지면 그때 캐시를 앞에 둔다 |
| **로그인 실패 처리의 트랜잭션 분리** | `AdminLoginAttemptService.attempt()` 는 예외를 던지지 않고 결과만 반환, 예외 변환은 호출자가 트랜잭션 밖에서 | 실패 경로는 "DB 를 바꾸고 나서 실패"해야 한다. 한 트랜잭션으로 감싸면 실패 카운터가 롤백되어 **아무리 시도해도 계정이 잠기지 않는다** |
| **감사로그 `REQUIRES_NEW`** | `AuditLogService.record` 를 별도 트랜잭션으로 커밋 | 감사 대상 작업은 실패하며 롤백되는 경우가 많다. 같은 트랜잭션이면 가장 남겨야 할 사건의 기록만 사라진다 |
| **감사 실패가 업무를 막지 않음** | 기록 실패 시 ERROR 로그만 남기고 예외를 삼킴. SpEL 이 안 풀려도 값만 비우고 행은 남김 | 감사 테이블 장애로 관리자 로그인·주문 처리가 멈추는 편이 더 큰 사고다. 식 하나 때문에 감사 행 자체를 버리면 실패 사례가 통째로 사라진다 |
| **계정 잠금 + Rate Limit 병행** | 계정 5회 / IP 분당 10회 | 잠금만 있으면 계정마다 4회씩 훑는 공격이 통과하고, Rate Limit 만 있으면 한 계정 집중 추측이 통과한다. 대체재가 아니라 보완재다 |
| **Rate Limit 인메모리(Caffeine)** | Bucket4j + Caffeine, 단일 인스턴스 전제 | 설계서 §7.3 "Redis 는 트래픽 발생 후" 원칙. 다중화 시 인스턴스 수만큼 한도가 늘어나는 한계가 있고, 그 전까지는 Nginx IP 허용목록이 1차 방어선이다 |
| **관리자 계정 DELETE 없음** | 퇴사·권한 회수는 `status=DISABLED` | 계정을 지우면 그 계정이 남긴 감사로그의 주체가 사라진다 |
| **본인·마지막 SUPER_ADMIN 보호** | 본인 권한/상태 변경 금지, 마지막 활성 SUPER_ADMIN 강등·정지 금지 | 둘 다 시스템을 복구 불능으로 만든다. 권한 검사(@PreAuthorize)를 통과한 요청이라도 서비스 계층에서 막는다 |
| **본문 파싱 실패 → 400** | `HttpMessageNotReadableException` 핸들러 추가 | 깨진 JSON 이 500 으로 나가면 클라이언트 잘못이 서버 ERROR 로그를 채우고, 진짜 장애가 그 사이에 묻힌다 |

### C-5. STEP 6 에서 내린 설계 결정

| 항목 | 결정 | 사유 |
|---|---|---|
| **보류(pending)를 예외로 던지지 않음** | `PriceApplyResult.applied=false` + HTTP 200 | 변동률 초과 보류는 오류가 아니라 **설계된 정상 경로**다. 예외로 던지면 관리자 화면에 "승인이 필요합니다"가 아니라 "오류가 발생했습니다"가 뜬다 |
| **마감 UPDATE 를 명시적으로 flush** | 기존 행 `close()` 후 `saveAndFlush`, 그다음 신규 INSERT | JPA 는 INSERT 를 UPDATE보다 앞세운다. flush 가 없으면 신규 행(`effective_to IS NULL`)이 먼저 들어가 부분 UNIQUE 인덱스 `ux_mpe_current` 를 위반한다 |
| **advisory lock (metal×side)** | `pg_advisory_xact_lock(hashtext(key))` | 최초 발행 시에는 **잠글 행 자체가 없어** 행 잠금으로는 동시성을 막을 수 없다. 행이 아니라 키를 잠근다. 부분 UNIQUE 인덱스가 최종 안전망이라 락이 없어도 데이터는 깨지지 않고 예외로 실패한다 |
| **승인 시 변동률 재검사 안 함** | 보류 당시 `calc_price` 를 그대로 발행 | 임계값 초과를 알고도 승인한 것이라 다시 막으면 승인이 무의미하다. 재계산도 하지 않는다 — 승인 시점에 정책이 바뀌어 있으면 화면에서 본 값과 다른 값이 발행된다 |
| **MANUAL 인데 `manual_price` 가 NULL** | 원본을 그대로 최종가로 적용 + 조합당 1회 WARN | V2 가 전 금속×side 를 `MANUAL`+NULL 로 시드한다. 예외로 처리하면 **수동 고시(주력 경로)가 첫 시도부터 실패**한다. 지정가가 없다는 것은 오버라이드할 것이 없다는 뜻이다 |
| **수집 실패는 원본을 만들지 않음** | `market_price_fetch_logs` 에만 기록 | 원본이 없으면 발행도 없고, 발행이 없으면 기존 시세가 유지된다. "실패가 가격을 0원으로 만들지 않는다"의 구조적 근거 |
| **재시도 대상 구분** | HTTP_ERROR / TIMEOUT / EXCEPTION 만 3회, PARSE_ERROR·INVALID_VALUE 는 1회 | 값이 이상하거나 파싱이 안 되는 것은 다시 불러도 똑같다. 로그만 3배로 늘고 API 쿼터를 태운다 |
| **쓰기를 별도 빈으로 분리** | `MarketPriceCollectionWriter` | `REQUIRES_NEW` 를 같은 클래스 안에 두면 자기 호출이라 **프록시를 타지 않아 어노테이션이 조용히 무시된다.** 장애 전까지 드러나지 않는 결함이라 빈을 나눠 구조적으로 막았다 |
| **감사로그를 이벤트 리스너가 아니라 서비스에서** | 리스너는 캐시 무효화만 | §12.7 은 리스너에서 감사도 기록하도록 그렸으나, 행위자·사유·보류 여부 맥락이 서비스에 있다. **보류로 끝난 경우는 이벤트 자체가 발행되지 않아** 리스너에서는 기록할 수 없다 |
| **스케줄러 cron 을 DB 에서 매 실행 재조회** | `SchedulingConfigurer` + 동적 `CronTrigger` | `@Scheduled(cron=상수)` 는 주기 변경이 재배포다. 잘못된 cron 이면 기본값으로 되돌리고 WARN — cron 오타 하나로 수집이 영영 멈추는 것이 더 큰 사고다 |
| **`isDeclaredByAdmin()` 이 `created_by` 를 봄** | `source.getCode()` 대신 | `source` 는 지연로딩이라 트랜잭션 밖에서 부르면 `LazyInitializationException` 이다. 판정 하나 때문에 응답 전체가 500 이 되는 함정 (통합테스트가 실제로 잡았다) |
| **공개 API 는 노후 시세를 내보내지 않음** | 관리자 API 는 노후 값도 노출 | 관리자는 "무엇이 언제부터 낡았는지"를 봐야 하고, 고객은 화면에 뜬 가격으로 주문하려 한다 |

### C-6. STEP 7 에서 내린 설계 결정

| 항목 | 결정 | 사유 |
|---|---|---|
| **부가세 미확정(A-1)** | 예외 대신 임시 `NONE`(면세) + WARN 1회 | A-2 반올림과 같은 처리다. 세 후보 중 **최종 금액을 바꾸지 않는 유일한 값**이라 골랐다 — `EXCLUDED` 면 구성요소 합계보다 10% 비싼 가격이, `INCLUDED` 면 면세 상품에 세액이 표시된다 |
| **SIMPLE 도 구성요소로 정규화** | `price_type` 컬럼 → `ComponentLine` 목록으로 변환 후 계산 | 개발계획 §13.1. 주얼리용 `product_price_components`(D-1)가 들어와도 **불러오는 곳만 바뀌고 계산·Snapshot·테스트는 그대로**다 |
| **구성요소 합계 = 소계 (불변식)** | 각 줄을 전(錢) 단위 `Money` 로 확정한 뒤 더한다 | 개발계획 §13.5 는 `MathContext.DECIMAL64` 누적을 그렸으나, 그러면 줄 합계와 소계가 1원씩 어긋날 수 있다. **주문 명세서의 항목 합이 청구액과 달라지는 것**이 더 큰 문제라 줄 단위 확정을 택했다 (DB 는 어차피 `NUMERIC(15,2)`) |
| **반올림은 마지막 1회** | 소계·세액에는 적용하지 않고 합계에만 | 단계마다 반올림하면 구성요소 합계와 최종가가 어긋난다. `roundingDelta` 로 조정액을 노출해 검증 가능하게 했다 |
| **배수를 별도 줄로 분리** | `MARKET_MULTIPLIER` 를 `METAL_BASE` + `MARGIN(×배수-1)` 로 | METAL_BASE 에 배수를 곱해 넣으면 상세페이지에 "210,000 × 3.75 = 905,625" 처럼 **계산이 맞지 않는 줄**이 표시된다 |
| **시세 없음/노후는 예외 아님** | `PriceStatus.INQUIRY_ONLY` 반환, 금액 필드는 `null` | 예외로 던지면 목록에 시세 없는 상품 하나가 섞였을 때 **목록 전체가 500** 이 된다. 0 을 돌려주지 않는 이유는 화면에 "0원"으로 그려지기 때문 |
| **관리자 미리보기는 캐시·노출검사 제외** | HIDDEN 상품도 계산, 캐시 미사용 | 없으면 운영자가 가격 확인을 위해 **일단 공개했다가 되돌리는** 절차를 쓰게 된다 |
| **옵션 추가공임은 MARKET_PLUS 만** | 다른 타입이면 무시하되 WARN | 설계서 §7.1 그대로다. 조용히 무시하면 "왜 반영이 안 되지"로 시간이 샌다 |
| **대표가는 최저가 옵션** | 목록 화면의 "○○원~" | 옵션 미지정 조회에서는 재고를 보지 않는다 — 어떤 옵션이든 하나라도 살 수 있으면 목록에서 "품절"로 표시하면 안 된다 |
| **`price_calculation_logs` 미구현** | STEP 13(주문)로 미룸 | §13.7 은 `ORDER` / `ADMIN_PREVIEW` 전건 저장을 요구한다. 주문이 없는 지금은 `ADMIN_PREVIEW` 뿐이라 감사 가치가 낮고, 테이블 신설(마이그레이션)을 정당화하지 못한다 |

### C-7. STEP 8 에서 내린 설계 결정

| 항목 | 결정 | 사유 |
|---|---|---|
| **상품 DELETE 없음** | 판매 종료는 `status=DISCONTINUED` | 상품을 지우면 그 상품을 담은 **과거 주문의 상품 정보가 사라진다**. 관리자 계정과 같은 원칙 |
| **등록 직후 `HIDDEN`** | 관리자가 확인 후 `PATCH /{id}/status` 로 판매 시작 | 등록 시점에는 재고·이미지가 없고 시세 연동이면 가격도 미확인이다. 그대로 노출되면 고객이 **재고 없는 상품을 주문**하거나 잘못된 가격을 본다 |
| **기본 옵션 자동 생성 (D-7)** | 옵션 없이 등록하면 서버가 1개를 만든다. 마지막 활성 옵션은 비활성화 불가 | "옵션 없는 상품" 개념을 없애면 재고 소스가 `product_options.stock` 한 곳뿐이고, 주문·취소·환불의 재고 경로가 분기 없이 단일해진다 (개발계획 §9.3). **단 `is_default` 컬럼과 부분 UNIQUE 인덱스는 V8 대기** — 지금은 응용 계층 방어다 |
| **`ProductPricing` 값 객체** | `price_type` 별 필수값을 한 곳에서 검증 | 흩어 두면 서비스·컨트롤러·엔티티 중 한 곳만 빠뜨려도 통과한다. DB 제약(`chk_price_type_fields`)만 믿으면 관리자 입력 오류가 **500 으로 나간다** |
| **재고 조정은 절대 수량** | 증감이 아니라 "조정 후 수량" | 실사는 "세어 보니 7개였다"이지 "3개 줄여라"가 아니다. 증감이면 요청이 중복 도착할 때 재고가 두 번 깎인다 |
| **재고 조정과 주문 차감 분리** | `adjustStock` ≠ (STEP 13) `decreaseStock` | 같은 메서드면 실사 입력이 주문 차감과 경합하고, 감사로그에서도 둘을 구분할 수 없다 |
| **노출 조건을 서버가 강제** | 공개 검색은 `condition.forPublic()` 으로 `status`·`active` 를 덮어쓴다 | 클라이언트가 끌 수 있으면 **파라미터 하나로 숨김 상품을 열람**할 수 있다 |
| **숨김 상품 상세는 403 이 아니라 404** | 존재 자체를 알리지 않는다 | 403 이면 "여기 뭔가 있다"가 새어 나가 상품 ID 를 훑어 미공개 상품 목록을 만들 수 있다 |
| **공개 응답에 재고 수량 없음** | `inStock`(불리언)만 노출 | 정확한 재고는 영업 정보다. 고객이 알아야 할 것은 "살 수 있는가" 뿐 |
| **가격 산출 실패가 목록을 죽이지 않음** | 상품별로 잡아 "시세문의"로 그리고 WARN | 상품 하나의 설정 오류(중량 누락 등)로 **목록 전체가 500** 이 되면 안 된다 |
| **카테고리 순환 참조 차단** | 자기 자신·하위를 부모로 지정 불가, 깊이 32 가드 | 트리가 고리가 되는 순간 카테고리를 순회하는 모든 코드가 **무한 루프**에 빠진다. 화면이 멎을 때까지 아무도 눈치채지 못한다 |
| **사용 중 카테고리 삭제 차단** | 상품·하위가 있으면 409, `active=false` 로 숨김 | 지우면 그 상품들이 **존재하지 않는 분류**를 가리키고 목록에서 조용히 사라진다 |
| **이미지는 목록 통째 교체** | 개별 추가·삭제 대신 `PUT /{id}/images` | 순서가 의미를 갖는 목록이라, 개별 조작은 여러 요청으로 나뉘며 중간 상태가 화면에 노출된다 |

### ~~C-1. V8에서 해소할 스키마 결함~~ — ✅ 전건 해소 (2026-08-27)

> **번호 변경 이력**
> - 2026-08-24: STEP 5(관리자 인증)가 `admin_users` 에 실패횟수·잠금시각·토큰세대 컬럼을
>   먼저 필요로 해서 **V6 = `V6__admin_auth.sql`** 이 가져갔다.
> - 2026-08-26: STEP 6 이 수집 실패를 기록할 곳(D-3)을 먼저 필요로 해서
>   **V7 = `V7__market_price_fetch_logs.sql`** 이 가져갔다.
> - 2026-08-27: 남은 11건을 **V8 = `V8__remaining_defects.sql`** 이 한 번에 해소했다.
>   주문·결제·회원 코드보다 먼저 넣은 이유는 D-2·D-6·D-9 가 <b>나중에 붙일 수 없는</b>
>   항목이기 때문이다 — 주문이 쌓인 뒤 구성요소 스냅샷을 추가하면 그 주문은 내역을
>   복원할 수 없고, 멱등키 없이 받은 콜백은 중복 여부를 사후 판정할 수 없으며,
>   약관 동의 시각은 소급 생성 자체가 위법이다.

| # | 등급 | 문제 | 해소 방식 |
|---|---|---|---|
| ~~D-1~~ | ~~치명~~ | ~~주얼리 "부자재·스톤" 확장 불가~~ | **✅ V8** — `product_price_components` 신설 + `chk_ppc_method` 로 산출식별 필수값 강제. `products.pricing_model`(SIMPLE/COMPONENT) 추가 |
| ~~D-2~~ | ~~치명~~ | ~~주문에 구성요소별 금액 Snapshot 없음~~ | **✅ V8** — `order_item_price_components` 신설, `order_item_adjustments`(취소·환불은 원본 수정 대신 조정 행), `order_items`/구성요소 **UPDATE·DELETE 차단 트리거** |
| ~~D-3~~ | ~~치명~~ | ~~`market_price_raw`가 수집 실패를 기록 못함~~ | **✅ V7** — `market_price_fetch_logs` 신설. V8 이 `status` 에서 `FAILED` 제거를 마무리 |
| ~~D-4~~ | ~~치명~~ | ~~관리자 보정 정책의 변경 이력 없음~~ | **✅ V8** — `market_price_policy_history` 신설 (append-only 트리거) |
| ~~D-5~~ | ~~높음~~ | ~~MANUAL 고시가 원본 레이어를 건너뜀~~ | **✅ STEP 6(코드) + V8(스키마)** — 수동 고시도 `raw` INSERT 를 거치고, effective 에 원본·보정 스냅샷 컬럼이 생겼다 |
| ~~D-6~~ | ~~높음~~ | ~~PG 콜백 멱등성 키 없음~~ | **✅ V8** — `payment_transactions.idempotency_key` + `ux_ptx_idem`, `ux_pay_tid_approved`(같은 TID 로 승인 2건 불가) |
| ~~D-7~~ | ~~높음~~ | ~~옵션 없는 상품의 재고 필드 부재~~ | **✅ STEP 8(응용) + V8(DB)** — `is_default` 컬럼 + `ux_opt_default` 부분 UNIQUE. 기존 상품 7건은 정렬 최상단 옵션으로 백필 |
| ~~D-8~~ | ~~높음~~ | ~~`order_items`에 `option_id` 미보관~~ | **✅ V8** — `option_id` 외 Snapshot 확정 필드 14개 추가 |
| ~~D-9~~ | ~~높음~~ | ~~약관 동의 이력 테이블 부재 (법적 필수)~~ | **✅ V8** — `user_agreements` 신설. 철회도 `agreed=false` 행 추가이며 UPDATE 를 트리거가 막는다 |
| ~~D-10~~ | ~~중간~~ | ~~`max_change_percent` 컬럼 누락~~ | **✅ V8** — 금속별 임계값 컬럼(NULL=전역값). `derive_from`/`derive_ratio`/`auto_apply`/`active` 도 함께 |
| ~~D-11~~ | ~~중간~~ | ~~effective에 원본가·보정액 없음~~ | **✅ V8** — `raw_price_per_gram`·`adjustment_amount`·`source_code`·`quote_at`·`pending_id` 추가. 기존 22행 백필(원본+보정=최종 전건 일치), 가드 트리거가 신규 컬럼까지 보호 |
| ~~D-12~~ | ~~중간~~ | ~~시세 원본 중복 적재 방지 제약 없음~~ | **✅ V8** — `quote_at`(기준시각) 분리 + `ux_mpr_dedup(source_id, metal_type, quote_at)` |

**검증**: `SchemaGuardIntegrationTest` 19건이 애플리케이션이 아니라 **DB 가 실제로 거절하는지**를
확인한다 (주문 UPDATE 거부, 멱등키 중복 거부, 기본 옵션 2개 거부, 동의 철회 UPDATE 거부 등).
전체 테스트 152건 통과. 개발 DB 적용 후 40테이블·schemaVersion=8 확인.

**V8 에 함께 들어갔으나 아직 코드가 쓰지 않는 것** (스키마만 준비된 상태):
- `products.slug` — SEO URL. 한글 상품명 자동 생성은 의미가 뭉개져 비워 뒀다. 운영에서 지정한다
- `market_price_policies.mode = 'DERIVED'` — 파생 시세(18K/14K를 24K에서 산출). **A-7 미확정**이라
  계산 로직이 없다. API 는 enum 검증이 있어 이 값을 만들 수 없다
- `price_calculation_logs` / `order_status_histories` — 주문·결제 STEP 에서 쓰기 시작한다

## D. 오픈 후 과제 (Phase 2 이후)

- ERP 구축 시 연동: 시세 일원화, 주문 전송, 재고 연동
- 온라인 매입 접수 (택배 매입)
- Redis 캐시 (트래픽 발생 시)
- 실시간 유료 시세 벤더 검토
