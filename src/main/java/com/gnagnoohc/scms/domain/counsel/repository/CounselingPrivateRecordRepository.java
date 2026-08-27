package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingPrivateRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 비공개 상담 기록 조회·저장을 담당한다. 기록 자체는 잠금이 필요 없다 — 회기 행을 먼저 잠그는
 * CounselingPrivateRecordService의 저장·확정 트랜잭션이 접근을 직렬화한다.
 */
public interface CounselingPrivateRecordRepository extends JpaRepository<CounselingPrivateRecord, Integer> {
    Optional<CounselingPrivateRecord> findByCounselingSessionCounselingSessionId(Integer sessionId);
}
