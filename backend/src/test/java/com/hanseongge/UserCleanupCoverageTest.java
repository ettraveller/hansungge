package com.hanseongge;

import com.hanseongge.support.EmbeddedPostgresSupport;
import com.hanseongge.support.UserTestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserTestFixtures#DELETE_ORDER} 가 실제 스키마를 따라가는지 본다.
 *
 * <h2>왜 필요한가</h2>
 * 이 목록은 손으로 관리된다. 그래서 {@code users} 에 닿는 테이블이 새로 생길 때마다
 * 조용히 낡는다 — 실제로 장바구니·주문·위시리스트가 들어오는 동안 갱신되지 않아,
 * 회원을 만드는 테스트 <b>139개</b>가 외래키 위반으로 무너진 채 남아 있었다.
 *
 * <p>나쁜 점은 실패 개수가 아니라 <b>증상이 원인을 가리키지 않는다는 것</b>이다.
 * {@code PasswordResetIntegrationTest} 가 빨개지면 비밀번호 재설정을 먼저 뒤지게 되는데,
 * 정작 범인은 정리 코드에 빠진 테이블 이름 하나다. 그 상태로 139개가 상시 빨가면
 * 진짜 회귀가 그 속에 묻힌다.
 *
 * <p>그래서 카탈로그에서 직접 읽어 대조한다. 테이블을 하나 더하면 <b>여기서</b> 걸리고,
 * 실패 메시지가 빠진 이름을 그대로 알려준다.
 */
@SpringBootTest
class UserCleanupCoverageTest extends EmbeddedPostgresSupport {

    @Autowired JdbcTemplate jdbcTemplate;

    /**
     * {@code users} 에 외래키로 닿는 모든 테이블. 직접 참조뿐 아니라 그 자식까지 따라간다 —
     * {@code order_items} 는 {@code users} 를 직접 참조하지 않지만 {@code orders} 를 통해
     * 묶여 있어 먼저 지워야 한다.
     */
    private static final String REACHES_USERS = """
            WITH RECURSIVE reach AS (
                SELECT c.conrelid AS tbl
                  FROM pg_constraint c
                 WHERE c.contype = 'f' AND c.confrelid = 'users'::regclass
                UNION
                SELECT c.conrelid
                  FROM pg_constraint c JOIN reach r ON c.confrelid = r.tbl
                 WHERE c.contype = 'f'
            )
            SELECT DISTINCT tbl::regclass::text FROM reach
            """;

    @Test
    @DisplayName("users 에 닿는 테이블이 전부 삭제 목록에 있다 — 빠지면 회원 테스트가 통째로 무너진다")
    void deleteOrderCoversEveryTableReachingUsers() {
        List<String> reaching = jdbcTemplate.queryForList(REACHES_USERS, String.class);

        assertThat(reaching)
                .describedAs("users 에 닿는 테이블")
                .isNotEmpty();
        assertThat(UserTestFixtures.DELETE_ORDER)
                .describedAs("""
                        UserTestFixtures.DELETE_ORDER 에 빠진 테이블이 있습니다.
                        users 를 참조하는 테이블을 새로 만들었다면 참조의 역순으로 넣어 주세요.
                        빠뜨리면 회원을 만드는 테스트가 전부 외래키 위반으로 실패합니다.""")
                .containsAll(reaching);
    }

    @Test
    @DisplayName("삭제가 실제로 통한다 — 트리거가 걸린 테이블까지 포함해서")
    void deleteAllRuns() {
        UserTestFixtures.deleteAll(jdbcTemplate);

        Integer remaining = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);
        assertThat(remaining).isZero();
    }
}
