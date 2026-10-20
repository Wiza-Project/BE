package com.gnagnoohc.scms.global.common.repository;

import com.gnagnoohc.scms.global.common.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    // ── 내 알림 목록 조회 ────────────────────────────────────────────────────
    Page<Notification> findByRecipientUser_UserId(Integer userId, Pageable pageable);

    Page<Notification> findByRecipientUser_UserIdAndReadAtIsNull(Integer userId, Pageable pageable);

    // 벨 아이콘 뱃지용. 뱃지는 개수가 아니라 빨간 점(존재 여부)만 보여주면 되므로
    // COUNT 전체 스캔 대신 EXISTS(LIMIT 1)로 끝나는 이 방식이 더 가볍다.
    boolean existsByRecipientUser_UserIdAndReadAtIsNull(Integer userId);

    // ── 전체 읽음 처리 ───────────────────────────────────────────────────────
    // 알림이 수십~수백 건이어도 건마다 엔티티를 읽어와 dirty checking으로 반영하지 않고
    // 한 번의 UPDATE로 끝내기 위한 벌크 쿼리 (AppUserRepository.recordSuccessfulLogin과 동일 패턴).
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :readAt WHERE n.recipientUser.userId = :userId AND n.readAt IS NULL")
    int markAllAsRead(@Param("userId") Integer userId, @Param("readAt") Instant readAt);
}
