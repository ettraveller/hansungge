package com.hanseongge.domain.notification;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * NCP API Gateway 서명 v2 — 메일러(Cloud Outbound Mailer)와 문자(SENS)가 같이 쓴다.
 *
 * <p>{@code HMAC-SHA256(secretKey, "METHOD url\ntimestamp\naccessKey")} 를 Base64 로 인코딩한다.
 * 공백은 한 칸이고 줄바꿈은 {@code \n} 이다 — 한 글자만 달라도 401 이 돌아오는데,
 * 응답 본문이 원인을 알려주지 않아 찾는 데 오래 걸린다. 그래서 한 곳에만 둔다.
 *
 * <p>{@code url} 은 호스트를 뺀 경로다 (쿼리가 있으면 쿼리까지).
 */
public final class NcpApiSigner {

    private NcpApiSigner() {
    }

    public static String sign(String method, String url, String timestamp,
                              String accessKey, String secretKey) {
        String message = method + " " + url + "\n" + timestamp + "\n" + accessKey;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("NCP API 서명 생성 실패", e);
        }
    }
}
