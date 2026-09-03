package com.hanseongge.domain.audit.repository;

import com.hanseongge.domain.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 감사로그 저장소.
 *
 * <p>수정·삭제 메서드를 제공하지 않는다. {@link JpaRepository} 가 물려주는
 * {@code delete*} 는 프레임워크 제약상 남지만, 애플리케이션 코드에서 호출하지 않는다.
 * 보존기간 만료 삭제는 STEP 18 이후 DB 파티션/배치로 다룬다.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, AuditLogRepositoryCustom {
}
