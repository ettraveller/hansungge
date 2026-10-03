package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.entity.IdentityPurpose;
import com.hanseongge.domain.user.entity.IdentityVerification;
import com.hanseongge.domain.user.entity.VerifiedIdentity;
import com.hanseongge.domain.user.identity.IdentityKeyHasher;
import com.hanseongge.domain.user.identity.IdentityMethod;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 본인인증(간편인증) 기록.
 *
 * <h2>CI 는 여기서 사라진다</h2>
 * 공급자가 준 CI 원본은 {@link #verifyAndRecord} 안에서 곧바로 판정 키로 해시되고, 원본을 담은
 * 결과 객체는 이 메서드를 벗어나지 않는다. 저장·로그·토큰 어디에도 CI 원본이 없다.
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
    private final IdentityKeyHasher keyHasher;
    private final AppProperties appProperties;

    /**
     * 인증창을 여는 폼.
     *
     * <p>결과는 공급자가 우리 <b>백엔드 수신 주소</b>로 보낸다 ({@code app.identity.callback-url}).
     * 화면(Next.js 페이지)은 폼 POST 를 받을 수 없어서다.
     */
    public IdentityStartForm start(String requestId, IdentityMethod method) {
        var provider = registry.require();
        if (!provider.supports(method)) {
            throw method.unsupported();
        }
        return provider.start(requestId, appProperties.identity().callbackUrl(), method);
    }

    /** 설정에 적힌 기본 방식. 화면이 고르지 않았을 때 쓴다. */
    public IdentityMethod defaultMethod() {
        return IdentityMethod.ofCode(appProperties.identity().reqSvcCd().trim());
    }

    /** 공급자가 있고 판정 키를 만들 비밀값도 있어야 본인인증을 쓸 수 있다. */
    public boolean isAvailable() {
        return registry.isAvailable() && keyHasher.isConfigured();
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
        if (!keyHasher.isConfigured()) {
            throw new BusinessException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE, "판정 키 비밀값 없음");
        }
        IdentityVerificationProvider provider = registry.require();

        IdentityVerificationResult result;
        try {
            result = provider.verify(requestId, returned);
        } catch (IdentityVerificationException e) {
            // 공급자 응답 원문을 사용자에게 내보내지 않는다 — 상점 아이디가 섞여 나온다.
            log.warn("[IDENTITY] {} 본인인증 검증 실패: {}", provider.code(), e.getMessage());
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }

        // CI 가 없으면 같은 사람 판정을 할 수 없다. 저장하지 않고 수단을 바꾸게 한다.
        if (!result.hasCi()) {
            log.warn("[IDENTITY] {} 응답에 CI 없음 — 인증 수단이 CI 를 주지 않았다. txId={}",
                    provider.code(), result.providerTxId());
            throw new BusinessException(ErrorCode.IDENTITY_KEY_UNAVAILABLE);
        }
        VerifiedIdentity identity = toVerifiedIdentity(result);

        // 만 14세 판정은 저장 전에 한다. 가입시킬 수 없는 사람의 신원을 남길 이유가 없다.
        identity.requireMinimumAge(LocalDate.now());

        OffsetDateTime expiresAt = OffsetDateTime.now()
                .plusMinutes(appProperties.signUp().identityTtlMinutes());
        return recorder.save(provider.code(), result.providerTxId(), identity, result.carrier(),
                purpose, expiresAt, requestInfo);
    }

    /** 여기서 CI 원본이 판정 키로 바뀐다. 이 뒤로 CI 는 어디에도 전달되지 않는다. */
    private VerifiedIdentity toVerifiedIdentity(IdentityVerificationResult result) {
        try {
            return new VerifiedIdentity(
                    keyHasher.hash(result.ci()), result.name(), result.birthDate(), result.phone());
        } catch (IllegalArgumentException e) {
            // 이름·생년월일 누락 등. 원문(개인정보)을 싣지 않는다.
            log.warn("[IDENTITY] 본인인증 응답 형식 오류. txId={}", result.providerTxId());
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_FAILED);
        }
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
     * 이 회원이 최근에 본인인증을 받았는가 — 매입 신청·정산계좌 등록 앞에서 부른다.
     *
     * <p>로그인한 채 받은 본인인증({@code STEP_UP})만 센다. 가입 때의 인증은 오래됐을 수 있고,
     * 돈이 나가는 순간의 본인 확인이 목적이다.
     *
     * @throws BusinessException {@code IDENTITY_VERIFICATION_INVALID} — 화면은 본인인증 창을 띄우면 된다
     */
    @Transactional(readOnly = true)
    public void requireRecentStepUp(Long userId, Duration within) {
        OffsetDateTime since = OffsetDateTime.now().minus(within);
        boolean recent = repository.existsByUserIdAndPurposeAndConsumedAtAfter(userId, IdentityPurpose.STEP_UP, since);
        if (!recent) {
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_INVALID, "최근 본인인증 없음");
        }
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
        public IdentityVerification save(String providerCode, String providerTxId, VerifiedIdentity identity,
                                         String carrier, IdentityPurpose purpose, OffsetDateTime expiresAt,
                                         RequestInfo requestInfo) {
            try {
                return repository.saveAndFlush(IdentityVerification.record(
                        providerCode, providerTxId, purpose, identity,
                        carrier, expiresAt, requestInfo == null ? null : requestInfo.ip()));
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
