package com.hanseongge.domain.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NCP 메일 본문 — NCP 는 본문을 HTML 로 보여 준다.
 */
class NcpMailBodyTest {

    @Test
    @DisplayName("줄바꿈이 <br> 로 바뀐다 — 그대로 보내면 메일함에서 모든 줄이 한 줄로 붙는다")
    void newlinesBecomeBreaks() {
        String html = NcpOutboundMailSender.toHtml("첫 줄\n\n    123456\n마지막\n");
        assertThat(html).contains("첫 줄<br>\n<br>\n    123456<br>\n마지막");
        assertThat(html).doesNotEndWith("<br>\n</div>");
    }

    @Test
    @DisplayName("꺾쇠·앰퍼샌드는 이스케이프한다 — 본문에 섞인 값이 태그가 되지 않는다")
    void escapesMarkup() {
        String html = NcpOutboundMailSender.toHtml("<script>alert(1)</script> & \"x\"");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;x&quot;");
        assertThat(html).doesNotContain("<script>");
    }
}
