package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingPrivateRecordResponse;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingAssignment;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingPrivateRecord;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSession;
import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
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

    private final CounselUserRepository counselUserRepository;
    private final CounselingSessionRepository counselingSessionRepository;
    private final CounselingPrivateRecordRepository counselingPrivateRecordRepository;

    /**
     * 조회는 현재 활성 배정 담당자뿐 아니라 과거(종료된) 배정 담당자도 허용한다 — 자신이 작성한
     * 기록의 사후 열람은 막을 이유가 없기 때문이다. 접근 사유(ACTIVE_ASSIGNMENT_WORK /
     * PAST_ASSIGNMENT_DOCUMENTATION)는 감사로그용으로만 쓰고 응답에는 포함하지 않는다.
     */
    public CounselingPrivateRecordResponse getRecord(Integer sessionId, Integer counselorId) {
        // TODO(common-audit): ensureActiveCounselor 실패 시에도 READ_PRIVATE_RECORD 실패 — actorUserId=counselorId,
        // resourceType=COUNSELING_SESSION, resourceId=sessionId, actionCode=READ_PRIVATE_RECORD,
        // actionResult=FAILURE. privateContent 전달 금지.
        ensureActiveCounselor(counselorId);
        // TODO(common-audit): READ_PRIVATE_RECORD 실패 — actorUserId=counselorId, resourceType=COUNSELING_SESSION,
        // resourceId=sessionId, actionCode=READ_PRIVATE_RECORD, actionResult=FAILURE. privateContent 전달 금지.
        CounselingSession session = counselingSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.getCounselingAssignment().isOwnedBy(counselorId)) {
            // TODO(common-audit): READ_PRIVATE_RECORD 실패 — actorUserId=counselorId, resourceType=COUNSELING_SESSION,
            // resourceId=sessionId, actionCode=READ_PRIVATE_RECORD, actionResult=FAILURE. privateContent 전달 금지.
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }

        // 감사로그의 accessReason 값. active면 현재 담당자의 업무 조회, 아니면 과거 담당자의 기록 열람이다.
        boolean active = session.getCounselingAssignment().isActive();

        Instant now = Instant.now();
        CounselingPrivateRecord record = counselingPrivateRecordRepository
                .findByCounselingSessionCounselingSessionId(sessionId)
                .orElse(null);
        boolean canSaveDraft = canSaveDraft(session, record, now);
        boolean canConfirm = canConfirm(session, record, now);
        CounselingPrivateRecordResponse response = CounselingPrivateRecordResponse.from(
                sessionId, record, canSaveDraft, canConfirm
        );

        // TODO(common-audit): READ_PRIVATE_RECORD 성공 — actorUserId=counselorId, resourceType=COUNSELING_SESSION,
        // resourceId=sessionId, actionCode=READ_PRIVATE_RECORD, actionResult=SUCCESS,
        // accessReason=(active?ACTIVE_ASSIGNMENT_WORK:PAST_ASSIGNMENT_DOCUMENTATION). privateContent 전달 금지.
        return response;
    }

    /**
     * 초안 저장. 첫 저장은 새 행을 만들고, 이후 저장은 같은 행을 수정한다(versionNo=1 고정).
     * 확정된 기록의 수정은 CounselingPrivateRecord.updateContent()가 S009로 막는다.
     */
    @Transactional
    public CounselingPrivateRecordResponse saveDraft(Integer sessionId, String privateContent, Integer counselorId) {
        ensureActiveCounselor(counselorId);
        CounselingSession session = counselingSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.getCounselingAssignment().isOwnedBy(counselorId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (!session.getCounselingAssignment().isActive()) {
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
        ensureActiveCounselor(counselorId);
        CounselingSession session = counselingSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.getCounselingAssignment().isOwnedBy(counselorId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
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

    private boolean canSaveDraft(CounselingSession session, CounselingPrivateRecord record, Instant now) {
        CounselingAssignment assignment = session.getCounselingAssignment();
        return assignment.isActive() && session.isPrivateDraftAllowed(now) && (record == null || !record.isConfirmed());
    }

    private boolean canConfirm(CounselingSession session, CounselingPrivateRecord record, Instant now) {
        CounselingAssignment assignment = session.getCounselingAssignment();
        return assignment.isActive() && session.isPrivateConfirmAllowed(now) && record != null && !record.isConfirmed();
    }

    private void ensureActiveCounselor(Integer counselorId) {
        if (!counselUserRepository.isActiveCounselor(counselorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
