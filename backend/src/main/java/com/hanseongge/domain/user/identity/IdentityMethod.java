package com.hanseongge.domain.user.identity;

import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;

/**
 * 본인확인 방식.
 *
 * <h2>둘은 다른 것이다</h2>
 * <ul>
 *   <li>{@link #SIMPLE} <b>간편인증</b> — PASS·토스·카카오 같은 <b>앱</b>으로 확인한다. 빠르지만
 *       그 앱이 깔려 있어야 한다.
 *   <li>{@link #PHONE} <b>휴대폰 본인확인</b> — 통신사가 보낸 <b>문자</b>의 번호를 입력한다.
 *       앱이 없어도 되고, 휴대폰이 있으면 누구나 할 수 있다.
 * </ul>
 *
 * <p>앱이 없는 분을 간편인증 창 앞에 세워 두면 거기서 가입이 끝난다. 그래서 고르게 한다.
 *
 * <p>둘 다 CI 를 주므로 가입 뒤의 처리는 똑같다 — 어느 쪽으로 왔는지는 중요하지 않다.
 */
public enum IdentityMethod {

    /** 간편인증. 이니시스 {@code reqSvcCd=01} */
    SIMPLE("01"),
    /** 휴대폰 본인확인(문자). 이니시스 {@code reqSvcCd=03} */
    PHONE("03");

    private final String code;

    IdentityMethod(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** 설정값(01/03)에서 고른다. 알 수 없는 값이면 기동을 막는다 — 조용히 다른 창이 뜨면 안 된다. */
    public static IdentityMethod ofCode(String code) {
        for (IdentityMethod method : values()) {
            if (method.code.equals(code)) {
                return method;
            }
        }
        throw new IllegalStateException("본인확인 방식 코드는 01(간편인증) 또는 03(휴대폰)이어야 합니다: " + code);
    }

    /** 화면이 보낸 값. 비어 있으면 기본값을 쓴다 — 예전 화면이 보내지 않아도 동작해야 한다. */
    public static IdentityMethod ofNullable(IdentityMethod method, IdentityMethod fallback) {
        return method == null ? fallback : method;
    }

    /** 이 방식을 쓸 수 없는 공급자 설정일 때. */
    public BusinessException unsupported() {
        return new BusinessException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE,
                this == PHONE
                        ? "휴대폰 문자 인증을 지금 쓸 수 없습니다. 간편인증으로 진행해 주세요."
                        : "간편인증을 지금 쓸 수 없습니다.");
    }
}
