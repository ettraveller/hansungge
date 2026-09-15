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
    PRICE_CHANGE_NEEDS_CONFIRM(HttpStatus.CONFLICT, "직전 고시보다 크게 바뀌는 값이 있습니다. 바뀌는 값을 확인한 뒤 다시 고시해 주세요."),
    PRICE_SPREAD_INVERTED(HttpStatus.BAD_REQUEST, "살 때 시세가 팔 때 시세보다 커야 합니다. 두 값이 바뀌지 않았는지 확인해 주세요."),
    PRICE_METAL_DERIVED(HttpStatus.CONFLICT, "24K 환산으로 설정된 금속은 직접 고시할 수 없습니다. 환산 설정에서 '직접 입력'으로 바꿔 주세요."),
    PRICE_APPLY_CONFLICT(HttpStatus.CONFLICT, "다른 작업이 시세를 변경 중입니다. 다시 시도해 주세요."),
    PRICE_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "시세 설정을 찾을 수 없습니다."),
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
    /** 마지막 로그인 수단을 끊으면 들어올 방법이 사라진다. 비밀번호가 있으면 소셜은 끊을 수 있다. */
    SOCIAL_LAST_CONNECTION(HttpStatus.CONFLICT, "마지막 로그인 수단은 해제할 수 없습니다."),

    // --- 자체 가입 · 본인확인 (STEP 9-2) ---
    /** 계약·설정 전이라 본인확인을 쓸 수 없다. 404 가 아닌 이유는 없는 것이 아니라 준비되지 않은 것이기 때문. */
    IDENTITY_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "현재 본인확인을 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    /** 공급자 응답 검증 실패. 상세 사유는 로그에만 남긴다. */
    IDENTITY_VERIFICATION_FAILED(HttpStatus.BAD_GATEWAY, "본인확인에 실패했습니다. 다시 시도해 주세요."),
    /** 만료됐거나 이미 가입에 쓴 본인확인. 한 번 쓴 인증은 재사용할 수 없다. */
    IDENTITY_VERIFICATION_INVALID(HttpStatus.BAD_REQUEST, "본인확인 정보가 만료되었습니다. 처음부터 다시 진행해 주세요."),
    /**
     * 만 14세 미만.
     *
     * <p>개인정보보호법 §22조의2 는 법정대리인 동의를 받으면 수집할 수 있다고 하지만,
     * 그 절차를 만들지 않았으므로 가입 자체를 막는다. 절차 없이 받아 두면 그 시점부터 위법이다.
     */
    UNDER_MINIMUM_AGE(HttpStatus.FORBIDDEN, "만 14세 미만은 회원가입을 할 수 없습니다."),
    /** 같은 DI 로 이미 가입한 계정이 있다. 병합할 수 없는 경우에만 쓴다. */
    IDENTITY_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 회원입니다. 로그인해 주세요."),
    /** 이메일은 같은데 본인확인 결과가 다르다 — 남의 이메일로 가입하려는 시도일 수 있다. */
    EMAIL_OWNED_BY_ANOTHER(HttpStatus.CONFLICT, "이 이메일은 다른 분이 사용 중입니다. 고객센터로 문의해 주세요."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    /** 코드 불일치·만료. 어느 쪽인지 알려주지 않는다 — 알려주면 유효한 코드를 찾는 데 쓰인다. */
    EMAIL_VERIFICATION_INVALID(HttpStatus.BAD_REQUEST, "인증번호가 올바르지 않거나 만료되었습니다."),
    /** 시도 횟수 초과. 6자리 숫자는 막지 않으면 만료 전에 다 훑을 수 있다. */
    EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "인증 시도 횟수를 초과했습니다. 인증번호를 다시 받아 주세요."),
    /** 메일 발송 실패. 설정 누락이거나 공급자 장애다. */
    MAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY, "인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요."),
    /** 소셜로만 가입한 계정에 비밀번호 로그인을 시도했다. */
    PASSWORD_NOT_SET(HttpStatus.CONFLICT, "비밀번호가 설정되지 않은 계정입니다. 소셜 로그인으로 들어와 주세요."),
    /** 연속 실패로 잠긴 계정. 관리자와 달리 시간이 지나면 스스로 풀린다. */
    USER_ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "로그인 시도가 많아 계정이 잠겼습니다. 30분 후 다시 시도해 주세요."),
    /** 비밀번호 재설정 링크가 만료됐거나 이미 사용됐다. */
    PASSWORD_RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "비밀번호 재설정 링크가 만료되었습니다. 다시 요청해 주세요."),

    // --- 게시판 (STEP 15~16) ---
    /** 내린 공지도 이 코드로 응답한다 — 존재 자체를 알리지 않는다. */
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다."),
    /** 비밀글을 남이 열었을 때도 이 코드다. 403 이면 "여기 글이 있다"가 새어 나간다. */
    QNA_NOT_FOUND(HttpStatus.NOT_FOUND, "문의글을 찾을 수 없습니다."),
    /** 답변이 달린 뒤 고객이 질문을 고치면 답변이 엉뚱한 질문에 붙는다. */
    QNA_ALREADY_ANSWERED(HttpStatus.CONFLICT, "답변이 등록된 문의는 수정하거나 삭제할 수 없습니다."),
    /** 기간이 지났거나 아직 시작하지 않은 이벤트. */
    EVENT_NOT_OPEN(HttpStatus.CONFLICT, "응모 기간이 아닙니다."),
    EVENT_ALREADY_ENTERED(HttpStatus.CONFLICT, "이미 응모하셨습니다."),

    // --- 회원 관리 (관리자) ---
    /**
     * 탈퇴는 되돌리지 않는다.
     *
     * <p>탈퇴는 본인이 한 의사표시이고, 관리자가 계정을 되살리는 것은 <b>본인 동의 없이
     * 개인정보 처리를 재개</b>하는 일이다. 같은 사람이 다시 쓰려면 다시 가입하면 된다 —
     * 이메일·DI 의 UNIQUE 인덱스가 탈퇴 행을 제외하므로 막히지 않는다.
     */
    MEMBER_WITHDRAWN_IMMUTABLE(HttpStatus.CONFLICT, "탈퇴한 회원의 상태는 변경할 수 없습니다."),
    /** 관리자가 회원을 탈퇴시키지 않는다. 탈퇴는 본인만 할 수 있다. */
    MEMBER_WITHDRAW_DENIED(HttpStatus.BAD_REQUEST, "탈퇴 처리는 회원 본인만 할 수 있습니다.");

    private final HttpStatus status;
    private final String message;
}
