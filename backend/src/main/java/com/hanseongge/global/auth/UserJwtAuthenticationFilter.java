package com.hanseongge.global.auth;

import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.repository.UserRepository;
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
 * 회원 Bearer 토큰 인증 필터. 쇼핑몰 체인에 등록된다.
 *
 * <h2>매 요청 회원 행을 읽는 이유</h2>
 * 토큰만 검증하면 <b>방금 정지한 계정이 30분 동안 계속 주문할 수 있다.</b> 조회 1건으로
 * 상태(WITHDRAWN/BLOCKED/DORMANT)를 매번 확인한다. 기본키 단건 조회라 비용이 작고,
 * 인증이 필요한 경로(장바구니·주문·마이페이지)는 어차피 회원 정보를 쓴다.
 *
 * <p>상품·시세 같은 공개 조회는 토큰 없이 오므로 이 조회 자체가 일어나지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
public class UserJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final UserJwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token == null) {
            // 토큰이 없는 것 자체는 이 필터가 판단할 일이 아니다. 인증이 필요한 경로인지는
            // 뒤의 인가 단계가 정하고, 401 은 EntryPoint 가 만든다.
            chain.doFilter(request, response);
            return;
        }

        try {
            Long userId = jwtProvider.parseAccessToken(token);
            authenticate(loadVerifiedUser(userId), request);
        } catch (BusinessException e) {
            SecurityContextHolder.clearContext();
            log.debug("[USER-AUTH] 인증 거부: {} - {}", e.getErrorCode().name(), e.getMessage());
            SecurityResponseWriter.write(response, e.getErrorCode());
            return;
        }

        chain.doFilter(request, response);
    }

    /** 토큰이 유효한 것과 계정이 유효한 것은 다른 문제다. */
    private User loadVerifiedUser(Long userId) {
        Optional<User> found = userRepository.findById(userId);
        if (found.isEmpty()) {
            // 존재 여부를 응답으로 구분해 주지 않는다.
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "존재하지 않는 회원: " + userId);
        }
        User user = found.get();
        user.requireSignInAllowed();
        return user;
    }

    private void authenticate(User user, HttpServletRequest request) {
        UserPrincipal principal = UserPrincipal.from(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(UserPrincipal.AUTHORITY)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(UserPrincipal.class.getName(), principal);
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
