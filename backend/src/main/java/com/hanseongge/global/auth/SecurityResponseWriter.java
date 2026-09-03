package com.hanseongge.global.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.global.error.ErrorCode;
import com.hanseongge.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 시큐리티 필터 단계의 오류 응답 생성기.
 *
 * <p>필터는 {@code @RestControllerAdvice} 바깥에서 동작하므로 GlobalExceptionHandler 를
 * 타지 못한다. 그래서 같은 {@link ErrorResponse} 모양을 여기서 직접 만든다 —
 * 클라이언트 입장에서는 401/403 도 다른 오류와 동일한 형태여야 한다.
 */
public final class SecurityResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

    private SecurityResponseWriter() {
    }

    public static void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        OBJECT_MAPPER.writeValue(response.getWriter(), ErrorResponse.of(errorCode));
    }
}
