package com.hanseongge.domain.user.repository;

import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일로 찾는다.
     *
     * <p><b>{@code lower()} 로 비교한다.</b> V13 의 부분 UNIQUE 인덱스가 {@code lower(email)}
     * 기준이라, 그냥 {@code =} 로 찾으면 인덱스를 타지 않을뿐더러 대소문자만 다른 주소를
     * 다른 계정으로 취급하게 된다.
     *
     * <p>탈퇴한 회원은 제외한다. 탈퇴 행은 남아 있지만 로그인 대상이 아니고, 같은 주소로
     * 다시 가입한 새 계정과 구분되어야 한다.
     */
    @Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email) AND u.status <> :excluded")
    Optional<User> findActiveByEmail(@Param("email") String email,
                                     @Param("excluded") UserStatus excluded);

    default Optional<User> findByEmail(String email) {
        return findActiveByEmail(email, UserStatus.WITHDRAWN);
    }

    /**
     * DI 로 찾는다 — 중복가입 방지와 계정 병합의 근거다.
     *
     * <p>이메일은 바꿀 수 있지만 DI 는 사람에 붙는다. 그래서 "이미 가입한 사람인가"의
     * 최종 판단은 이쪽이다.
     */
    @Query("SELECT u FROM User u WHERE u.di = :di AND u.status <> :excluded")
    Optional<User> findActiveByDi(@Param("di") String di,
                                  @Param("excluded") UserStatus excluded);

    default Optional<User> findByDi(String di) {
        return findActiveByDi(di, UserStatus.WITHDRAWN);
    }

    /**
     * 관리자 회원 목록 — 검색어와 상태로 좁힌다.
     *
     * <p>탈퇴 회원도 <b>포함한다.</b> 관리자 화면의 목적은 "지금 쓸 수 있는 계정"이 아니라
     * "이 사람이 우리와 어떤 관계였나"를 확인하는 것이고, 탈퇴 이력이 안 보이면 같은
     * 사람이 재가입한 것을 알 수 없다.
     *
     * <p>검색은 이메일·이름·휴대폰 세 곳을 함께 본다. 고객센터로 걸려 오는 전화는
     * "제 번호가 5678 로 끝나요"거나 "홍길동인데요" 둘 중 하나다.
     *
     * <p>정렬은 질의에 고정한다. {@code Pageable} 의 정렬을 그대로 받으면 인덱스를 벗어난
     * 정렬로 전건 스캔이 나고, 그 순간을 아무도 눈치채지 못한다.
     *
     * <h2>⚠️ {@code keyword} 에 null 을 넘기지 않는다 — 빈 문자열이다</h2>
     * {@code :keyword IS NULL} 로 분기하면 검색어가 없을 때 <b>타입이 없는 null</b> 이
     * 바인딩되고, PostgreSQL 은 그 타입을 {@code bytea} 로 추론한다. 그러면
     * {@code lower(...)} 가 이렇게 터진다.
     *
     * <pre>
     * ERROR: function lower(bytea) does not exist
     * </pre>
     *
     * <p>그래서 "검색어 없음"을 <b>빈 문자열</b>로 표현한다. 잃는 것도 없다 — 어차피
     * {@code LIKE '%...%'} 는 앞이 와일드카드라 인덱스를 못 쓰고, 빈 검색어는 의미상
     * 전건이 맞다.
     *
     * <p>{@code status} 는 함수에 넣지 않아서 null 이어도 안전하다({@code ? IS NULL} 은
     * 타입 없는 값에도 동작한다). 여기에 함수를 씌우게 되면 같은 함정에 빠진다.
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:status IS NULL OR u.status = :status)
              AND (:keyword = ''
                   OR lower(u.email) LIKE lower(concat('%', :keyword, '%'))
                   OR lower(u.name) LIKE lower(concat('%', :keyword, '%'))
                   OR u.phone LIKE concat('%', :keyword, '%'))
            ORDER BY u.id DESC
            """)
    Page<User> findAdminPage(
            @Param("keyword") String keyword,
            @Param("status") UserStatus status,
            Pageable pageable);

    long countByStatus(UserStatus status);
}
