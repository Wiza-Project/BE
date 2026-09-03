package com.gnagnoohc.scms.global.common.audit;

import com.gnagnoohc.scms.global.common.service.AuditLogService;
import com.gnagnoohc.scms.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;

/**
 * {@link AuditTrail}이 붙은 메서드 호출을 가로채 성공·실패 감사 로그를 남긴다.
 *
 * <p>행위자는 메서드 인자 중 {@code AuthUser}를 우선 사용하고, 없으면 SecurityContext의
 * 인증 principal에서 찾는다. 둘 다 없으면 null로 기록한다(WP-240 정책상 성공 로그는
 * actorUserId가 필수이므로, 이 경우 성공 로그 기록 자체가 스킵되고 경고만 남는다).</p>
 *
 * <p>감사 로그 기록(및 그 검증) 자체의 예외는 여기서 흡수한다. 업무 메서드의 반환값과
 * 예외는 그대로 통과시켜, AOP가 업무 성공/실패 결과를 절대 바꾸지 않는다.</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggingAspect.class);

    private final AuditLogService auditLogService;

    @Around("@annotation(auditTrail)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditTrail auditTrail) throws Throwable {
        Integer resourceId = extractResourceId(joinPoint);
        Integer actorId = extractActorId(joinPoint);

        try {
            Object result = joinPoint.proceed();
            recordSafely(() -> auditLogService.recordSuccess(
                    actorId, auditTrail.resourceType(), resourceId, auditTrail.action()));
            return result;
        } catch (Throwable e) {
            recordSafely(() -> auditLogService.recordFailure(
                    actorId, auditTrail.resourceType(), resourceId, auditTrail.action()));
            throw e;
        }
    }

    private void recordSafely(Runnable recorder) {
        try {
            recorder.run();
        } catch (RuntimeException e) {
            // 감사 로그 기록 실패가 업무 성공/실패 결과를 바꾸면 안 된다.
            log.warn("감사 로그 기록에 실패했습니다.", e);
        }
    }

    private Integer extractResourceId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(AuditResourceId.class)) {
                return toResourceId(args[i]);
            }
        }
        return null;
    }

    private Integer toResourceId(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.valueOf(str);
            } catch (NumberFormatException e) {
                log.warn("감사 로그 대상 ID를 숫자로 변환할 수 없습니다.");
                return null;
            }
        }
        log.warn("감사 로그 대상 ID로 지원하지 않는 타입입니다: {}", value.getClass());
        return null;
    }

    private Integer extractActorId(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof AuthUser authUser) {
                return authUser.getId();
            }
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
            return authUser.getId();
        }
        return null;
    }
}
