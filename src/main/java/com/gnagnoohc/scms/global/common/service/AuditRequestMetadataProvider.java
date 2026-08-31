package com.gnagnoohc.scms.global.common.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** HTTP 요청이 있는 경우에만 감사 로그의 IP와 User-Agent를 안전하게 가져온다. */
@Component
public class AuditRequestMetadataProvider {

    private static final int IP_ADDRESS_MAX_LENGTH = 45;
    private static final int USER_AGENT_MAX_LENGTH = 500;

    public AuditRequestMetadata current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return new AuditRequestMetadata(null, null);
        }

        HttpServletRequest request = servletAttributes.getRequest();
        return new AuditRequestMetadata(
                truncate(request.getRemoteAddr(), IP_ADDRESS_MAX_LENGTH),
                truncate(request.getHeader("User-Agent"), USER_AGENT_MAX_LENGTH)
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
