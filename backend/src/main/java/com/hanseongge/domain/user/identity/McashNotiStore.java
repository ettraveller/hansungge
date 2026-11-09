package com.hanseongge.domain.user.identity;

import com.hanseongge.global.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * mcash 노티로 먼저 도착한 신원을, 뒤따라오는 화면 전환(Okurl)이 찾아갈 때까지 들고 있는 곳.
 *
 * <h2>왜 필요한가</h2>
 * 이니시스는 결과를 <b>우리가 되묻는다</b>. mcash 는 반대로 <b>공급자가 두 번 부른다</b> —
 * 신원은 서버 간 호출(Notiurl)로 먼저 오고, 사용자는 그 뒤에 화면 전환(Okurl)으로 돌아온다.
 * 그래서 먼저 온 신원을 거래번호로 묶어 두었다가, 돌아온 사용자에게 돌려준다.
 *
 * <h2>왜 DB 가 아닌가</h2>
 * 여기 담기는 값에는 <b>CI 원본</b>이 들어 있다. 저장하지 않는다는 것이 우리 규칙이라
 * (2024-08-14 시행 정보통신망법 §23조의5·6) 디스크에 닿지 않는 곳에 둔다. 수명은 몇 초,
 * 길어야 본인확인 유효시간이다.
 *
 * <p>ponytail: 단일 인스턴스 전제다. API 서버를 늘리면 노티를 받은 서버와 사용자가 돌아온
 * 서버가 달라져 못 찾는다. 그때 Redis 로 옮긴다 — 그 전에는 맵 하나면 된다.
 *
 * <p>서버를 재시작하면 비어 있다. 그 사이에 걸친 한 명은 본인확인을 다시 하게 된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.identity.provider", havingValue = McashIdentityVerificationProvider.CODE)
class McashNotiStore {

    private final Map<String, Entry> byTradeId = new ConcurrentHashMap<>();
    private final Duration ttl;

    McashNotiStore(AppProperties appProperties) {
        this.ttl = Duration.ofMinutes(appProperties.signUp().identityTtlMinutes());
    }

    private record Entry(IdentityVerificationResult result, Instant expiresAt) {
    }

    void put(String tradeId, IdentityVerificationResult result) {
        sweep();
        byTradeId.put(tradeId, new Entry(result, Instant.now().plus(ttl)));
    }

    /**
     * 한 번만 꺼낼 수 있다. 꺼내는 순간 지운다 — 같은 노티를 두 번 쓰는 경로를 막는다.
     */
    Optional<IdentityVerificationResult> take(String tradeId) {
        Entry entry = byTradeId.remove(tradeId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            log.info("[IDENTITY] mcash 노티가 만료됐다. tradeId={}", tradeId);
            return Optional.empty();
        }
        return Optional.of(entry.result());
    }

    /**
     * 짝을 찾지 못한 노티를 치운다.
     *
     * <p>사용자가 인증을 마치고 창을 닫아 버리면 Okurl 이 오지 않아 항목이 남는다.
     * 별도 스케줄러를 두지 않는다 — 다음 노티가 올 때 치우면 충분하고, 들어오는 노티가
     * 없으면 치울 것도 없다.
     */
    private void sweep() {
        Instant now = Instant.now();
        byTradeId.values().removeIf(entry -> entry.expiresAt().isBefore(now));
    }
}
