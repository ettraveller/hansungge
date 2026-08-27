package com.hanseongge.domain.user.oauth;

import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.global.config.AppProperties;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 제공자 구현체 조회.
 *
 * <p>설정되지 않은 제공자를 <b>없는 것처럼 감추지 않는다.</b> 등록은 되어 있고
 * {@link OAuthProvider#isConfigured()} 가 false 일 뿐이다. 그래야 "카카오 버튼이 왜 없지"의
 * 답이 코드가 아니라 환경변수에 있다는 것이 분명해진다.
 */
@Slf4j
@Component
public class OAuthProviderRegistry {

    private final Map<OAuthProviderType, OAuthProvider> providers = new EnumMap<>(OAuthProviderType.class);
    private final AppProperties.OAuth config;

    public OAuthProviderRegistry(List<OAuthProvider> providerBeans, AppProperties appProperties) {
        providerBeans.forEach(p -> providers.put(p.type(), p));
        this.config = appProperties.oauth();
    }

    @PostConstruct
    void logAvailability() {
        List<String> ready = available().stream().map(Enum::name).toList();
        if (ready.isEmpty()) {
            log.warn("""
                    [OAUTH] 사용 가능한 소셜 로그인이 하나도 없습니다. 회원 로그인이 동작하지 않습니다.
                    앱 등록·검수(TODO A-4)가 끝나면 아래 환경변수를 주입하세요.
                      OAUTH_GOOGLE_CLIENT_ID / OAUTH_GOOGLE_CLIENT_SECRET
                      OAUTH_NAVER_CLIENT_ID  / OAUTH_NAVER_CLIENT_SECRET
                      OAUTH_KAKAO_CLIENT_ID  / OAUTH_KAKAO_CLIENT_SECRET""");
        } else {
            log.info("[OAUTH] 사용 가능한 소셜 로그인: {}", String.join(", ", ready));
        }
    }

    /** 지금 실제로 쓸 수 있는 제공자. 화면은 이 목록만 버튼으로 노출한다. */
    public List<OAuthProviderType> available() {
        return providers.values().stream()
                .filter(OAuthProvider::isConfigured)
                .map(OAuthProvider::type)
                .sorted()
                .toList();
    }

    /**
     * 사용 가능한 제공자를 돌려준다.
     *
     * @throws BusinessException 설정되지 않은 제공자면 503. 404 가 아닌 이유는 존재하지
     *                           않는 것이 아니라 <b>아직 준비되지 않은</b> 것이기 때문이다
     */
    public OAuthProvider require(OAuthProviderType type) {
        OAuthProvider provider = providers.get(type);
        if (provider == null || !provider.isConfigured()) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_UNAVAILABLE,
                    type + " 클라이언트 설정 없음");
        }
        return provider;
    }

    /**
     * 제공자에 등록한 콜백 주소.
     *
     * <p>인가 요청과 토큰 교환 <b>양쪽에서 같은 값</b>이어야 한다. 한쪽만 바뀌면
     * 제공자가 {@code redirect_uri_mismatch} 로 거부한다. 그래서 한 곳에서 만든다.
     */
    public String redirectUri(OAuthProviderType type) {
        String base = config.redirectBaseUrl();
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + "/" + type.name().toLowerCase(Locale.ROOT);
    }
}
