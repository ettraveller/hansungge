package com.hanseongge.global.auth;

import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 관리자 Bearer 토큰 인증 필터. {@code /api/admin/**} 체인에만 등록된다.
 *
 * <h2>매 요청 DB 를 한 번 읽는 이유</h2>
 * 토큰만 검증하면 충분해 보이지만, 그러면 <b>방금 잠근 계정이 30분 동안 계속 일한다.</b>
 * 관리자는 시세·주문·환불을 건드리는 주체라 그 30분을 허용할 수 없다. 조회 1건으로
 * 상태(LOCKED/DISABLED)와 토큰 세대를 매번 확인한다. 관리자 트래픽은 초당 수 건 수준이라
 * 이 비용이 문제가 되지 않는다 — 문제가 될 규모가 되면 그때 캐시를 앞에 둔다.
 */
@Slf4j
@RequiredArgsConstructor
public class AdminJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final AdminJwtProvider jwtProvider;
    private final AdminUserRepository adminUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String token = extractToken(request);
        if (token == null) {
            // 토큰이 없는 것 자체는 이 필터가 판단할 일이 아니다.
            // 인증이 필요한 경로인지는 뒤의 인가 단계가 정하고, 401 은 EntryPoint 가 만든다.
            chain.doFilter(request, response);
            return;
        }

        try {
            AdminTokenClaims claims = jwtProvider.parse(token, AdminTokenType.ACCESS);
            AdminUser admin = loadVerifiedAdmin(claims);
            authenticate(admin, request);
        } catch (BusinessException e) {
            SecurityContextHolder.clearContext();
            log.debug("[ADMIN-AUTH] 인증 거부: {} - {}", e.getErrorCode().name(), e.getMessage());
            SecurityResponseWriter.write(response, e.getErrorCode());
            return;
        }

        chain.doFilter(request, response);
    }

    /** 계정이 지금도 유효한지 확인한다. 토큰이 유효한 것과 계정이 유효한 것은 다른 문제다. */
    private AdminUser loadVerifiedAdmin(AdminTokenClaims claims) {
        Optional<AdminUser> found = adminUserRepository.findById(claims.adminId());
        if (found.isEmpty()) {
            // 삭제된 계정의 토큰. 존재 여부를 응답으로 구분해 주지 않는다.
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "존재하지 않는 관리자 계정: " + claims.adminId());
        }
        AdminUser admin = found.get();

        if (admin.getTokenVersion() != claims.tokenVersion()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID,
                    "토큰 세대 불일치 (로그아웃·비밀번호·권한 변경 이후 토큰): " + admin.getLoginId());
        }
        if (!admin.isActive()) {
            throw new BusinessException(ErrorCode.ADMIN_ACCOUNT_LOCKED,
                    "비활성 계정의 토큰 사용: " + admin.getLoginId() + " status=" + admin.getStatus());
        }
        return admin;
    }

    private void authenticate(AdminUser admin, HttpServletRequest request) {
        AdminPrincipal principal = AdminPrincipal.from(admin);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(principal.authority())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(AdminPrincipal.class.getName(), principal);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
