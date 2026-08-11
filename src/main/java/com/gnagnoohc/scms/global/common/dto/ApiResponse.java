package com.gnagnoohc.scms.global.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 API 의 공통 응답 포맷.
 * 프론트가 응답 형태를 하나만 알면 되도록 고정합니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String code,
        String message
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, null);
    }

    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(false, null, code, message);
    }
}
