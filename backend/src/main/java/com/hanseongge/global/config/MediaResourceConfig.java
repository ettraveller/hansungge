package com.hanseongge.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 업로드한 이미지를 {@code /media/**} 로 내보낸다.
 *
 * <h2>왜 nginx 가 아니라 애플리케이션이 내보내는가</h2>
 * nginx {@code alias} 로 내보내면 한 단계 빠르지만, 저장 경로가 설정 파일 두 곳
 * (nginx, 애플리케이션)에 적히고 한쪽만 고치는 날 업로드는 성공하는데 화면이 전부
 * 깨진다. 이 사이트의 이미지 트래픽은 Java 가 충분히 감당하는 양이라, 경로의 주인을
 * 하나로 두는 쪽을 택했다. 나중에 느려지면 nginx 블록을 덧붙이면 되고 — 그때도
 * {@code app.media.dir} 하나만 보면 된다.
 *
 * <h2>캐시</h2>
 * 파일명이 UUID 라 <b>내용이 바뀌면 이름도 바뀐다.</b> 같은 이름이 다른 그림이 되는 일이
 * 없으므로 길게 캐시해도 안전하다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MediaResourceConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Path.of(properties.media().dir()).toAbsolutePath().normalize();

        // 없으면 만든다. 운영에서는 provision.sh 가 미리 만들어 두지만, 처음 받은
        // 개발 PC 에서는 없는 상태로 기동한다 — 그 경우 Path.toUri() 가 끝 슬래시를
        // 붙이지 않아서(디렉터리인 줄 모른다) 조회가 전부 404 가 된다. 업로드는
        // 성공하므로 원인을 찾기 어려운 모양이다.
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            // 기동을 막지 않는다. 이미지가 안 보이는 것은 사이트가 뜨지 않는 것보다 작다.
            log.warn("업로드 디렉터리를 만들지 못했다 — 이미지 업로드가 실패한다 dir={}", root, e);
        }

        log.info("업로드 이미지 서빙 /media/** -> {}", root);

        registry.addResourceHandler("/media/**")
                // 끝 슬래시가 있어야 디렉터리로 읽힌다. toUri() 는 디렉터리가 실제로
                // 존재할 때만 붙여 주므로 여기서 확실히 보장한다.
                .addResourceLocations(root.toUri().toString().replaceFirst("/*$", "/"))
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }
}
