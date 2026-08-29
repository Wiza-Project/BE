package com.gnagnoohc.scms.global.common.repository;

import com.gnagnoohc.scms.global.common.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 감사 로그는 기록 전용이다. 관리자 조회 기능이 생길 때까지도 save() 외의 변경·삭제
 * 메서드는 서비스에서 노출하지 않는다.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {
}
