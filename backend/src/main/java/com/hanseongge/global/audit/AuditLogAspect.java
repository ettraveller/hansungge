package com.hanseongge.global.audit;

import com.hanseongge.domain.audit.dto.AuditRecord;
import com.hanseongge.domain.audit.entity.ActorType;
import com.hanseongge.domain.audit.service.AuditLogService;
import com.hanseongge.global.auth.AdminPrincipal;
import com.hanseongge.global.auth.RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * {@link AuditLog} 어드바이스 — 감사 기록을 서비스 코드 밖으로 빼낸다.
 *
 * <h2>왜 AOP 인가</h2>
 * 감사 기록을 서비스 본문에 직접 쓰면, 새 기능을 추가하는 사람이 그 다섯 줄을 빠뜨리는
 * 순간 조용히 기록이 사라진다. 빠뜨렸다는 사실은 사고가 난 뒤에야 드러난다.
 * 어노테이션 한 줄로 만들어 두면 "붙였는가"를 코드리뷰에서 눈으로 확인할 수 있다.
 *
 * <h2>감사 기록이 업무를 깨뜨리지 않는다</h2>
 * 어드바이스에서 나는 예외(SpEL 오타 등)는 삼키고 WARN 만 남긴다. 감사 표현식 오타로
 * 관리자 계정 생성이 실패하는 것은 과한 대가다. 기록 자체의 트랜잭션 분리는
 * {@link AuditLogService#record} 가 담당한다.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final String NULL_EXPRESSION = "null";

    private final AuditLogService auditLogService;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        // 이전 요청이 남긴 값이 섞이지 않도록, 대상 메서드 진입 전에 비운다.
        AuditContext.clear();

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable error) {
            if (auditLog.recordFailure()) {
                safeRecord(joinPoint, auditLog, null, error);
            } else {
                AuditContext.clear();
            }
            throw error;
        }

        safeRecord(joinPoint, auditLog, result, null);
        return result;
    }

    private void safeRecord(ProceedingJoinPoint joinPoint, AuditLog auditLog,
                            Object result, Throwable error) {
        try {
            record(joinPoint, auditLog, result, error);
        } catch (Exception e) {
            log.warn("[AUDIT] 어드바이스 처리 실패. action={} — 업무 처리는 계속합니다.",
                    auditLog.action(), e);
        } finally {
            AuditContext.clear();
        }
    }

    private void record(ProceedingJoinPoint joinPoint, AuditLog auditLog,
                        Object result, Throwable error) {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        EvaluationContext context = evaluationContext(method, joinPoint.getArgs(), result);

        boolean failed = error != null;
        String action = failed ? auditLog.action() + "_FAILED" : auditLog.action();
        Object after = failed
                // 실패는 "무엇이 되었는가"가 없다. 대신 왜 실패했는지를 남긴다.
                ? java.util.Map.of("error", error.getClass().getSimpleName(),
                                   "message", String.valueOf(error.getMessage()))
                : evaluate(auditLog.after(), context, false);

        HttpServletRequest request = RequestMetadata.currentRequest();

        AuditRecord.Builder builder = AuditRecord.builder(action)
                .before(AuditContext.consumeBefore())
                .after(after)
                .request(RequestMetadata.ip(request), RequestMetadata.userAgent(request));

        applyActor(builder);

        if (!auditLog.targetType().isBlank()) {
            // 실패했을 때는 targetId 식이 #result 를 참조해 풀리지 않는 것이 정상이다.
            // 그 이유로 감사 기록 자체를 버리지 않는다 — 대상이 비더라도 "시도했다"는
            // 사실이 남아야 한다.
            Object targetId = evaluate(auditLog.targetId(), context, failed);
            builder.target(auditLog.targetType(),
                    targetId == null ? null : String.valueOf(targetId));
        }

        auditLogService.record(builder.build());
    }

    /**
     * 현재 인증 주체를 actor 로 넣는다.
     *
     * <p>인증이 없는 상태에서 실행되는 경우(배치, 부트스트랩)는 {@code SYSTEM} 으로 남긴다.
     * "행위자 미상"으로 비워두면 나중에 로그를 읽는 사람이 유실인지 시스템 작업인지 알 수 없다.
     */
    private void applyActor(AuditRecord.Builder builder) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal admin) {
            builder.actor(ActorType.ADMIN, admin.loginId());
        } else {
            builder.actor(ActorType.SYSTEM, "SYSTEM");
        }
    }

    private EvaluationContext evaluationContext(Method method, Object[] args, Object result) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] names = parameterNameDiscoverer.getParameterNames(method);
        if (names != null) {
            for (int i = 0; i < names.length && i < args.length; i++) {
                context.setVariable(names[i], args[i]);
            }
        }
        context.setVariable("result", result);
        return context;
    }

    /**
     * SpEL 평가. 식이 풀리지 않아도 예외를 밖으로 내보내지 않는다.
     *
     * <p>식 하나가 실패했다고 감사로그 한 줄을 통째로 버리면, 정작 기록이 가장 필요한
     * 실패 사례가 사라진다. 값만 비우고 기록은 남긴다.
     *
     * @param lenient 대상 메서드가 실패한 상황이라 식이 안 풀리는 것이 정상인 경우.
     *                false 인데 실패하면 설정 오류이므로 WARN 으로 드러낸다.
     */
    private Object evaluate(String expression, EvaluationContext context, boolean lenient) {
        if (expression == null || expression.isBlank() || NULL_EXPRESSION.equals(expression.trim())) {
            return null;
        }
        try {
            Expression parsed = expressionParser.parseExpression(expression);
            return parsed.getValue(context);
        } catch (Exception e) {
            if (lenient) {
                log.debug("[AUDIT] 실패 경로에서 식을 평가할 수 없습니다: {} ({})", expression, e.getMessage());
            } else {
                log.warn("[AUDIT] 감사 표현식 평가 실패: {} — 해당 값을 비웁니다.", expression, e);
            }
            return null;
        }
    }
}
