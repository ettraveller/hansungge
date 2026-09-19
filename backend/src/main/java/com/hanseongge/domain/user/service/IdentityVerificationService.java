package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.entity.IdentityPurpose;
import com.hanseongge.domain.user.entity.IdentityVerification;
import com.hanseongge.domain.user.identity.IdentityStartForm;
import com.hanseongge.domain.user.identity.IdentityVerificationException;
import com.hanseongge.domain.user.identity.IdentityVerificationProvider;
import com.hanseongge.domain.user.identity.IdentityVerificationRegistry;
import com.hanseongge.domain.user.identity.IdentityVerificationResult;
import com.hanseongge.domain.user.repository.IdentityVerificationRepository;
import com.hanseongge.global.auth.RequestInfo;
import com.hanseongge.global.config.AppProperties;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 휴대폰 본인확인 기록.
 *
 * <h2>공급자 호출과 트랜잭션을 섞지 않는다</h2>
 * {@link #verifyAndRecord} 는 외부 HTTP 호출을 한 <b>뒤에</b> 트랜잭션을 연다.
 * 호출까지 트랜잭션에 넣으면 공급자가 느릴 때 DB 커넥션을 붙잡고 대기하게 되고,
 * 커넥션 풀이 먼저 마른다 — {@code UserAuthService} 가 소셜 로그인에서 같은 이유로
 * {@code @Transactional} 을 붙이지 않았다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityVerificationService {

    private final IdentityVerificationRegistry registry;
    private final IdentityVerificationRepository repository;
    private final IdentityVerificationRecorder recorder;
    private final AppProperties appProperties;

    /**
     * 인증창을 여는 폼.
     *
     * <p>결과는 공급자가 우리 <b>백엔드 수신 주소</b>로 보낸다 ({@code app.identity.callback-url}).
     * 화면(Next.js 페이지)은 폼 POST 를 받을 수 없어서다.
     */
    public IdentityStartForm start(String requestId) {
        return registry.require().start(requestId, appProperties.identity().callbackUrl());
    }

    public boolean isAvailable() {
        return registry.isAvailable();
    }

    /**
     * 공급자에 되물어 검증하고 이력을 남긴다.
     *
     * <p>브라우저가 들고 온 값을 그대로 믿지 않는다. 사용자가 고칠 수 있는 경로로 온
     * 데이터라, 공급자 서버에 다시 물어 확인한 값만 저장한다.
     *
     * @param requestId 서명된 진행 토큰에서 꺼낸 거래번호 — 결과가 이 요청의 것인지 대조한다
     * @param returned  공급자가 수신 주소로 보낸 값
     */
    public IdentityVerification verifyAndRecord(String requestId, Map<String, String> returned,
                                                IdentityPurpose purpose, RequestInfo requestInfo) {
        if (requestId == null || requestId.isBlank()) {
            // 본인확인 창을 이 흐름에서 연 적이 없다.
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_INVALID, "거래번호 없음");
        }
        IdentityVerificationProvider provider = registry.require();

        IdentityVerificationResult result;
        try {
            result = provider.verify(requestId, returned);
        } catch (IdentityVerificationException e) {
            // 공급자 응답 원문을 사용자에게 내보내지 않는다 — 상점 아이디가 섞여 나온다.
            log.warn("[IDENTITY] {} 본인확인 검증 실패: {}", provider.code(), e.getMessage());
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        // 만 14세 판정은 저장 전에 한다. 가입시킬 수 없는 사람의 신원을 남길 이유가 없다.
        result.identity().requireMinimumAge(LocalDate.now());

        OffsetDateTime expiresAt = OffsetDateTime.now()
                .plusMinutes(appProperties.signUp().identityTtlMinutes());
        return recorder.save(provider.code(), result, purpose, expiresAt, requestInfo);
    }

    /**
     * 아직 쓸 수 있는 본인확인인지 확인하고 돌려준다.
     *
     * <p>목적까지 대조한다 — 가입하려고 받은 인증으로 남의 비밀번호를 바꾸는 경로를 막는다.
     */
    @Transactional(readOnly = true)
    public IdentityVerification requireUsable(Long id, IdentityPurpose purpose) {
        IdentityVerification verification = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_VERIFICATION_INVALID));
        if (verification.getPurpose() != purpose || !verification.isUsable(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_INVALID);
        }
        return verification;
    }

    /**
     * 저장만 하는 별도 빈.
     *
     * <p>같은 클래스 안에서 {@code @Transactional} 메서드를 부르면 프록시를 거치지 않아
     * 트랜잭션이 조용히 무시된다. {@code UserAccountService} 를 {@code UserAuthService} 에서
     * 떼어낸 것과 같은 이유다.
     */
    @Service
    @RequiredArgsConstructor
    public static class IdentityVerificationRecorder {

        private final IdentityVerificationRepository repository;

        @Transactional
        public IdentityVerification save(String providerCode, IdentityVerificationResult result,
                                         IdentityPurpose purpose, OffsetDateTime expiresAt,
                                         RequestInfo requestInfo) {
            try {
                return repository.saveAndFlush(IdentityVerification.record(
                        providerCode, result.providerTxId(), purpose, result.identity(),
                        result.carrier(), expiresAt, requestInfo == null ? null : requestInfo.ip()));
            } catch (DataIntegrityViolationException e) {
                // ux_iv_tx 위반 — 같은 공급자 거래번호가 두 번 들어왔다. 남의 본인확인
                // 응답을 가로채 재전송하는 방식으로 신원을 도용하려는 시도일 수 있다.
                log.warn("[IDENTITY] 거래번호 재사용 시도. provider={}", providerCode);
                throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_INVALID,
                        "이미 사용된 본인확인입니다.");
            }
        }
    }
}
