package com.hanseongge.domain.media;

import com.hanseongge.global.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 앱 서버 디스크에 저장하고, nginx 가 그 디렉터리를 정적으로 내보낸다.
 *
 * <p>키 모양은 {@code products/2026/10/3f2a….webp} 다. 날짜로 한 겹 나누는 이유는
 * 한 디렉터리에 파일이 수만 개 쌓이면 {@code ls} 조차 느려지고 백업·정리가 어려워지기
 * 때문이다.
 *
 * <p>파일명은 <b>UUID 로 새로 만든다.</b> 올린 사람의 파일명을 쓰면 세 가지가 따라온다 —
 * 한글 파일명이 서버·브라우저·nginx 를 지나며 깨지고, {@code ../} 로 경로를 거슬러
 * 올라갈 수 있고, 같은 이름을 올리면 먼저 올린 상품의 사진이 조용히 바뀐다.
 *
 * <p>⚠️ 이 구현은 <b>서버 한 대</b>를 전제한다. 앱 서버를 늘리면 올린 서버에만 파일이
 * 있어서 다른 서버로 간 요청이 404 를 낸다. 그때는 이 클래스 대신 Object Storage 구현을
 * 끼운다 — 그래서 {@link MediaStorage} 뒤에 두었다.
 */
@Slf4j
@Component
public class LocalFileMediaStorage implements MediaStorage {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path root;
    private final String baseUrl;

    public LocalFileMediaStorage(AppProperties properties) {
        this.root = Path.of(properties.media().dir()).toAbsolutePath().normalize();
        // 끝 슬래시를 떼어 둔다. 붙은 채로 조립하면 주소에 `//` 가 생기고, 그 주소로
        // 저장된 OG 태그·캐시 키가 슬래시 하나 차이로 갈라진다.
        this.baseUrl = stripTrailingSlash(properties.media().publicBaseUrl());
    }

    @Override
    public StoredMedia store(String folder, byte[] content, ImageKind kind) {
        if (kind == null) {
            throw new MediaStoreException("이미지 형식을 알 수 없습니다.");
        }

        String objectKey = "%s/%s/%s.%s".formatted(
                folder, LocalDate.now().format(YEAR_MONTH), UUID.randomUUID(), kind.extension());
        Path target = root.resolve(objectKey).normalize();

        // folder 는 호출하는 코드가 정하는 상수지만, 그 약속이 깨졌을 때 디스크 전체에
        // 쓰게 되는 것보다 여기서 멈추는 쪽이 낫다.
        if (!target.startsWith(root)) {
            throw new MediaStoreException("저장 경로가 올바르지 않습니다.");
        }

        try {
            Files.createDirectories(target.getParent());
            // 임시 파일에 다 쓴 뒤 옮긴다. 바로 쓰면 쓰는 중인 파일이 nginx 에 노출되어
            // 반쯤 그려진 이미지가 보이고, 그 상태가 브라우저 캐시에 남는다.
            Path temp = Files.createTempFile(target.getParent(), "upload-", ".part");
            try {
                Files.write(temp, content);
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.deleteIfExists(temp);
                throw e;
            }
        } catch (IOException e) {
            log.error("이미지 저장 실패 key={} root={}", objectKey, root, e);
            throw new MediaStoreException("이미지를 저장하지 못했습니다.", e);
        }

        log.info("이미지 저장 key={} bytes={}", objectKey, content.length);
        return new StoredMedia(objectKey, urlFor(objectKey));
    }

    @Override
    public String urlFor(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        return baseUrl + "/" + objectKey.replaceFirst("^/+", "");
    }

    private static String stripTrailingSlash(String value) {
        String trimmed = value.strip();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
