package com.hanseongge.global.audit;

/**
 * 감사로그 행위 코드 사전.
 *
 * <p>문자열을 호출부에 흩어 두면 오타가 나도 컴파일이 통과하고, 그 오타는 검색으로
 * 찾을 수 없는 감사로그를 만든다. {@link com.hanseongge.global.error.ErrorCode} 와
 * 같은 이유로 한곳에 모은다.
 *
 * <p>{@code audit_logs.action} 은 60자다. 새 코드를 추가할 때 길이를 넘기지 않는다.
 */
public final class AuditActions {

    // --- 인증 ---
    public static final String ADMIN_LOGIN = "ADMIN_LOGIN";
    public static final String ADMIN_LOGIN_FAILED = "ADMIN_LOGIN_FAILED";
    /** 잠기거나 비활성화된 계정으로 로그인을 시도함. */
    public static final String ADMIN_LOGIN_BLOCKED = "ADMIN_LOGIN_BLOCKED";
    /** 연속 실패 임계치 도달로 자동 잠김. */
    public static final String ADMIN_ACCOUNT_AUTO_LOCKED = "ADMIN_ACCOUNT_AUTO_LOCKED";
    public static final String ADMIN_LOGOUT = "ADMIN_LOGOUT";
    public static final String ADMIN_TOKEN_REFRESH = "ADMIN_TOKEN_REFRESH";

    // --- 관리자 계정 관리 ---
    public static final String ADMIN_CREATE = "ADMIN_CREATE";
    public static final String ADMIN_UPDATE = "ADMIN_UPDATE";
    public static final String ADMIN_STATUS_CHANGE = "ADMIN_STATUS_CHANGE";
    public static final String ADMIN_PASSWORD_CHANGE = "ADMIN_PASSWORD_CHANGE";
    public static final String ADMIN_PASSWORD_RESET = "ADMIN_PASSWORD_RESET";

    // --- 시세 (STEP 6) ---
    /** 최종 시세 발행 */
    public static final String PRICE_APPLY = "PRICE_APPLY";
    /** 변동률 초과로 발행하지 않고 승인 대기로 돌림 */
    public static final String PRICE_APPLY_HELD = "PRICE_APPLY_HELD";
    public static final String PRICE_POLICY_UPDATE = "PRICE_POLICY_UPDATE";
    public static final String PRICE_PENDING_APPROVE = "PRICE_PENDING_APPROVE";
    public static final String PRICE_PENDING_REJECT = "PRICE_PENDING_REJECT";

    // --- 상품 (STEP 8) ---
    public static final String CATEGORY_CREATE = "CATEGORY_CREATE";
    public static final String CATEGORY_UPDATE = "CATEGORY_UPDATE";
    public static final String CATEGORY_DELETE = "CATEGORY_DELETE";
    public static final String PRODUCT_CREATE = "PRODUCT_CREATE";
    public static final String PRODUCT_UPDATE = "PRODUCT_UPDATE";
    public static final String PRODUCT_STATUS_CHANGE = "PRODUCT_STATUS_CHANGE";
    public static final String PRODUCT_OPTION_CREATE = "PRODUCT_OPTION_CREATE";
    public static final String PRODUCT_OPTION_UPDATE = "PRODUCT_OPTION_UPDATE";
    /** 재고 실사 반영. 주문에 의한 차감은 별도 행위다 */
    public static final String PRODUCT_STOCK_ADJUST = "PRODUCT_STOCK_ADJUST";
    public static final String PRODUCT_IMAGE_CHANGE = "PRODUCT_IMAGE_CHANGE";

    // --- 게시판 (STEP 15~16) ---
    public static final String NOTICE_CREATE = "NOTICE_CREATE";
    public static final String NOTICE_UPDATE = "NOTICE_UPDATE";
    /** 공지를 내림. 행을 지우지 않으므로 DELETE 가 아니다 */
    public static final String NOTICE_HIDE = "NOTICE_HIDE";
    public static final String EVENT_CREATE = "EVENT_CREATE";
    public static final String EVENT_UPDATE = "EVENT_UPDATE";
    public static final String EVENT_HIDE = "EVENT_HIDE";
    /** 관리자가 고객 문의에 답변함 */
    public static final String QNA_ANSWER = "QNA_ANSWER";
    /** 관리자가 문의글을 내림 (욕설·개인정보 노출 등) */
    public static final String QNA_HIDE = "QNA_HIDE";

    // --- 대상 유형 ---
    public static final String TARGET_ADMIN_USER = "ADMIN_USER";
    public static final String TARGET_NOTICE = "NOTICE";
    public static final String TARGET_EVENT = "EVENT";
    public static final String TARGET_QNA = "QNA";
    public static final String TARGET_CATEGORY = "CATEGORY";
    public static final String TARGET_PRODUCT = "PRODUCT";
    public static final String TARGET_PRODUCT_OPTION = "PRODUCT_OPTION";
    /** 시세 대상 식별자는 {@code GOLD_24K/SELL} 형태의 metal×side 조합이다. */
    public static final String TARGET_MARKET_PRICE = "MARKET_PRICE";
    /** 존재 여부가 확인되지 않은, 사용자가 입력한 아이디. */
    public static final String TARGET_LOGIN_ID = "LOGIN_ID";

    private AuditActions() {
    }
}
