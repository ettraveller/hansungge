package com.hanseongge.domain.user.identity;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * mcash 인증결과 노티 수신 (서버 간 호출).
 *
 * <p>mcash 서버가 직접 부른다. 브라우저가 아니라서 쿠키도 세션도 없고, 응답 본문으로
 * {@code SUCCESS} 라는 <b>글자</b>를 돌려줘야 한다 (매뉴얼 5.3). 그 글자를 받아야 mcash 가
 * 사용자를 Okurl 로 보낸다 — 아니면 사용자 화면이 {@code Resultcd=9997} 로 끝난다.
 *
 * <p>공급자가 바뀌면 이 주소도 함께 사라져야 해서 공통 인증 컨트롤러에 두지 않았다.
 * {@code IDENTITY_PROVIDER=MCASH} 일 때만 빈이 된다.
 *
 * <p>인증 없이 열려 있다({@code /api/v1/auth/**} 는 permitAll). 대신 들어온 값은 믿지 않는다 —
 * 가맹점 고유키가 들어간 {@code Mac} 이 맞아야만 받아들인다.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/auth/identity")
@ConditionalOnProperty(name = "app.identity.provider", havingValue = McashIdentityVerificationProvider.CODE)
public class McashNotiController {

    private final McashIdentityVerificationProvider provider;

    McashNotiController(McashIdentityVerificationProvider provider) {
        this.provider = provider;
    }

    /**
     * @return 받아들였으면 {@code SUCCESS}. 그 밖에는 {@code FAIL} — mcash 가 재전송하지는 않는다
     */
    @PostMapping(value = "/noti", produces = MediaType.TEXT_PLAIN_VALUE)
    public String noti(@RequestParam Map<String, String> params) {
        return provider.acceptNoti(params) ? "SUCCESS" : "FAIL";
    }
}
