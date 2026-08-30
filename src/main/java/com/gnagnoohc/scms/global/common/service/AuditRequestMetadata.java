package com.gnagnoohc.scms.global.common.service;

/**
 * 감사 로그에 남길 요청 단말 정보다. IP는 신뢰 프록시 정책을 별도로 설정하기 전까지
 * 서블릿 컨테이너가 알려 준 원격 주소만 사용하며, X-Forwarded-For는 신뢰하지 않는다.
 */
public record AuditRequestMetadata(String ipAddress, String userAgent) {
}
