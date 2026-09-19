package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.repository.UserRepository;
import com.hanseongge.global.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 회원 비밀번호 로그인 시도 — 자격 검증, 실패 누적, 자동 잠금.
 *
 * <h2>왜 예외를 던지지 않고 결과를 돌려주는가</h2>
 * 로그인 실패는 <b>DB 를 변경하면서 실패하는</b> 드문 경우다(실패 횟수 +1, 임계치 도달 시
 * 잠금). 이 메서드 안에서 예외를 던지면 그 변경이 통째로 롤백되어 <b>아무리 시도해도
 * 계정이 잠기지 않는다.</b> 그래서 결과만 돌려주고, 예외 변환은 호출자가 트랜잭션 밖에서
 * 한다 — {@code AdminLoginAttemptService} 가 같은 이유로 같은 구조를 쓴다.
 *
 * <h2>관리자와 다른 점: 잠금이 스스로 풀린다</h2>
 * 관리자는 상태가 LOCKED 로 바뀌어 다른 관리자가 풀어줘야 한다. 회원에게 같은 방식을
 * 쓰면 <b>고객센터에 전화해야 로그인할 수 있다.</b> 30분이면 무차별 대입은 충분히
 * 무의미해지고, 잊어버린 본인은 기다리면 풀린다 ({@link User#LOCK_DURATION}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserLoginAttemptService {

    /** 로그인 시도 결과. 호출자가 응답/예외로 번역한다. */
    public enum Result {
        SUCCESS,
        /** 번호가 없거나 비밀번호가 틀림 — 응답에서는 구분하지 않는다. */
        BAD_CREDENTIALS,
        /** 연속 실패로 잠긴 계정 */
        LOCKED,
        /** 소셜로만 가입해 비밀번호가 없는 계정 */
        NO_PASSWORD,
        /** 탈퇴·정지 */
        NOT_ALLOWED
    }

    public record Outcome(Result result, User user) {

        public boolean isSuccess() {
            return result == Result.SUCCESS;
        }
    }

    /**
     * 존재하지 않는 계정에도 같은 비용을 치르기 위한 더미.
     *
     * <p>실제 cost-12 해시 형식이어야 한다. 형식이 다르면 {@code matches} 가 즉시 false 를
     * 돌려주고, 그러면 응답이 눈에 띄게 빨라져 <b>가입 여부가 시간차로 새어 나간다.</b>
     */
    private static final String DUMMY_HASH =
            "$2a$12$C6UzMDM.H6dfI/f/IKcEe.6vDVUKUmuAgqDMxlIhtT9FHYzHcbBjS";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    /**
     * @param phone 정규화된 휴대폰번호. 형식이 틀려 정규화에 실패했으면 null — 없는 계정과 같이 다룬다
     */
    @Transactional
    public Outcome attempt(String phone, String rawPassword) {
        Optional<User> found = phone == null ? Optional.empty() : userRepository.findByPhone(phone);

        if (found.isEmpty()) {
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            return new Outcome(Result.BAD_CREDENTIALS, null);
        }

        User user = found.get();

        if (!user.getStatus().canSignIn()) {
            // 정지·휴면 계정은 비밀번호가 맞아도 통과시키지 않고 실패 횟수도 올리지 않는다.
            return new Outcome(Result.NOT_ALLOWED, user);
        }

        if (!user.hasPassword()) {
            // 소셜로만 가입한 계정. 여기서도 더미 해시를 돌려 시간차를 없앤다 —
            // 안 그러면 "비밀번호가 있는 계정인가"가 응답 속도로 드러난다.
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            return new Outcome(Result.NO_PASSWORD, user);
        }

        if (user.isLocked(OffsetDateTime.now())) {
            return new Outcome(Result.LOCKED, user);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            int max = appProperties.signUp().maxLoginFailures();
            if (user.recordLoginFailure(max)) {
                log.warn("[AUTH] 연속 실패 {}회로 회원 계정을 잠갔습니다. userId={}", max, user.getId());
                return new Outcome(Result.LOCKED, user);
            }
            return new Outcome(Result.BAD_CREDENTIALS, user);
        }

        return new Outcome(Result.SUCCESS, user);
    }
}
