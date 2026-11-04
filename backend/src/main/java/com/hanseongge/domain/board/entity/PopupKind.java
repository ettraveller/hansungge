package com.hanseongge.domain.board.entity;

/**
 * 팝업 종류 ({@code popups.kind} CHECK 와 같은 값이어야 한다).
 *
 * <h2>이미지 한 장으로 받지 않는다</h2>
 * 쇼핑몰 팝업은 보통 이미지 한 장을 올리게 만든다. 만들기는 쉽지만 그 이미지에 적힌
 * 글자는 <b>읽어 주는 도구가 읽지 못하고, 검색에도 안 걸리고, 좁은 화면에서 뭉개진다.</b>
 * 제목·본문을 글로 받고 이미지는 곁들이로 둔다.
 */
public enum PopupKind {

    /** 매입 계산기를 그대로 띄운다. 내용이 코드에 있어 관리자가 쓸 것이 없다 */
    CALCULATOR("매입 계산기"),

    /** 관리자가 쓴 제목·본문·이미지 */
    CONTENT("직접 작성");

    private final String displayName;

    PopupKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
