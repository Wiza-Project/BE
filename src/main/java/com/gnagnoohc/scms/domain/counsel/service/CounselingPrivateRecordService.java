package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingPrivateRecordResponse;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingAssignment;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingPrivateRecord;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSession;
import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
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

    private final CounselUserRepository counselUserRepository;
    private final CounselingSessionRepository counselingSessionRepository;
    private final CounselingAssignmentRepository counselingAssignmentRepository;
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
        // 배정 종료(예약 취소)가 예약 행만 잠그고 이 회기 행과는 다른 행이라 직렬화되지 않는다.
        // 배정 행도 잠가야 "활성 여부를 확인한 시점"이 커밋까지 유효함을 보장한다. session.getCounselingAssignment()의
        // isOwnedBy/isActive를 먼저 호출하면 프록시가 초기화돼 이 잠금으로도 필드가 갱신되지 않을 수 있으므로,
        // 이 잠금 조회가 배정에 대한 첫 접근이어야 한다(CounselingSessionService.complete()와 같은 패턴).
        Integer assignmentId = session.getCounselingAssignment().getCounselingAssignmentId();
        CounselingAssignment assignment = counselingAssignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!assignment.isOwnedBy(counselorId)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
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

    private void ensureActiveCounselor(Integer counselorId) {
        if (!counselUserRepository.isActiveCounselor(counselorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
