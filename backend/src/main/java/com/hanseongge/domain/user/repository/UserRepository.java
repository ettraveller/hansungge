package com.hanseongge.domain.user.repository;

import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.UserStatus;
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
}
