package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingPrivateRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 비공개 상담 기록 조회·저장을 담당한다. 기록 자체는 잠금이 필요 없다 — 회기 행을 먼저 잠그는
 * CounselingPrivateRecordService의 저장·확정 트랜잭션이 접근을 직렬화한다.
 */
public interface CounselingPrivateRecordRepository extends JpaRepository<CounselingPrivateRecord, Integer> {
    Optional<CounselingPrivateRecord> findByCounselingSessionCounselingSessionId(Integer sessionId);

    /**
     * 공개 결과 서비스가 "이 회기의 비공개 기록이 확정됐는지"만 판정할 때 쓴다. 확정 여부는
     * confirmedAt 값 유무로만 결정되므로 원문(private_content)을 함께 로드하는 엔티티 조회 대신
     * 존재 여부만 반환한다 — 설계상 공개 결과 흐름은 비공개 원문 자체를 조회하지 않아야 하고,
     * 원문을 SELECT 대상에서 빼 두는 편이 민감정보 노출면을 좁힌다.
     */
    @Query("""
            select case when count(r) > 0 then true else false end
            from CounselingPrivateRecord r
            where r.counselingSession.counselingSessionId = :sessionId
              and r.confirmedAt is not null
            """)
    boolean existsConfirmedByCounselingSessionId(@Param("sessionId") Integer sessionId);
}
