package com.hanseongge.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 감사로그에 남길 요청 정보(IP / User-Agent) 추출.
 *
 * <p>{@code server.forward-headers-strategy: framework} 설정으로 Spring 의
 * {@code ForwardedHeaderFilter} 가 이미 {@code X-Forwarded-For} 를 반영한 값을
 * {@code getRemoteAddr()} 로 돌려준다. 그래도 헤더를 직접 한 번 더 보는 이유는,
 * 그 설정이 빠진 환경에서 감사로그의 IP 가 전부 Nginx 주소로 찍히는 사고를 막기 위해서다.
 */
public final class RequestMetadata {

    private static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_REAL_IP = "X-Real-IP";
    private static final String HEADER_USER_AGENT = "User-Agent";

    /** audit_logs.ip 컬럼 길이(IPv6 최대 45자). */
    private static final int IP_MAX = 45;

    private RequestMetadata() {
    }

    public static String ip(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader(HEADER_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            // "client, proxy1, proxy2" — 맨 앞이 원 클라이언트다.
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return cut(first);
            }
        }
        String realIp = request.getHeader(HEADER_REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            return cut(realIp.trim());
        }
        return cut(request.getRemoteAddr());
    }

    public static String userAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader(HEADER_USER_AGENT);
    }

    /**
     * 현재 요청 스레드의 HttpServletRequest. 컨트롤러 인자로 받을 수 없는 지점
     * (AOP 어드바이스, 서비스 계층)에서만 쓴다.
     *
     * @return 요청 컨텍스트가 없으면(스케줄러·테스트) null
     */
    public static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private static String cut(String ip) {
        if (ip == null) {
            return null;
        }
        return ip.length() <= IP_MAX ? ip : ip.substring(0, IP_MAX);
    }
}
