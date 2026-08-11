package com.gnagnoohc.scms.global.common;

import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 프론트-백엔드 연결 확인용. 연동 확인이 끝나면 삭제하세요.
 * SecurityConfig 의 permitAll 목록에 /api/auth/** 만 열려 있으므로
 * 이 엔드포인트는 그 경로 아래에 둡니다.
 */
@Tag(name = "Health", description = "서버 상태 확인")
@RestController
@RequestMapping("/api/auth")
public class HealthController {

    @Operation(summary = "핑", description = "프론트 연동 확인용")
    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of("message", "pong"));
    }
}
