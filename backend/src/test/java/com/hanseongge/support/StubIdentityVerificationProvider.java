package com.hanseongge.support;

import com.hanseongge.domain.user.identity.IdentityMethod;
import com.hanseongge.domain.user.identity.IdentityStartForm;
import com.hanseongge.domain.user.identity.IdentityVerificationException;
import com.hanseongge.domain.user.identity.IdentityVerificationProvider;
import com.hanseongge.domain.user.identity.IdentityVerificationResult;

import java.util.Map;
import java.util.UUID;

/**
 * 테스트용 본인인증 대역.
 *
 * <p>Mockito 목이 아니라 <b>평범한 클래스</b>다 — {@code StubOAuthProvider} 와 같은 이유.
 *
 * <p>실물(이니시스)과 같은 모양으로 움직인다. {@link #start} 는 백엔드 수신 주소로 보내는 폼을
 * 돌려주고, {@link #verify} 는 되물은 결과의 거래번호가 요청과 같은지 대조한다. 그래서 테스트도
 * "인증창 → 수신 주소 → 화면 → 결과 제출" 경로를 그대로 탄다 ({@link SignUpFlow}).
 *
 * <p>사람을 <b>지정할 수 있다.</b> 중복가입·번호 재할당·명의 불일치는 같은(또는 다른) CI 가
 * 두 번 오는 상황을 만들어야 검증된다. CI 를 빼고 돌려줄 수도 있다(카카오 "제한적 제공").
 */
public class StubIdentityVerificationProvider implements IdentityVerificationProvider {

    public static final String CODE = "STUB";

    /** false 로 두면 "본인인증 준비 안 됨"(자체 가입 닫힘) 자체를 검증할 수 있다. */
    public boolean configured = true;

    /** 다음 호출이 돌려줄 사람. null 이면 매번 새 사람(새 CI·새 번호)을 만든다. */
    public UserTestFixtures.Person nextPerson;

    /** 어느 방식으로 불렸는지 — 화면이 고른 값이 공급자까지 가는지 테스트가 본다. */
    public IdentityMethod lastMethod;

    /** true 면 다음 결과에서 CI 를 뺀다. */
    public boolean omitCi;

    /**
     * true 면 다음 결과에서 DI 를 뺀다.
     *
     * <p>간편인증은 DI 를 주지 않는다. 그 경로를 시험하려면 뺄 수 있어야 한다.
     */
    public boolean omitDi;

    /** 설정해 두면 {@link #verify} 가 그 예외를 던진다. */
    public IdentityVerificationException nextFailure;

    public String lastRequestId;
    public String lastCallbackUrl;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public IdentityStartForm start(String requestId, String callbackUrl, IdentityMethod method) {
        this.lastMethod = method;
        this.lastRequestId = requestId;
        this.lastCallbackUrl = callbackUrl;
        return new IdentityStartForm(callbackUrl, "POST", Map.of(
                "resultCode", "0000",
                "txId", "stub-" + UUID.randomUUID(),
                "mTxId", requestId));
    }

    @Override
    public IdentityVerificationResult verify(String requestId, Map<String, String> returned) {
        this.lastRequestId = requestId;
        if (nextFailure != null) {
            throw nextFailure;
        }
        if (!"0000".equals(returned.get("resultCode"))) {
            throw new IdentityVerificationException("스텁 본인인증 실패 resultCode=" + returned.get("resultCode"));
        }
        // 실물의 "되물은 결과의 거래번호가 우리 요청과 같은가"
        if (!requestId.equals(returned.get("mTxId"))) {
            throw new IdentityVerificationException("스텁 본인인증 거래번호 불일치");
        }
        UserTestFixtures.Person person = nextPerson != null ? nextPerson : UserTestFixtures.person("테스트회원");
        return new IdentityVerificationResult(returned.get("txId"),
                omitCi ? null : person.ci(),
                omitDi ? null : person.di(),
                person.name(), person.birthDate(), person.phone(), "STUB");
    }

    /** 이 사람으로 다음 인증이 통과한다. */
    public void willReturn(UserTestFixtures.Person person) {
        this.nextPerson = person;
    }

    public void willReturn(String ci, String name, String phone) {
        this.nextPerson = UserTestFixtures.personWith(ci, name, phone);
    }

    public void reset() {
        this.configured = true;
        this.nextPerson = null;
        this.omitCi = false;
        this.omitDi = false;
        this.nextFailure = null;
        this.lastRequestId = null;
        this.lastCallbackUrl = null;
    }
}
