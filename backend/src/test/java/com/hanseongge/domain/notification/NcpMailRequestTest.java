package com.hanseongge.domain.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NCP 메일 요청의 모양.
 *
 * <p>여기 걸린 두 가지는 2026-10-03 에 실제로 틀려 있던 값이다. 둘 다 <b>인증 실패처럼
 * 보이는 증상</b>을 내서 키를 의심하게 만들었고, 원인을 찾는 데 닷새가 걸렸다
 * ({@code docs/메일발송_401_정리.md}). 되돌아가면 여기서 막는다.
 */
class NcpMailRequestTest {

    @Test
    @DisplayName("경로는 SENS 꼴이다 — 옛 /api/v1/mails 로 되돌리면 77301 로 떨어진다")
    void pathIsSensShape() {
        String path = NcpOutboundMailSender.pathFor("ncp:mail:kr:1234:hanseongge");

        assertThat(path).isEqualTo("/mail/v2/services/ncp:mail:kr:1234:hanseongge/requests");
        assertThat(path).doesNotContain("/api/v1/mails");
    }

    @Test
    @DisplayName("서비스 ID 의 콜론을 그대로 둔다 — 서명이 같은 문자열로 계산되어야 한다")
    void serviceIdIsNotEncoded() {
        assertThat(NcpOutboundMailSender.pathFor("ncp:mail:kr:1234:hanseongge"))
                .contains("ncp:mail:kr:1234:hanseongge")
                .doesNotContain("%3A");
    }

    @Test
    @DisplayName("수신자 type 은 TO 다 — 옛 경로의 R 을 보내면 77102 로 거부당한다")
    void recipientTypeIsTo() {
        Map<String, Object> body = NcpOutboundMailSender.payload(
                "no-reply@hanseongge.com", "한성금거래소", "buyer@example.com", "인증번호", "123456");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recipients = (List<Map<String, Object>>) body.get("recipients");
        assertThat(recipients).singleElement()
                .returns("buyer@example.com", r -> r.get("address"))
                .returns("TO", r -> r.get("type"));
    }

    @Test
    @DisplayName("individual 은 true 다 — false 면 수신자 목록이 서로에게 보인다")
    void sendsIndividually() {
        Map<String, Object> body = NcpOutboundMailSender.payload(
                "no-reply@hanseongge.com", "한성금거래소", "buyer@example.com", "제목", "본문");

        assertThat(body.get("individual")).isEqualTo(true);
        assertThat(body.get("body").toString()).contains("본문").startsWith("<div");
    }
}
