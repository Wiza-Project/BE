package com.gnagnoohc.scms.global.error;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /*
     * 실패 응답 바디에 함께 실어 보낼 부가 정보(예: 로그인 실패 시 잔여 시도 횟수)
     */
    private final Object data;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    /** 메시지는 ErrorCode 기본값을 그대로 쓰되, 응답 바디에 data 만 추가로 실어 보내고 싶을 때. */
    public static BusinessException withData(ErrorCode errorCode, Object data) {
        return new BusinessException(errorCode, errorCode.getMessage(), data);
    }
}
