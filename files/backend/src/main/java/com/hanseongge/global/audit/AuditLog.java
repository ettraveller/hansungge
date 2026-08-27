package com.hanseongge.global.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 메서드의 실행을 {@code audit_logs} 에 남긴다.
 *
 * <p>행위자(actor) · IP · User-Agent · 시각은 {@link AuditLogAspect} 가 자동으로 채운다.
 * 서비스 코드가 감사 기록을 위해 요청 객체를 인자로 받아 다닐 필요가 없다.
 *
 * <pre>{@code
 * @AuditLog(action = "ADMIN_CREATE", targetType = "ADMIN_USER", targetId = "#result.id()")
 * public AdminUserResponse create(AdminCreateRequest request) { ... }
 * }</pre>
 *
 * <h2>변경 전(before) 값</h2>
 * 변경 전 상태는 메서드가 실행되기 전에만 알 수 있고, 그 값을 아는 것은 서비스 자신이다.
 * 어드바이스가 DB 를 다시 읽어 추측하지 않는다 — 그러면 어드바이스가 도메인을 알아야 한다.
 * 대신 서비스가 {@link AuditContext#before(Object)} 로 넘겨주면 어드바이스가 집어간다.
 *
 * @see AuditLogAspect
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 행위 코드. {@code audit_logs.action} 에 그대로 들어간다 (예: ADMIN_CREATE). */
    String action();

    /** 대상 유형 (예: ADMIN_USER). 비우면 기록하지 않는다. */
    String targetType() default "";

    /**
     * 대상 식별자 SpEL. 메서드 인자명과 {@code #result} 를 쓸 수 있다.
     * 예: {@code "#id"}, {@code "#result.id()"}
     */
    String targetId() default "";

    /**
     * 변경 후 값 SpEL. 기본값은 반환값 전체다.
     * {@code "null"} 로 두면 after 를 기록하지 않는다.
     */
    String after() default "#result";

    /**
     * 메서드가 예외로 끝났을 때도 {@code <action>_FAILED} 로 기록할지.
     *
     * <p>기본이 true 인 이유: 실패한 시도야말로 감사에서 중요한 신호다.
     * "권한이 없는데 계속 시도한 흔적"은 성공 기록만 봐서는 보이지 않는다.
     */
    boolean recordFailure() default true;
}
