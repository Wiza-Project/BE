package com.gnagnoohc.scms.global.common.service;

import com.gnagnoohc.scms.global.common.entity.AuditLog;
import com.gnagnoohc.scms.global.common.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 감사 로그만을 위한 독립 트랜잭션 경계. */
@Service
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditLog auditLog) {
        // flush까지 수행해 제약조건 오류 등을 이 경계 안에서 감지한다.
        auditLogRepository.saveAndFlush(auditLog);
    }
}
