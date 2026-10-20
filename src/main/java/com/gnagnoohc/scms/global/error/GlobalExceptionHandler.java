package com.gnagnoohc.scms.global.error;

import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        log.warn("BusinessException: {} - {}", ec.getCode(), e.getMessage());
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.fail(ec.getCode(), e.getMessage(), e.getData()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.fail(ec.getCode(), message));
    }

    // 빈 본문·문법 오류 JSON의 파싱 예외 메시지에는 요청 본문 일부가 실릴 수 있어 로그에 남기지
    // 않는다. 컨트롤러 메서드 진입 전에 실패하므로 감사 로그 대상도 아니다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        ErrorCode ec = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.fail(ec.getCode(), ec.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        ErrorCode ec = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.fail(ec.getCode(), ec.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileSize(MaxUploadSizeExceededException e) {
        ErrorCode ec = ErrorCode.FILE_UPLOAD_FAILED;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.fail(ec.getCode(), "파일 용량이 허용치를 초과했습니다."));
    }

    // 요청 URL·쿼리스트링·본문은 로그에 남기지 않는다(민감정보가 쿼리스트링에 실려 오는 케이스가
    // 있을 수 있어 catch-all의 log.error("Unhandled exception", e)와 달리 여기서는 로그를 남기지 않는다).
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        ErrorCode ec = ErrorCode.METHOD_NOT_ALLOWED;
        HttpHeaders headers = new HttpHeaders();
        headers.setAllow(e.getSupportedHttpMethods());
        return ResponseEntity.status(ec.getStatus())
                .headers(headers)
                .body(ApiResponse.fail(ec.getCode(), ec.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.fail(ec.getCode(), ec.getMessage()));
    }
}
