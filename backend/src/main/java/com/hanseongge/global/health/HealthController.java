package com.hanseongge.global.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 헬스체크 API.
 *
 * <p>Controller 규칙 (설계서 §2.2):
 * <ul>
 *   <li>비즈니스 로직을 쓰지 않는다 — 위임만 한다</li>
 *   <li>Entity 를 반환하지 않는다 — DTO 만 반환한다</li>
 * </ul>
 */
@Tag(name = "System", description = "서비스 상태")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/health")
public class HealthController {

    private final HealthService healthService;

    @Operation(summary = "서비스 상태 조회",
            description = "기동 여부, 활성 프로파일, DB/Flyway 적용 상태, 반올림 정책을 확인한다.")
    @GetMapping
    public HealthResponse health() {
        return healthService.getHealth();
    }
}
