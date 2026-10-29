package com.hanseongge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.domain.media.ImageKind;
import com.hanseongge.support.AdminTestFixtures;
import com.hanseongge.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 이미지 업로드 ({@code POST /api/admin/media}).
 *
 * <p>고정하는 것:
 * <ul>
 *   <li>확장자·Content-Type 이 아니라 <b>실제 바이트</b>로 형식을 판정한다</li>
 *   <li>저장 파일명은 <b>서버가 만든다</b> — 올린 파일명이 경로에 섞이지 않는다</li>
 *   <li>folder 는 허용 목록 안에서만 쓰인다 — 경로를 거슬러 올라갈 수 없다</li>
 *   <li>한 장이라도 거절되면 아무것도 저장하지 않는다 — 떠돌이 파일을 남기지 않는다</li>
 *   <li>로그인하지 않으면 올릴 수 없다</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-for-media-tests-0123456789abcdef",
        "app.admin-security.login-rate-per-minute=1000",
        "app.media.public-base-url=https://cdn.example.test/media"
})
class MediaUploadIntegrationTest extends EmbeddedPostgresSupport {

    /**
     * 업로드 경로를 임시 디렉터리로 돌린다 — 테스트가 프로젝트 안에 파일을 남기지 않는다.
     *
     * <p>{@code static} 이어야 한다. {@link DynamicPropertySource} 는 스프링 컨텍스트를
     * 만들기 전에 불리고, 그때는 아직 테스트 인스턴스가 없다.
     */
    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) {
        registry.add("app.media.dir", () -> uploadDir.toString());
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminUserRepository adminUserRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        // ⚠️ @TempDir 이 static 이라 <b>클래스 전체가 한 디렉터리를 공유한다.</b> 비우지
        // 않으면 앞 테스트가 올린 파일이 남아, "거절된 파일은 저장되지 않는다"를 보는
        // 테스트가 남의 파일을 보고 실패한다.
        //
        // 디렉터리 자체는 지우지 않는다 — 스프링 컨텍스트가 기동 때 그 경로를 들고 있다.
        for (Path file : filesUnder(uploadDir)) {
            Files.delete(file);
        }

        jdbcTemplate.update("DELETE FROM admin_users WHERE login_id LIKE ?",
                AdminTestFixtures.PREFIX + "%");
        AdminUser manager = AdminTestFixtures.create(
                adminUserRepository, passwordEncoder, "mediamgr", AdminRole.MANAGER);
        token = login(manager.getLoginId());
    }

    @Test
    @DisplayName("PNG 를 올리면 키와 URL 을 돌려주고 그 키 그대로 디스크에 저장된다")
    void storesPng() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("files", "돌반지.png", "image/png", png()))
                        .param("folder", "products")
                        .header("Authorization", bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();

        var uploaded = objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
        String objectKey = uploaded.get("objectKey").asText();

        assertThat(objectKey).startsWith("products/").endsWith(".png");
        // URL 은 설정한 베이스 + 키다. 슬래시가 겹치지 않아야 한다.
        assertThat(uploaded.get("url").asText())
                .isEqualTo("https://cdn.example.test/media/" + objectKey);
        assertThat(uploadDir.resolve(objectKey)).exists();
    }

    @Test
    @DisplayName("올린 파일명은 저장 경로에 쓰이지 않는다 — 경로를 거슬러 올라갈 수 없다")
    void ignoresClientFilename() throws Exception {
        String objectKey = uploadOne("../../etc/passwd.png", png());

        // 서버가 만든 이름이라 올린 파일명의 흔적이 없다.
        assertThat(objectKey).doesNotContain("..").doesNotContain("passwd");
        // 실제 파일도 업로드 디렉터리 안에만 생긴다.
        assertThat(uploadDir.resolve(objectKey).normalize()).startsWith(uploadDir);
    }

    @Test
    @DisplayName("확장자와 Content-Type 을 이미지로 속여도 내용이 이미지가 아니면 거절된다")
    void rejectsDisguisedFile() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("files", "photo.png", "image/png", html))
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_UNSUPPORTED_TYPE"));

        assertThat(filesUnder(uploadDir)).isEmpty();
    }

    @Test
    @DisplayName("SVG 는 받지 않는다 — 스크립트를 품을 수 있는 문서다")
    void rejectsSvg() throws Exception {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script/></svg>"
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("files", "logo.svg", "image/svg+xml", svg))
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_UNSUPPORTED_TYPE"));
    }

    @Test
    @DisplayName("한 장이라도 이미지가 아니면 아무것도 저장하지 않는다")
    void storesNothingWhenOneFileIsBad() throws Exception {
        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("files", "good.png", "image/png", png()))
                        .file(new MockMultipartFile("files", "bad.png", "image/png",
                                "not an image".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest());

        // 먼저 온 PNG 까지 함께 거절된다. 그렇지 않으면 상품에 연결되지 않은 파일이
        // 디스크에 쌓이고, 그것을 지우는 사람이 없다.
        assertThat(filesUnder(uploadDir)).isEmpty();
    }

    @Test
    @DisplayName("허용 목록 밖의 folder 는 거절된다")
    void rejectsUnknownFolder() throws Exception {
        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("files", "a.png", "image/png", png()))
                        .param("folder", "../../etc")
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest());

        assertThat(filesUnder(uploadDir)).isEmpty();
    }

    @Test
    @DisplayName("로그인하지 않으면 올릴 수 없다")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("files", "a.png", "image/png", png())))
                .andExpect(status().isUnauthorized());

        assertThat(filesUnder(uploadDir)).isEmpty();
    }

    @Test
    @DisplayName("상한을 넘는 장수는 거절된다")
    void rejectsTooManyFiles() throws Exception {
        // ⚠️ header() 를 먼저 부르면 MockHttpServletRequestBuilder 로 좁혀져 file() 이
        // 사라진다. 파일을 모두 붙인 뒤에 헤더를 얹는다.
        var request = multipart("/api/admin/media");
        for (int i = 0; i <= 10; i++) {
            request = request.file(new MockMultipartFile("files", i + ".png", "image/png", png()));
        }

        mockMvc.perform(request.header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEDIA_TOO_MANY"));
    }

    @Test
    @DisplayName("ImageKind 는 앞머리 바이트로 네 형식만 가려낸다")
    void sniffsKnownFormats() {
        assertThat(ImageKind.sniff(png())).isEqualTo(ImageKind.PNG);
        assertThat(ImageKind.sniff(jpeg())).isEqualTo(ImageKind.JPEG);
        assertThat(ImageKind.sniff(gif())).isEqualTo(ImageKind.GIF);
        assertThat(ImageKind.sniff(webp())).isEqualTo(ImageKind.WEBP);

        // RIFF 컨테이너지만 WEBP 가 아닌 것(WAV)은 통과하지 않는다 — 앞 4바이트만
        // 보면 소리 파일이 이미지로 들어온다.
        assertThat(ImageKind.sniff(riff("WAVE"))).isNull();
        // 12바이트가 안 되면 판정하지 않는다. 추측하지 않고 null 을 낸다.
        assertThat(ImageKind.sniff(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})).isNull();
        assertThat(ImageKind.sniff(null)).isNull();
    }

    // =================================================================
    // 도우미
    // =================================================================

    private String uploadOne(String filename, byte[] content) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("files", filename, "image/png", content))
                        .header("Authorization", bearer()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get(0).get("objectKey").asText();
    }

    /** 디렉터리 아래의 파일 전부. 저장되지 말아야 할 것이 저장됐는지 확인하는 데 쓴다. */
    private static List<Path> filesUnder(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    /**
     * 진짜 1x1 PNG 바이트.
     *
     * <p>매직 넘버만 맞춘 가짜를 쓰지 않는다 — 그러면 "앞 8바이트만 본다"는 현재 구현에
     * 테스트가 묶여서, 나중에 이미지 라이브러리로 검사를 강화할 때 통째로 고쳐야 한다.
     */
    private static byte[] png() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH"
                        + "/q842iQAAAABJRU5ErkJggg==");
    }

    private static byte[] jpeg() {
        byte[] bytes = new byte[16];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xE0;
        return bytes;
    }

    private static byte[] gif() {
        byte[] bytes = new byte[16];
        byte[] header = "GIF89a".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, bytes, 0, header.length);
        return bytes;
    }

    private static byte[] webp() {
        return riff("WEBP");
    }

    /** {@code RIFF} + 길이 4바이트 + 네 글자 형식 코드. */
    private static byte[] riff(String format) {
        byte[] bytes = new byte[16];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
        System.arraycopy(format.getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 4);
        return bytes;
    }

    private String login(String loginId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "loginId", loginId, "password", AdminTestFixtures.PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String bearer() {
        return "Bearer " + token;
    }
}
