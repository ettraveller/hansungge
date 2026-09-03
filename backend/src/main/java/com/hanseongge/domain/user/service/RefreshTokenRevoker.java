package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.entity.RefreshToken;
import com.hanseongge.domain.user.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈취 대응 전용 폐기.
 *
 * <h2>왜 별도 빈이고 왜 REQUIRES_NEW 인가</h2>
 * 이미 폐기된 Refresh 토큰이 다시 오면 그 회원의 세션을 전부 끊고 <b>요청은 거부</b>해야
 * 한다. 그런데 폐기와 거부를 같은 트랜잭션에서 하면, 거부하려고 던진 예외가 폐기까지
 * 되돌린다 — 보안 조치가 실행된 것처럼 보이지만 DB 에는 아무 일도 일어나지 않는다.
 *
 * <p>그래서 폐기를 독립된 트랜잭션에서 커밋한 뒤 예외를 던진다. 같은 클래스 안에서
 * 호출하면 프록시를 거치지 않아 {@code REQUIRES_NEW} 가 무시되므로 빈 자체를 분리했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenRevoker {

    private final RefreshTokenRepository refreshTokenRepository;

    /** 바깥 트랜잭션이 롤백되어도 남는 폐기. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllNow(Long userId) {
        int revoked = refreshTokenRepository.revokeAll(RefreshToken.SUBJECT_USER, userId);
        log.warn("[AUTH] 탈취 의심으로 회원 {} 의 세션 {}건을 즉시 폐기했습니다.", userId, revoked);
        return revoked;
    }
}
