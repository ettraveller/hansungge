package com.hanseongge.domain.media;

/**
 * 업로드한 파일을 어디에 두는지 감춘다 (설계서 원칙 P5 — 외부 연동은 인터페이스 뒤에).
 *
 * <p>지금 구현은 앱 서버 디스크({@link LocalFileMediaStorage})다. 설계서는 NCP Object
 * Storage 를 말하지만 그것은 계약·버킷·자격증명이 선행되는 일이고, 상품 등록은 지금
 * 막혀 있다. 저장하는 값이 URL 이 아니라 <b>키</b>이므로(= {@code product_images.object_key}),
 * 나중에 이 인터페이스의 구현만 바꾸면 DB 는 그대로 둘 수 있다.
 */
public interface MediaStorage {

    /**
     * 이미지를 저장하고 키와 공개 URL 을 돌려준다.
     *
     * @param folder  키 앞에 붙는 분류. <b>호출하는 코드가 정하는 상수</b>여야 한다 —
     *                클라이언트가 보낸 값을 그대로 넘기면 경로를 거슬러 올라갈 수 있다.
     * @param content 파일 내용. 종류는 {@link ImageKind#sniff}로 이미 판정된 것이어야 한다.
     */
    StoredMedia store(String folder, byte[] content, ImageKind kind);

    /** 저장된 키를 브라우저가 읽을 수 있는 주소로 바꾼다. */
    String urlFor(String objectKey);

    /** 저장 결과. 키는 DB 에 넣고, URL 은 방금 올린 것을 화면에 바로 보여주는 데 쓴다. */
    record StoredMedia(String objectKey, String url) {}
}
