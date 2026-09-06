package com.hanseongge.domain.user.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 성별.
 *
 * <p>회원이 고르는 값이 아니라 <b>본인확인기관이 돌려준 값</b>이다. 그래서 선택지를
 * 넓히지 않는다 — 본인확인 응답에 그 두 가지밖에 없고, 없는 값을 받을 수 있는 것처럼
 * 열거형을 넓혀 두면 DB CHECK 제약과 어긋난다.
 *
 * <p>이 값을 쓰는 곳은 지금 없다. 그럼에도 저장하는 이유는 본인확인 응답을 <b>받은 그대로</b>
 * 남겨야 나중에 공급자 기록과 대조할 수 있기 때문이다. 화면에 노출하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum Gender {

    MALE("남"),
    FEMALE("여");

    private final String displayName;
}
