package com.hanseongge.global.auth;

import com.hanseongge.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** 권한 부족(403)을 표준 에러 응답으로 내려준다. */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        SecurityResponseWriter.write(response, ErrorCode.FORBIDDEN);
    }
}
