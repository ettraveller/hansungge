package com.hanseongge.domain.audit.dto;

import com.hanseongge.domain.audit.entity.ActorType;

/**
 * 감사로그 1건 기록 요청.
 *
 * <p>{@code before} / {@code after} 는 아직 직렬화되지 않은 임의의 객체다.
 * JSON 변환은 {@link com.hanseongge.domain.audit.service.AuditLogService} 가 담당한다 —
 * 호출부마다 ObjectMapper 를 들고 다니면 마스킹 규칙(비밀번호 제거)이 흩어진다.
 */
public record AuditRecord(
        ActorType actorType,
        String actor,
        String action,
        String targetType,
        String targetId,
        Object before,
        Object after,
        String ip,
        String userAgent
) {

    public static Builder builder(String action) {
        return new Builder(action);
    }

    /**
     * 선택 필드가 많아 빌더를 둔다. 인자 9개짜리 생성자를 호출부에 노출하면
     * 순서를 잘못 넣어도 컴파일이 통과한다 (전부 String 이라 더 위험하다).
     */
    public static final class Builder {
        private ActorType actorType = ActorType.SYSTEM;
        private String actor = "SYSTEM";
        private final String action;
        private String targetType;
        private String targetId;
        private Object before;
        private Object after;
        private String ip;
        private String userAgent;

        private Builder(String action) {
            this.action = action;
        }

        public Builder actor(ActorType type, String actor) {
            this.actorType = type;
            this.actor = actor;
            return this;
        }

        public Builder target(String targetType, String targetId) {
            this.targetType = targetType;
            this.targetId = targetId;
            return this;
        }

        public Builder before(Object before) {
            this.before = before;
            return this;
        }

        public Builder after(Object after) {
            this.after = after;
            return this;
        }

        public Builder request(String ip, String userAgent) {
            this.ip = ip;
            this.userAgent = userAgent;
            return this;
        }

        public AuditRecord build() {
            return new AuditRecord(actorType, actor, action, targetType, targetId,
                    before, after, ip, userAgent);
        }
    }
}
