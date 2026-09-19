package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.domain.user.entity.SocialAccount;
import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.UserStatus;
import com.hanseongge.domain.user.repository.SocialAccountRepository;
import com.hanseongge.domain.user.repository.UserRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.audit.AuditContext;
import com.hanseongge.global.audit.AuditLog;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 회원 관리 (관리자).
 *
 * <h2>이 서비스가 하지 않는 일</h2>
 * <ul>
 *   <li><b>회원 정보를 고치지 않는다.</b> 이름·생년월일·휴대폰은 본인확인 응답으로만
 *       채워지는 값이라, 관리자가 손대는 순간 "확인된 값"이 아니라 "누군가 적어 넣은 값"이
 *       된다. 잘못된 값은 본인확인을 다시 받아 고친다</li>
 *   <li><b>탈퇴시키지 않는다.</b> 탈퇴는 본인의 의사표시다. 이용을 막아야 하면 정지다</li>
 *   <li><b>비밀번호를 초기화하지 않는다.</b> 관리자 계정과 다르다 — 회원에게는 이메일
 *       재설정 경로가 있고, 관리자가 비밀번호를 정해 주면 그 값을 전달하는 과정에서
 *       새어 나간다</li>
 * </ul>
 *
 * <h2>잠금과 정지는 다른 것이다</h2>
 * <table>
 *   <tr><td><b>잠금</b></td><td>연속 로그인 실패로 <b>자동</b>으로 걸린다. 30분 뒤 스스로
 *       풀린다. {@code status} 는 그대로 {@code ACTIVE} 다</td></tr>
 *   <tr><td><b>정지</b></td><td>관리자가 건다. 스스로 풀리지 않는다.
 *       {@code status = BLOCKED}</td></tr>
 * </table>
 * 화면에서 이 둘을 한 버튼으로 묶으면, 잠긴 고객을 풀어 주려다 정지시키는 일이 생긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    // =================================================================
    // 조회
    // =================================================================

    /**
     * 목록. 검색어가 비어 있으면 전체다.
     *
     * <p>검색어 없음을 <b>null 이 아니라 빈 문자열</b>로 넘긴다. 이유는
     * {@link UserRepository#findAdminPage} 주석에 있다 — 타입 없는 null 이
     * {@code lower()} 에 들어가면 PostgreSQL 이 {@code bytea} 로 추론해 질의가 깨진다.
     */
    @Transactional(readOnly = true)
    public Page<User> findPage(String keyword, UserStatus status, Pageable pageable) {
        return userRepository.findAdminPage(likePattern(keyword), phonePattern(keyword), status, pageable);
    }

    /**
     * 휴대폰 검색용 패턴 — 숫자만 남긴다.
     *
     * <p>휴대폰은 숫자만 저장한다(V21). 고객센터에서 "010-1234-5678" 을 그대로 넣으면 하이픈
     * 때문에 아무것도 안 걸린다. 숫자가 없는 검색어(이름·이메일)는 이메일·이름 패턴을 그대로
     * 쓴다 — 어차피 숫자뿐인 번호에는 걸리지 않는다. 숫자만 남기므로 와일드카드가 끼어들 수 없다.
     */
    private static String phonePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        String digits = keyword.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? likePattern(keyword) : "%" + digits + "%";
    }

    /**
     * 검색어 → LIKE 패턴.
     *
     * <h2>와일드카드를 막는 이유</h2>
     * 목록은 이메일·이름·휴대폰을 가려서 내보내는데, 검색은 <b>가리기 전 원본</b>을
     * 대상으로 한다. 그래서 검색어에 {@code %} 나 {@code _} 를 넣을 수 있으면 가려 둔
     * 자리를 한 글자씩 확인하는 도구가 된다.
     *
     * <pre>
     * 화면에 보이는 값        010-****-5678
     * keyword=010_2345  →   걸리면 셋째 자리가 확정된다
     * keyword=%         →   전건이 매칭된다
     * </pre>
     *
     * <p>그렇게 몇십 번만 물어보면 가려 둔 번호가 통째로 복원된다. 그래서 특수문자를
     * escape 해 <b>글자 그대로</b> 찾게 만든다. escape 문자 자체({@code !})도 먼저
     * 치환해야 한다 — 안 그러면 {@code !} 를 넣어 escape 를 무력화할 수 있다.
     */
    private static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        String escaped = keyword.trim()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    /** 상태별 회원 수. 목록 상단 요약에 쓴다. */
    @Transactional(readOnly = true)
    public Map<UserStatus, Long> countByStatus() {
        Map<UserStatus, Long> counts = new LinkedHashMap<>();
        for (UserStatus status : UserStatus.values()) {
            counts.put(status, userRepository.countByStatus(status));
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "회원 없음: " + id));
    }

    /**
     * 상세 조회 — <b>감사로그에 남는다.</b>
     *
     * <p>{@code readOnly} 가 아니다. {@code @AuditLog} 가 별도 트랜잭션으로 기록을 쓰지만,
     * 이 메서드를 읽기 전용으로 두면 의도가 흐려진다 — 여기서 일어나는 일은 "조회"가
     * 아니라 <b>"열람 기록을 남기며 조회"</b>다.
     */
    @Transactional
    @AuditLog(action = AuditActions.MEMBER_VIEW,
              targetType = AuditActions.TARGET_MEMBER,
              // ⚠️ after 를 비워 두면 AOP 가 <b>반환값을 통째로 직렬화</b>한다. 이 메서드는
              // User 를 돌려주므로 이름·이메일·휴대폰이 감사로그 JSONB 에 그대로 박힌다 —
              // 목록을 가려 놓은 것이 무의미해지고, 감사로그를 볼 수 있는 사람이 회원
              // 개인정보를 전부 보게 된다. 실제로 그렇게 기록되는 것을 확인하고 막았다.
              //
              // 남겨야 할 것은 "누가 몇 번 회원을 언제 열었나"이고, 그건 actor·targetId·
              // created_at 에 이미 있다.
              targetId = "#id",
              after = "null")
    public User findForDetail(Long id) {
        return find(id);
    }

    /** 연결된 소셜 제공자. 상세에서 "어떻게 가입했나"를 보여준다. */
    @Transactional(readOnly = true)
    public List<OAuthProviderType> socialProviders(Long userId) {
        return socialAccountRepository.findByUserIdOrderByConnectedAtAsc(userId).stream()
                .map(SocialAccount::getProvider)
                .toList();
    }

    // =================================================================
    // 변경
    // =================================================================

    /**
     * 상태 변경 — 이용정지 · 정지 해제 · 휴면 해제.
     *
     * <p>탈퇴는 양방향으로 막는다. 탈퇴 회원의 상태를 바꾸는 것도, 관리자가 회원을
     * 탈퇴시키는 것도 안 된다. 이유는 {@link ErrorCode#MEMBER_WITHDRAWN_IMMUTABLE} 참조.
     */
    @Transactional
    @AuditLog(action = AuditActions.MEMBER_STATUS_CHANGE,
              targetType = AuditActions.TARGET_MEMBER,
              targetId = "#id",
              after = "{'status': #status.name(), 'reason': #reason}")
    public User changeStatus(Long id, UserStatus status, String reason, String adminLoginId) {
        if (status == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.MEMBER_WITHDRAW_DENIED,
                    "관리자 탈퇴 처리 시도: userId=" + id);
        }

        User user = find(id);
        AuditContext.before(snapshot(user));

        if (user.getStatus() == UserStatus.WITHDRAWN) {
            throw new BusinessException(ErrorCode.MEMBER_WITHDRAWN_IMMUTABLE,
                    "탈퇴 회원 상태 변경 시도: userId=" + id);
        }

        user.changeStatus(status);

        // 정지를 풀 때 잠금이 남아 있으면 "풀어 줬는데 로그인이 안 된다"가 된다.
        // 두 번 요청하게 만들지 않는다 — 관리자 비밀번호 초기화가 잠금을 함께 푸는 것과
        // 같은 판단이다.
        if (status == UserStatus.ACTIVE) {
            user.unlock();
        }

        log.info("[MEMBER] 상태 변경 userId={} status={} reason={} by={}",
                id, status, reason, adminLoginId);
        return user;
    }

    /**
     * 로그인 실패 잠금 해제.
     *
     * <p>{@code status} 는 건드리지 않는다. 잠금은 {@code ACTIVE} 인 채로도 걸리는
     * 별개의 상태라, 여기서 상태까지 바꾸면 정지된 회원이 잠금 해제만으로 되살아난다.
     */
    @Transactional
    @AuditLog(action = AuditActions.MEMBER_UNLOCK,
              targetType = AuditActions.TARGET_MEMBER,
              targetId = "#id",
              after = "{'reason': #reason}")
    public User unlock(Long id, String reason, String adminLoginId) {
        User user = find(id);
        AuditContext.before(snapshot(user));

        user.unlock();
        log.info("[MEMBER] 잠금 해제 userId={} reason={} by={}", id, reason, adminLoginId);
        return user;
    }

    /**
     * 감사로그의 before 스냅샷.
     *
     * <p>이름·이메일·휴대폰을 담지 않는다. 감사로그는 오래 남고 여러 사람이 조회하는
     * 자리라, 여기에 개인정보를 넣으면 <b>가려 놓은 목록과 무관하게</b> 그쪽으로 새어 나간다.
     * 무엇이 바뀌었는지를 알려면 상태와 잠금만 있으면 된다.
     */
    private Map<String, Object> snapshot(User user) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", user.getStatus().name());
        values.put("loginFailCount", user.getLoginFailCount());
        values.put("lockedAt", String.valueOf(user.getLockedAt()));
        return values;
    }

    /** 목록·상세가 잠금 판정에 쓰는 기준 시각. 한 응답 안에서는 같은 값을 쓴다. */
    public OffsetDateTime now() {
        return OffsetDateTime.now();
    }
}
