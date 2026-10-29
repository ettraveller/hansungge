package com.hanseongge.domain.media;

/**
 * 업로드를 허용하는 이미지 종류.
 *
 * <p><b>확장자나 Content-Type 을 믿지 않는다.</b> 둘 다 클라이언트가 보내는 값이라
 * {@code .jpg} 라는 이름의 HTML 을 올릴 수 있다. 실제 바이트 앞머리(매직 넘버)를 읽어
 * 종류를 판정하고, 파일명은 여기서 정한 확장자로 새로 만든다.
 *
 * <p><b>SVG 는 없다.</b> SVG 는 {@code <script>} 를 품을 수 있는 문서라서, 업로드를
 * 허용하면 관리자가 올린 파일이 그대로 저장 도메인에서 실행되는 XSS 경로가 된다.
 * 상품 사진에 SVG 가 필요한 경우가 없으므로 아예 받지 않는다.
 */
public enum ImageKind {

    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp");

    private final String extension;
    private final String contentType;

    ImageKind(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /**
     * 바이트 앞머리로 종류를 판정한다. 아는 형식이 아니면 {@code null}.
     *
     * <p>WEBP 만 12바이트를 본다 — {@code RIFF} 로 시작하는 컨테이너는 WEBP 말고도
     * 있어서(WAV, AVI) 8번째 바이트부터의 {@code WEBP} 까지 확인해야 구분된다.
     */
    public static ImageKind sniff(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if (starts(bytes, 0xFF, 0xD8, 0xFF)) {
            return JPEG;
        }
        if (starts(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return PNG;
        }
        if (starts(bytes, 'G', 'I', 'F', '8')) {
            return GIF;
        }
        if (starts(bytes, 'R', 'I', 'F', 'F')
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return WEBP;
        }
        return null;
    }

    private static boolean starts(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
