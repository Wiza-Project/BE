package com.gnagnoohc.scms.global.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link AuditTrail}이 기록할 대상 리소스 ID를 명시하는 파라미터 마커.
 *
 * <p>URI 문자열 파싱이나 응답 리플렉션이 아니라, 이 어노테이션이 붙은 인자 값을 그대로 사용한다.
 * Integer, 그 밖의 숫자 타입, 숫자 문자열만 지원한다. 대상 ID를 알 수 없는 API는
 * 이 어노테이션을 생략하면 null로 기록된다.</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditResourceId {
}
