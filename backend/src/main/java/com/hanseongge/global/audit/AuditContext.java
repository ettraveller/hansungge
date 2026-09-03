package com.hanseongge.global.audit;

/**
 * {@link AuditLog} 어드바이스에 "변경 전 값"을 건네는 통로.
 *
 * <p>ThreadLocal 을 쓰는 이유는 서비스 메서드의 <b>시그니처를 오염시키지 않기 위해서</b>다.
 * 감사 때문에 반환 타입에 before 를 끼워 넣으면 감사가 없어지는 날 API 가 같이 바뀐다.
 *
 * <p>요청 스레드당 1건만 유지한다. 어드바이스가 값을 가져가면서 반드시 비우므로
 * 스레드풀 재사용으로 다음 요청에 값이 새는 경로는 없다.
 */
public final class AuditContext {

    private static final ThreadLocal<Object> BEFORE = new ThreadLocal<>();

    private AuditContext() {
    }

    /** 변경 전 스냅샷 등록. 엔티티를 직접 넘기지 말고 값 사본(DTO/Map)을 넘긴다. */
    public static void before(Object snapshot) {
        BEFORE.set(snapshot);
    }

    /** 값을 가져가면서 비운다. 어드바이스 전용. */
    static Object consumeBefore() {
        Object value = BEFORE.get();
        BEFORE.remove();
        return value;
    }

    public static void clear() {
        BEFORE.remove();
    }
}
