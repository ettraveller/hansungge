package com.hanseongge.domain.media;

/** 저장에 실패했다. 디스크·권한 문제이거나 받은 내용이 이미지가 아니다. */
public class MediaStoreException extends RuntimeException {

    public MediaStoreException(String message) {
        super(message);
    }

    public MediaStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
