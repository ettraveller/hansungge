package com.hanseongge.domain.user.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이니시스 연동의 "필수 검증" — 결과조회 URL 과 해시 형식.
 *
 * <p>결과조회 URL({@code authRequestUrl})은 브라우저를 거쳐 온다. 이니시스 주소인지 확인하지
 * 않으면 우리 서버가 <b>공격자가 고른 주소</b>에 상점아이디를 실어 요청하게 된다(SSRF).
 * 이니시스 샘플은 {@code startsWith} 로 확인하는데, 그 방식이 뚫리는 모양을 여기서 막는다.
 */
@DisplayName("이니시스 결과조회 URL 검증 · 해시")
class InicisRequestGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://kssa.inicis.com/api/v1/result",
            "https://fcsa.inicis.com/api/v1/result?x=1",
            "https://KSSA.inicis.com/path",
            "https://kssa.inicis.com:443/path"
    })
    void 이니시스_주소는_통과(String url) {
        assertThat(InicisIdentityVerificationProvider.requireInicisUrl(url)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://kssa.inicis.com/api",              // https 아님
            "https://kssa.inicis.com.evil.io/api",     // 앞부분만 같은 다른 도메인 (startsWith 로는 통과)
            "https://kssa.inicis.com@evil.io/api",     // 사용자정보 트릭 (startsWith 로는 통과)
            "https://evil.io/https://kssa.inicis.com", // 경로에 숨김
            "https://sa.inicis.com/api",               // 이니시스지만 결과조회 호스트가 아님
            "https://kssa.inicis.com:8443/api",        // 다른 포트
            "https://127.0.0.1/api",
            "not a url"
    })
    void 그_밖은_거부(String url) {
        assertThatThrownBy(() -> InicisIdentityVerificationProvider.requireInicisUrl(url))
                .isInstanceOf(IdentityVerificationException.class);
    }

    @Test
    @DisplayName("authHash 는 SHA-256 소문자 16진수 — 이니시스 샘플(SHA256.java)과 같은 모양")
    void 해시는_소문자_16진수() {
        // SHA-256("abc") — FIPS 180-2 표준 예제
        assertThat(InicisIdentityVerificationProvider.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("한글도 UTF-8 로 해시한다 — 샘플이 getBytes(\"UTF-8\") 를 쓴다")
    void 해시는_UTF8() {
        assertThat(InicisIdentityVerificationProvider.sha256Hex("홍길동"))
                .hasSize(64)
                .matches("^[0-9a-f]{64}$");
    }
}
