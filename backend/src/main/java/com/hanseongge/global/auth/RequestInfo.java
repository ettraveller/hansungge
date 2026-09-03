package com.hanseongge.global.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 감사로그에 남길 요청 출처. 서비스 계층이 {@code HttpServletRequest} 를 직접
 * 알지 않도록 값만 뽑아 전달한다 — 서비스는 HTTP 를 몰라야 테스트가 쉽다.
 */
public record RequestInfo(String ip, String userAgent) {

    public static RequestInfo from(HttpServletRequest request) {
        return new RequestInfo(RequestMetadata.ip(request), RequestMetadata.userAgent(request));
    }

    /** 요청 컨텍스트가 없는 실행 경로(배치·테스트)용. */
    public static RequestInfo none() {
        return new RequestInfo(null, null);
    }
}
