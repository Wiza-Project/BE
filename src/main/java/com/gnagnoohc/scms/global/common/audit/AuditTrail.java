package com.gnagnoohc.scms.global.common.audit;

import com.gnagnoohc.scms.global.common.service.AuditAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 쓰기성 API 메서드에 부착해 감사 로그를 자동 기록하게 하는 어노테이션.
 *
 * <p>행위자는 요청 본문이 아니라 인증된 {@code AuthUser}(메서드 인자 또는 SecurityContext)에서
 * 추출하고, 대상 리소스 ID는 {@link AuditResourceId}로 표시한 인자에서만 가져온다.
 * URI나 응답 객체를 파싱해 값을 추론하지 않는다.</p>
 *
 * <pre>{@code
 * @AuditTrail(resourceType = "PROGRAM_APPLICATION", action = AuditAction.APPROVE)
 * public ApiResponse<?> approve(
 *         @AuditResourceId @PathVariable Integer applicationId,
 *         @AuthenticationPrincipal AuthUser authUser
 * ) {
 *     ...
 * }
 * }</pre>
 *
 * <p>이 어노테이션 자체는 AOP 인프라만 제공한다.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditTrail {

    /** 예: PROGRAM, PROGRAM_APPLICATION, MILEAGE_CLAIM, COUNSELING_RESERVATION */
    String resourceType();

    /** 감사 행위 코드. URI나 HTTP 메서드로 추론하지 않고 명시한다. */
    AuditAction action();
}
