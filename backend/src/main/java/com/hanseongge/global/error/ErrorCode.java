package com.hanseongge.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 도메인 오류 코드 사전.
 *
 * <p>코드 문자열은 프론트엔드가 분기에 사용하는 계약이다. 한번 배포한 코드는 바꾸지 않는다.
 * 메시지는 사용자 노출 문구이므로 시스템 내부 사정(스택트레이스, 테이블명)을 담지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // --- 공통 ---
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청하신 정보를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),

    // --- 설정 (STEP 3) ---
    SETTING_NOT_FOUND(HttpStatus.NOT_FOUND, "설정값을 찾을 수 없습니다."),

    // --- 시세 (STEP 7) ---
    PRICE_UNAVAILABLE(HttpStatus.CONFLICT, "현재 시세를 확인할 수 없습니다. 시세문의 후 주문해 주세요."),
    PRICE_STALE(HttpStatus.CONFLICT, "시세 정보가 오래되었습니다. 잠시 후 다시 시도해 주세요."),
    PRICE_CHANGE_EXCEEDS_LIMIT(HttpStatus.CONFLICT, "허용 변동폭을 초과했습니다. 관리자 승인이 필요합니다."),
    PRICE_APPLY_CONFLICT(HttpStatus.CONFLICT, "다른 작업이 시세를 변경 중입니다. 다시 시도해 주세요."),
    PRICE_RAW_NOT_FOUND(HttpStatus.NOT_FOUND, "원본 시세를 찾을 수 없습니다."),
    PRICE_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "보정 정책을 찾을 수 없습니다."),
    PRICE_PENDING_NOT_FOUND(HttpStatus.NOT_FOUND, "승인 대기 건을 찾을 수 없습니다."),
    PRICE_PENDING_ALREADY_DECIDED(HttpStatus.CONFLICT, "이미 처리된 승인 건입니다."),
    /** 0 이하·범위 이탈·단위(돈↔g) 오입력 의심. 상세 사유는 로그와 수집 이력에만 남긴다. */
    PRICE_INVALID_VALUE(HttpStatus.BAD_REQUEST, "입력한 시세가 정상 범위를 벗어났습니다. 값과 단위를 확인해 주세요."),

    // --- 상품/가격 (STEP 9~10) ---
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    /** 상품이 남아 있는 카테고리를 지우면 그 상품들이 존재하지 않는 분류를 가리키게 된다. */
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "상품이 등록된 카테고리는 삭제할 수 없습니다."),
    CATEGORY_HAS_CHILDREN(HttpStatus.CONFLICT, "하위 카테고리가 있는 카테고리는 삭제할 수 없습니다."),
    CATEGORY_CYCLE(HttpStatus.CONFLICT, "카테고리를 자기 자신이나 하위 카테고리의 하위로 옮길 수 없습니다."),
    /** 재고가 옵션에만 존재한다는 불변식을 지키기 위해, 마지막 옵션은 비활성화할 수 없다. */
    PRODUCT_LAST_OPTION(HttpStatus.CONFLICT, "상품에는 활성 옵션이 최소 1개 필요합니다."),
    PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "판매 중인 상품이 아닙니다."),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    PRICE_CALCULATION_FAILED(HttpStatus.CONFLICT, "가격을 계산할 수 없습니다."),

    // --- 주문/결제 (STEP 13~14) ---
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
    ORDER_STATUS_INVALID(HttpStatus.CONFLICT, "현재 주문 상태에서는 처리할 수 없습니다."),
    PRICE_CHANGED(HttpStatus.CONFLICT, "시세가 변경되어 금액이 달라졌습니다. 확인 후 다시 주문해 주세요."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "결제 금액이 주문 금액과 일치하지 않습니다."),
    PAYMENT_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "결제 검증에 실패했습니다."),
    PAYMENT_DUPLICATE(HttpStatus.CONFLICT, "이미 처리된 결제입니다."),

    // --- 인증 (STEP 4~5) ---
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."),
    ADMIN_ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "계정이 잠겼습니다. 관리자에게 문의하세요."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),

    // --- 관리자 계정 관리 (STEP 5) ---
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "관리자 계정을 찾을 수 없습니다."),
    ADMIN_LOGIN_ID_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
    ADMIN_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    /** 자기 자신의 권한·상태를 바꾸면 스스로를 잠가 복구 불가 상태를 만들 수 있다. */
    ADMIN_SELF_MODIFICATION_DENIED(HttpStatus.CONFLICT, "본인 계정에는 수행할 수 없는 작업입니다."),
    /** 마지막 SUPER_ADMIN 을 잠그면 아무도 관리자 계정을 만들 수 없게 된다. */
    ADMIN_LAST_SUPER_ADMIN(HttpStatus.CONFLICT, "마지막 최고관리자 계정은 변경할 수 없습니다."),

    // --- 회원 인증 (STEP 9) ---
    /** 클라이언트 ID/시크릿이 주입되지 않아 그 소셜 로그인을 쓸 수 없다 (TODO A-4 검수 대기). */
    OAUTH_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "현재 이 방식의 로그인을 사용할 수 없습니다."),
    /** 인가코드 교환 또는 사용자 정보 조회 실패. 상세 사유는 로그에만 남긴다. */
    OAUTH_EXCHANGE_FAILED(HttpStatus.BAD_GATEWAY, "소셜 로그인 처리에 실패했습니다. 다시 시도해 주세요."),
    /** state 불일치·만료. 인가코드 주입(CSRF) 시도일 수 있다. */
    OAUTH_STATE_INVALID(HttpStatus.BAD_REQUEST, "로그인 요청이 유효하지 않습니다. 처음부터 다시 시도해 주세요."),
    /** 가입 진행 토큰이 만료됐거나 위조됨. 약관 화면에 오래 머문 경우가 대부분이다. */
    SIGNUP_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "가입 진행 시간이 만료되었습니다. 다시 로그인해 주세요."),
    /** 필수 약관 미동의. 어떤 약관이 빠졌는지는 errors 필드에 담는다. */
    AGREEMENT_REQUIRED(HttpStatus.BAD_REQUEST, "필수 약관에 동의해야 가입할 수 있습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    /** 탈퇴·정지 계정. 두 경우를 한 코드로 묶지 않는다 — 안내 문구가 달라야 한다. */
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴한 계정입니다."),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "이용이 제한된 계정입니다. 고객센터로 문의해 주세요."),
    /** 이미 다른 계정에 연결된 소셜 계정 (설계서 §7.2 — 이메일 자동 병합은 하지 않는다). */
    SOCIAL_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 다른 계정에 연결된 소셜 계정입니다."),
    /** 마지막 소셜 연동을 끊으면 로그인할 방법이 사라진다. */
    SOCIAL_LAST_CONNECTION(HttpStatus.CONFLICT, "마지막 소셜 연동은 해제할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
