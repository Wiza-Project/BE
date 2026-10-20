package com.gnagnoohc.scms.global.common.controller;

import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.common.dto.NotificationReadStatus;
import com.gnagnoohc.scms.global.common.dto.NotificationResponseDTO;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.common.service.NotificationService;
import com.gnagnoohc.scms.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "로그인한 사용자 본인의 알림 조회/읽음 처리")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록 조회", description = "readStatus=UNREAD면 안읽은 알림만, 기본값(ALL)이면 전체를 최신순으로 조회합니다.")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponseDTO>> list(
            @RequestParam(defaultValue = "ALL") NotificationReadStatus readStatus,
            @AuthenticationPrincipal AuthUser authUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(notificationService.list(authUser.getId(), readStatus, pageable));
    }

    @Operation(summary = "안읽은 알림 존재 여부 조회", description = "벨 아이콘에 빨간 점을 표시할지 여부만 가볍게 반환합니다(개수 아님).")
    @GetMapping("/has-unread")
    public ApiResponse<Boolean> hasUnread(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(notificationService.hasUnread(authUser.getId()));
    }

    @Operation(summary = "알림 읽음 처리", description = "본인 알림만 처리할 수 있습니다.")
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@PathVariable Integer notificationId, @AuthenticationPrincipal AuthUser authUser) {
        notificationService.markRead(notificationId, authUser.getId());
        return ApiResponse.ok();
    }

    @Operation(summary = "내 알림 전체 읽음 처리")
    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal AuthUser authUser) {
        notificationService.markAllRead(authUser.getId());
        return ApiResponse.ok();
    }
}
