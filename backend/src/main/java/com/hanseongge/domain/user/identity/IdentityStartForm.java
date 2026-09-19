package com.hanseongge.domain.user.identity;

import java.util.Map;

/**
 * 본인확인 창을 여는 방법 — 브라우저가 이 폼을 그대로 전송한다.
 *
 * <p>주소 하나로 표현하지 않는 이유: KG이니시스 통합인증은 인증창을 <b>폼 POST</b> 로 연다
 * (상점아이디·거래번호·해시를 본문으로 보낸다). 화면은 이 값을 해석하지 않고 hidden 필드로
 * 옮겨 담아 전송만 한다 — 공급자마다 화면에 분기를 두면 그 분기가 검증되지 않는다.
 *
 * @param action 전송할 주소
 * @param method {@code POST} 또는 {@code GET}
 * @param fields 전송할 필드. 순서를 지킬 필요는 없다
 */
public record IdentityStartForm(String action, String method, Map<String, String> fields) {

    public IdentityStartForm {
        fields = Map.copyOf(fields);
    }
}
