package com.hanseongge.global.config;

import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.global.auth.AdminJwtAuthenticationFilter;
import com.hanseongge.domain.user.repository.UserRepository;
import com.hanseongge.global.auth.AdminJwtProvider;
import com.hanseongge.global.auth.JsonAccessDeniedHandler;
import com.hanseongge.global.auth.JsonAuthenticationEntryPoint;
import com.hanseongge.global.auth.LoginRateLimitFilter;
import com.hanseongge.global.auth.UserJwtAuthenticationFilter;
import com.hanseongge.global.auth.UserJwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 보안 설정 — 사용자 체인과 관리자 체인을 분리한다 (설계서 §10).
 *
 * <p>관리자 체인이 @Order(1) 로 {@code /api/admin/**} 을 먼저 가로챈다. 이 체인에만
 * audience="admin" 토큰 필터가 붙으므로, 사용자 토큰(STEP 6, aud="user")으로 관리자
 * API 를 호출하는 경로가 <b>경로 단위와 토큰 단위 양쪽에서</b> 막힌다.
 *
 * <p>권한 등급 검사는 여기(URL 단위)가 아니라 {@code @PreAuthorize}(메서드 단위)에서 한다.
 * URL 패턴으로 등급을 나누면 엔드포인트가 늘어날 때마다 이 파일과 컨트롤러 두 곳을
 * 맞춰야 하고, 어긋나는 순간을 아무도 눈치채지 못한다.
 *
 * @see MethodSecurityConfig 권한 등급 계층
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppProperties appProperties;
    private final AdminJwtProvider adminJwtProvider;
    private final AdminUserRepository adminUserRepository;
    private final UserJwtProvider userJwtProvider;
    private final UserRepository userRepository;

    /** 관리자 체인 — 사용자 체인보다 먼저 매칭된다. */
    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/admin/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 로그인·재발급은 토큰이 없는 상태에서 호출된다.
                        // 재발급의 자격 증명은 Refresh 토큰 자체이므로 서비스에서 검증한다.
                        .requestMatchers(HttpMethod.POST,
                                "/api/admin/auth/login",
                                "/api/admin/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                // Rate Limit 을 인증보다 앞에 둔다. 인증을 먼저 시도하면 제한을 걸어야 할
                // 요청까지 BCrypt 검증 비용을 치르게 된다.
                .addFilterBefore(loginRateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(adminJwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                        .accessDeniedHandler(new JsonAccessDeniedHandler()));

        return http.build();
    }

    /** 사용자(쇼핑몰) 체인. */
    @Bean
    @Order(2)
    public SecurityFilterChain userFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Bearer 토큰 기반 무상태 API 라 CSRF 토큰을 두지 않는다.
                //
                // Refresh 토큰만 쿠키로 오가지만 SameSite=Lax 라 교차 사이트 POST 에는
                // 실리지 않는다 — 공격자 페이지가 /api/v1/auth/refresh 를 호출해도 쿠키가
                // 가지 않아 실패한다. 쿠키 경로도 /api/v1/auth 로 좁혀 두었다.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(f -> f.deny()))
                // 회원 토큰(aud=user) 인증. 토큰이 없으면 아무 일도 하지 않으므로
                // 상품·시세 같은 공개 조회에는 영향이 없다.
                .addFilterBefore(userJwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // --- 공개 ---
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/health",
                                "/api/v1/prices/**",
                                "/api/v1/categories/**",
                                "/api/v1/products/**",
                                "/api/v1/notices/**",
                                "/api/v1/news/**",
                                "/api/v1/faqs/**",
                                "/api/v1/events/**",
                                "/api/v1/stores/**",
                                // 문의는 목록·상세만 공개다. 쓰기(POST/PUT/DELETE)는 아래
                                // 포괄 규칙에 걸려 인증이 필요하다 — GET 만 여기 두는 이유다.
                                //
                                // 다른 사람의 문의와 답변이 보여야 같은 질문을 또 하지 않는다.
                                // 대신 작성자명은 서버가 가려서 내보내고(PrivacyMask), 비밀글은
                                // 제목만 보이며 본문은 작성자·관리자만 받는다.
                                "/api/v1/qnas",
                                "/api/v1/qnas/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // --- 문서 / 상태 (prod 에서는 SwaggerConfig 자체가 비활성) ---
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // --- 그 외 사용자 API 는 인증 필요 ---
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                        .accessDeniedHandler(new JsonAccessDeniedHandler()));

        return http.build();
    }

    /**
     * 필터를 {@code @Bean} 으로 노출하지 않고 직접 만드는 이유: Spring Boot 는 컨테이너에
     * 있는 {@code Filter} 빈을 서블릿 컨테이너 전역에도 자동 등록한다. 그러면 관리자
     * 체인 전용으로 만든 필터가 <b>쇼핑몰 요청에도 전부 실행된다.</b>
     */
    private AdminJwtAuthenticationFilter adminJwtAuthenticationFilter() {
        return new AdminJwtAuthenticationFilter(adminJwtProvider, adminUserRepository);
    }

    private UserJwtAuthenticationFilter userJwtAuthenticationFilter() {
        return new UserJwtAuthenticationFilter(userJwtProvider, userRepository);
    }

    private LoginRateLimitFilter loginRateLimitFilter() {
        return new LoginRateLimitFilter(appProperties.adminSecurity().loginRatePerMinute());
    }

    /**
     * BCrypt cost 12 (설계서 §15). 관리자 비밀번호 전용 —
     * 일반 사용자는 OAuth2 만 쓰므로 비밀번호를 보관하지 않는다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(appProperties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(true);   // Refresh 토큰 쿠키(STEP 6) 대비
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
