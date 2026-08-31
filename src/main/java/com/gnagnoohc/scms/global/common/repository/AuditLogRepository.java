package com.gnagnoohc.scms.global.common.repository;

import com.gnagnoohc.scms.global.common.entity.AuditLog;
import org.springframework.data.repository.Repository;

/**
 * 감사 로그는 기록 전용이다. {@link Repository} 마커 인터페이스만 상속해
 * delete·deleteAll 등 삭제 계열 메서드를 아예 노출하지 않는다. 다른 빈이 이
 * 리포지토리를 주입받아도 삭제할 방법이 없다.
 */
public interface AuditLogRepository extends Repository<AuditLog, Integer> {

    AuditLog saveAndFlush(AuditLog auditLog);
}
