package com.gnagnoohc.scms.global.error;

import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        ErrorCode ec = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.fail(ec.getCode(), ec.getMessage()));
    }
}
