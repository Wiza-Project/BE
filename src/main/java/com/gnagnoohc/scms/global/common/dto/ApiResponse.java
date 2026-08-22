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

    /**
     * 실패 응답이더라도 부가 정보를 함께 내려줘야 하는 경우(예: 로그인 실패 시 잔여 시도
     * 횟수)를 위한 오버로드입니다. data 가 null이면 위 {@link #fail(String, String)} 과
     * 완전히 동일한 응답 바디가 나갑니다(NON_NULL 직렬화 설정 때문에 data 필드 자체가
     * 생략됩니다).
     */
    public static <T> ApiResponse<T> fail(String code, String message, T data) {
        return new ApiResponse<>(false, data, code, message);
    }
}
