package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingPrivateRecordResponse;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingAssignment;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingPrivateRecord;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSession;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingAssignmentRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingPrivateRecordRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingSessionRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 비공개 상담 기록(원문)의 조회·초안 저장·확정을 담당한다. 회기당 초안은 한 행만 존재하고,
 * 확정 이후에는 원본을 덮어쓰지 않는다(CounselingPrivateRecord 엔티티가 가드).
 * 타이밍·상태 허용 규칙은 CounselingSession.isPrivateDraftAllowed/isPrivateConfirmAllowed에 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounselingPrivateRecordService {

    private final CounselingSessionRepository counselingSessionRepository;
    private final CounselingAssignmentRepository counselingAssignmentRepository;
    private final CounselingPrivateRecordRepository counselingPrivateRecordRepository;
    private final CounselManagementAccessPolicy counselManagementAccessPolicy;

    /**
     * 조회는 현재 활성 배정 담당자뿐 아니라 과거(종료된) 배정 담당자도 허용한다 — 자신이 작성한
     * 기록의 사후 열람은 막을 이유가 없기 때문이다. 공통 감사 인프라가 접근 사유를 받지 않으므로
     * 활성 배정과 과거 배정을 감사 로그에서 별도로 구분하지 않는다.
     */
    public CounselingPrivateRecordResponse getRecord(Integer sessionId, Integer counselorId) {
        // 감사 로그(COUNSELING_SESSION/READ)는 컨트롤러의 @AuditTrail+@AuditResourceId(AOP)가
        // 이 메서드의 정상 반환을 SUCCESS로, 아래에서 던지는 예외를 FAILURE로 자동 기록한다.
        // 서비스에서 별도로 감사 호출을 추가하지 않는다.
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        CounselingSession session = counselingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.getCounselingAssignment().isOwnedBy(counselorId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        // 원문(privateContent)을 읽기 전에 유형 범위부터 확인한다. 걸리면 아래 조회를 아예 하지 않는다.
        ensureTypeInScope(scope, session);

        Instant now = Instant.now();
        CounselingPrivateRecord record = counselingPrivateRecordRepository
                .findByCounselingSessionCounselingSessionId(sessionId)
                .orElse(null);
        boolean canSaveDraft = canSaveDraft(session, record, now);
        boolean canConfirm = canConfirm(session, record, now);
        return CounselingPrivateRecordResponse.from(sessionId, record, canSaveDraft, canConfirm);
    }

    /**
     * 초안 저장. 첫 저장은 새 행을 만들고, 이후 저장은 같은 행을 수정한다(versionNo=1 고정).
     * 확정된 기록의 수정은 CounselingPrivateRecord.updateContent()가 S009로 막는다.
     */
    @Transactional
    public CounselingPrivateRecordResponse saveDraft(Integer sessionId, String privateContent, Integer counselorId) {
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        // 예약 취소가 "예약 → 배정 → 회기" 순서로 잠그므로, 이 메서드도 회기보다 배정을 먼저 잠가야
        // 두 트랜잭션이 반대 순서로 자원을 기다리다 교착되지 않는다. 잠금 없는 스칼라 조회로 배정 ID를
        // 먼저 식별한 뒤(권한 확인 완료로 간주하지 않음) 배정 → 회기 순서로 잠그고, 잠근 엔티티에서
        // 소유권·유형·상태를 기존과 같은 순서·에러코드로 다시 검증한다.
        Integer assignmentId = counselingSessionRepository
                .findOwnedAssignmentIdBySessionId(sessionId, counselorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        CounselingAssignment assignment = counselingAssignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        CounselingSession session = counselingSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!assignment.isOwnedBy(counselorId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        // 원문을 새로 쓰기 전에 유형 범위부터 확인한다. 여기서 걸리면 초안이 만들어지거나 바뀌지 않는다.
        ensureTypeInScope(scope, assignment.getCounselingReservation().getCounselingType().getTypeCode());
        if (!assignment.isActive()) {
            throw new BusinessException(ErrorCode.PRIVATE_RECORD_STATE_NOT_ALLOWED);
        }
        Instant now = Instant.now();
        if (!session.isPrivateDraftAllowed(now)) {
            throw new BusinessException(ErrorCode.PRIVATE_RECORD_STATE_NOT_ALLOWED);
        }

        CounselingPrivateRecord record = counselingPrivateRecordRepository
                .findByCounselingSessionCounselingSessionId(sessionId)
                .orElse(null);
        if (record == null) {
            record = CounselingPrivateRecord.createDraft(session, privateContent);
            counselingPrivateRecordRepository.save(record);
        } else {
            record.updateContent(privateContent);
        }

        boolean canSaveDraft = canSaveDraft(session, record, now);
        boolean canConfirm = canConfirm(session, record, now);
        return CounselingPrivateRecordResponse.from(sessionId, record, canSaveDraft, canConfirm);
    }

    /** 확정. 초안이 없거나 이미 확정된 경우 S009로 막는다(재확정·초안 없는 확정 방지). */
    @Transactional
    public CounselingPrivateRecordResponse confirm(Integer sessionId, Integer counselorId) {
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        CounselingSession session = counselingSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.getCounselingAssignment().isOwnedBy(counselorId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        // 확정(불변화)하기 전에 유형 범위부터 확인한다.
        ensureTypeInScope(scope, session);
        if (!session.getCounselingAssignment().isActive()) {
            throw new BusinessException(ErrorCode.PRIVATE_RECORD_STATE_NOT_ALLOWED);
        }
        Instant now = Instant.now();
        if (!session.isPrivateConfirmAllowed(now)) {
            throw new BusinessException(ErrorCode.PRIVATE_RECORD_STATE_NOT_ALLOWED);
        }

        CounselingPrivateRecord record = counselingPrivateRecordRepository
                .findByCounselingSessionCounselingSessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRIVATE_RECORD_STATE_NOT_ALLOWED));
        record.confirm(counselorId, now);

        boolean canSaveDraft = canSaveDraft(session, record, now);
        boolean canConfirm = canConfirm(session, record, now);
        return CounselingPrivateRecordResponse.from(sessionId, record, canSaveDraft, canConfirm);
    }

    /**
     * 응답의 canSaveDraft 값. session.isPrivateDraftAllowed()는 회기 상태만 보므로, 배정이 이미
     * 끝났는지(assignment.isActive())는 여기서 따로 확인한다 — 그래야 과거 담당자가 조회 화면에서
     * "저장 가능"으로 잘못 표시되지 않는다.
     */
    private boolean canSaveDraft(CounselingSession session, CounselingPrivateRecord record, Instant now) {
        CounselingAssignment assignment = session.getCounselingAssignment();
        return assignment.isActive() && session.isPrivateDraftAllowed(now) && (record == null || !record.isConfirmed());
    }

    /** canSaveDraft와 같은 이유로 배정 활성 여부를 별도 확인하고, 이미 확정된 기록이면 false다. */
    private boolean canConfirm(CounselingSession session, CounselingPrivateRecord record, Instant now) {
        CounselingAssignment assignment = session.getCounselingAssignment();
        return assignment.isActive() && session.isPrivateConfirmAllowed(now) && record != null && !record.isConfirmed();
    }

    /**
     * 회기(session)를 거쳐 연결된 상담 유형이 현재 역할 범위에서 허용되는지 확인한다.
     * 다른 유형이면 담당자가 아닌 회기와 동일하게 SESSION_NOT_FOUND(S007)로 통일해,
     * "내 담당 회기인데 유형이 안 맞는다"는 사실 자체를 노출하지 않는다.
     */
    private void ensureTypeInScope(CounselManagementAccessPolicy.Scope scope, CounselingSession session) {
        String typeCode = session.getCounselingAssignment()
                .getCounselingReservation()
                .getCounselingType()
                .getTypeCode();
        ensureTypeInScope(scope, typeCode);
    }

    private void ensureTypeInScope(CounselManagementAccessPolicy.Scope scope, String typeCode) {
        // 회기는 항상 DIRECT 유형 일정에서만 만들어지므로 신청 경로는 고정값으로 넘긴다.
        if (!counselManagementAccessPolicy.allows(scope, typeCode, "DIRECT")) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
    }
}
