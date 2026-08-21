package com.gnagnoohc.scms.global.common.service;

import com.gnagnoohc.scms.global.common.dto.NotificationReadStatus;
import com.gnagnoohc.scms.global.common.dto.NotificationResponseDTO;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.common.entity.Notification;
import com.gnagnoohc.scms.global.common.repository.NotificationRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * FE 알림함(벨 아이콘) API 전용 서비스. 알림 "발송"은 NotificationSender/NotificationSenderImpl
 * 쪽 책임이고, 여기는 로그인한 사용자 본인의 알림을 조회/읽음 처리하는 쪽만 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public PageResponse<NotificationResponseDTO> list(Integer userId, NotificationReadStatus readStatus, Pageable pageable) {
        Page<Notification> page = readStatus == NotificationReadStatus.UNREAD
                ? notificationRepository.findByRecipientUser_UserIdAndReadAtIsNull(userId, pageable)
                : notificationRepository.findByRecipientUser_UserId(userId, pageable);
        return PageResponse.from(page.map(NotificationResponseDTO::from));
    }

    // 벨 아이콘은 개수가 아니라 빨간 점(안읽은 알림 유무)만 표시하므로 boolean으로 충분하다.
    public boolean hasUnread(Integer userId) {
        return notificationRepository.existsByRecipientUser_UserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(Integer notificationId, Integer userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        // 본인 알림만 읽음 처리할 수 있다 — 다른 사람 알림 id를 넣어도 존재 여부를 알려주지 않기 위해
        // FORBIDDEN이 아니라 NOT_FOUND로 응답한다.
        if (!notification.getRecipientUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        notification.markRead(Instant.now());
    }

    @Transactional
    public void markAllRead(Integer userId) {
        notificationRepository.markAllAsRead(userId, Instant.now());
    }
}
