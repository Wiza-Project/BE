package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.response.CounselorPendingReservationResponse;
import com.gnagnoohc.scms.domain.counsel.dto.request.CounselorProxyReservationRequest;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselorReservationDecisionResponse;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselorReservationDetailResponse;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselorStudentLookupResponse;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingAssignment;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSchedule;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSession;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingType;
import com.gnagnoohc.scms.domain.counsel.event.CounselingReservationDecisionEvent;
import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingAssignmentRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingReservationRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingSessionRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingTypeRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentModuleCode;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentType;
import com.gnagnoohc.scms.domain.user.service.consent.ConsentVerifier;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 상담사가 자신의 일정에 걸린 DIRECT 예약(REQUESTED)을 승인·반려하고, 승인 시 최초 활성 배정을 만든다.
 * 또한 학번으로 활성 학생을 조회하고, 본인 소유의 예약 가능한 DIRECT 일정에 대행 예약을 즉시
 * 승인 상태로 생성한다. 예약 존재 여부는 "대상 없음 / 다른 상담사 일정 / 일정 없는 CENTER 예약"을
 * 구분하지 않고 모두 S003(RESERVATION_NOT_FOUND)으로 묶어, 다른 상담사가 담당하는 예약이 있다는
 * 사실 자체를 노출하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounselorReservationService {

    // 학번 조회 입력 상한. 확정 ERD의 university_no 컬럼 길이(30자)와 맞춘다.
    private static final int MAX_UNIVERSITY_NO_LENGTH = 30;

    private final CounselUserRepository counselUserRepository;
    private final CounselingTypeRepository counselingTypeRepository;
    private final CounselingReservationRepository counselingReservationRepository;
    private final CounselingAssignmentRepository counselingAssignmentRepository;
    private final CounselingSessionRepository counselingSessionRepository;
    private final CounselManagementAccessPolicy counselManagementAccessPolicy;
    private final CounselingScheduleService counselingScheduleService;
    private final ConsentVerifier consentVerifier;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * ST300 단독(CAREER_ONLY) 상담사는 자신의 CS200 예약만 대기 목록에서 봐야 하므로, 조회 조건 자체에
     * careerOnly를 넘겨 다른 유형의 예약 행을 애초에 읽지 않는다.
     */
    public PageResponse<CounselorPendingReservationResponse> getPending(
            Integer counselorId,
            int page,
            int size
    ) {
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        boolean careerOnly = scope == CounselManagementAccessPolicy.Scope.CAREER_ONLY;
        return PageResponse.from(counselingReservationRepository
                .findPendingByCounselor(counselorId, careerOnly, PageRequest.of(page, size))
                .map(CounselorPendingReservationResponse::from));
    }

    /**
     * 상세 조회는 단건이라 조회 조건에서 걸러도 실익이 크지 않지만, 소유권 확인 이후 유형까지
     * 정책으로 검사해 다른 유형 예약이면 담당자가 아닌 예약과 동일하게 RESERVATION_NOT_FOUND로
     * 응답한다(존재 여부를 노출하지 않는 기존 계약을 유지).
     */
    public CounselorReservationDetailResponse getReservation(Integer reservationId, Integer counselorId) {
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        CounselingReservation reservation = counselingReservationRepository
                .findByIdAndCounselor(reservationId, counselorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        ensureTypeInScope(scope, reservation);
        return CounselorReservationDetailResponse.from(reservation);
    }

    /**
     * 학번 완전 일치로 활성 학생 한 명만 조회한다. 존재하지 않음·비활성·비학생을 구분하지 않고
     * 모두 U001로 응답해, 실패 원인으로 다른 계정의 존재 여부를 추측할 수 없게 한다.
     *
     * <p>ST300(지도교수 전용, scope=CAREER_ONLY)은 조회 범위 자체가 자기 지도학생으로 제한된다
     * (설계 5.2). 다른 교수 지도학생·지도교수 미지정·학적 상세 없음도 결과 없음으로 수렴시켜
     * 같은 U001로 응답하고, "지도학생이 아니다"라는 사실을 별도 코드로 노출하지 않는다.</p>
     */
    public CounselorStudentLookupResponse lookupStudent(Integer counselorId, String universityNo) {
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        String trimmed = universityNo == null ? "" : universityNo.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_UNIVERSITY_NO_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        var result = scope == CounselManagementAccessPolicy.Scope.CAREER_ONLY
                ? counselUserRepository.findActiveAdviseeByUniversityNo(trimmed, counselorId)
                : counselUserRepository.findActiveStudentByUniversityNo(trimmed);
        return result.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 예약 행을 잠근 뒤 담당 상담사 본인인지 확인하고 승인 상태로 바꾼 다음, 같은 트랜잭션에서
     * 일정의 담당 상담사(=승인 처리자 본인)를 최초 활성 배정으로 등록한다.
     * approve()/reject()가 REQUESTED가 아니면 예외를 던지므로, 승인 재시도(같은 요청 중복 전송)로
     * 배정이 두 번 생성되지 않는다 — 첫 승인이 커밋된 뒤 재시도가 오면 이미 APPROVED라 S005로 막힌다.
     */
    @Transactional
    public CounselorReservationDecisionResponse approve(Integer reservationId, Integer counselorId) {
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        Instant now = Instant.now();
        CounselingReservation reservation = getOwnedReservationForUpdate(reservationId, counselorId);
        // 예약 행을 잠근 뒤, 상태를 바꾸거나 배정·회기를 만들기 전에 유형 범위부터 확정한다.
        // 여기서 걸리면 예약·배정·회기 중 어떤 행도 아직 바뀌지 않은 상태다.
        ensureTypeInScope(scope, reservation);

        // CLOSED는 신규 예약만 막는 상태이므로 여기서 schedule.isOpen()은 검사하지 않는다.
        // 이미 걸린 REQUESTED 예약은 일정이 마감됐어도 승인 가능해야 close()의 의미가 유지된다.
        // 대신 일정이 이미 시작(또는 경과)됐으면 과거 시각에 활성 배정이 생기므로 이것만 막는다.
        // reservation.approve() 등 어떤 필드 변경보다 먼저 던지므로 실패 시 부분 반영이 없다.
        CounselingSchedule schedule = reservation.getCounselingSchedule(); // 소유권 검증으로 이미 non-null 확정
        if (!schedule.getStartsAt().isAfter(now)) {
            throw new BusinessException(
                    ErrorCode.SCHEDULE_NOT_AVAILABLE,
                    "상담 시작 시각이 지나 승인할 수 없습니다."
            );
        }
        reservation.approve(counselorId, now);

        // 최초 배정 대상은 이 일정의 담당 상담사이며, 위 소유권 검사로 요청자 본인과 동일함이 이미
        // 확인됐고 요청자는 requireScope로 활성 STAFF+ST200 상담사임도 확인됐다.
        // 즉 배정 대상의 활성·역할 검증을 별도로 다시 하지 않아도 이미 충족된 상태다.
        AssignmentAndSession created = createInitialAssignmentAndSession(reservation, schedule, counselorId, now);

        // 예약·배정·회기 저장까지 끝난 뒤 같은 트랜잭션에서 이벤트만 발행한다. 실제 알림 저장은
        // 이 트랜잭션이 커밋된 뒤 리스너가 처리하므로, 여기서 알림 실패가 승인 처리를 되돌리지 않는다.
        eventPublisher.publishEvent(CounselingReservationDecisionEvent.confirmed(
                reservation.getCounselingReservationId(),
                reservation.getStudent().getUserId(),
                schedule.getStartsAt(),
                schedule.getEndsAt(),
                schedule.getLocation()
        ));

        return CounselorReservationDecisionResponse.from(reservation, created.assignment(), created.session());
    }

    @Transactional
    public CounselorPendingReservationResponse reject(
            Integer reservationId,
            Integer counselorId,
            String decisionReason
    ) {
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        Instant now = Instant.now();
        CounselingReservation reservation = getOwnedReservationForUpdate(reservationId, counselorId);
        // 상태를 REJECTED로 바꾸기 전에 유형 범위를 먼저 확인해, 걸리면 아무 것도 바뀌지 않게 한다.
        ensureTypeInScope(scope, reservation);
        reservation.reject(decisionReason, counselorId, now);

        eventPublisher.publishEvent(CounselingReservationDecisionEvent.rejected(
                reservation.getCounselingReservationId(),
                reservation.getStudent().getUserId(),
                decisionReason
        ));

        return CounselorPendingReservationResponse.from(reservation);
    }

    /**
     * 대면·전화로 접수한 학생을 대신해 예약을 생성하고 그 자리에서 즉시 승인·배정·1회기까지 만든다.
     * 잠금 순서는 학생 → 동의 → 일정이다. 서로 다른 순서로 잠그는 경로가 있으면 두 트랜잭션이
     * 각자 상대가 쥔 잠금을 기다리는 교착상태가 생길 수 있어, 학생 직접 예약과 같은 순서를 지킨다.
     */
    @Transactional
    public CounselorReservationDecisionResponse createProxyReservation(
            CounselorProxyReservationRequest request,
            Integer counselorId
    ) {
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        Instant now = Instant.now();

        AppUser student = counselUserRepository.findByIdForUpdate(request.studentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!"STUDENT".equals(student.getUserType()) || !"ACTIVE".equals(student.getAccountStatus())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 학생 행을 잠근 직후 지도학생 관계를 요청 시점 DB 현재값으로 다시 확인한다. 학번 조회
        // 시점엔 지도학생이었어도 예약 제출 전에 지도교수가 바뀌었거나, 목록 조회를 우회해
        // studentId를 직접 제출한 비지도학생이면 여기서 걸러 U001로 응답한다(설계 5.2). 이 검사가
        // 아래 일정 소유권·유형 검사보다 먼저 실행돼야, "비지도학생"과 "일정 소유권 불일치(S002)"가
        // 서로 다른 응답 코드로 구분된다.
        if (scope == CounselManagementAccessPolicy.Scope.CAREER_ONLY
                && !counselUserRepository.isAdviseeOf(request.studentId(), counselorId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        CounselingType counselingType = counselingTypeRepository
                .findByCounselingTypeIdAndActiveTrue(request.counselingTypeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!counselManagementAccessPolicy.allows(
                scope, counselingType.getTypeCode(), counselingType.getApplicationRoute()
        )) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
        }

        // 동의 후보가 아예 없는 경우와, 후보를 잠근 뒤 재검증(requireOwnedValidConsent)에서 막히는
        // 경우(동의 경쟁으로 그 사이 철회됨 등)를 모두 U009 하나로 통합한다. 저장소 장애 같은 다른
        // RuntimeException까지 여기서 삼키면 운영 장애를 "동의 없음"으로 잘못 보고하게 되므로
        // BusinessException(FORBIDDEN) 외에는 그대로 전파한다.
        Integer consentCandidateId = consentVerifier.findCurrentValidConsent(
                        request.studentId(), ConsentModuleCode.COUNSELING, ConsentType.PERSONAL_INFO, now
                )
                .map(UserConsent::getUserConsentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUIRED_CONSENT_NOT_AGREED));
        UserConsent userConsent;
        try {
            userConsent = consentVerifier.requireOwnedValidConsent(
                    consentCandidateId,
                    request.studentId(),
                    ConsentModuleCode.COUNSELING,
                    ConsentType.PERSONAL_INFO,
                    now
            );
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.FORBIDDEN) {
                throw new BusinessException(ErrorCode.REQUIRED_CONSENT_NOT_AGREED);
            }
            throw e;
        }

        CounselingSchedule schedule = counselingScheduleService.requireOwnedReservableDirectSchedule(
                request.scheduleId(), counselingType, request.studentId(), counselorId, now
        );

        // 생성과 승인이 하나의 사건이므로 예약을 만든 직후 같은 now로 바로 승인한다.
        CounselingReservation reservation = CounselingReservation.create(
                counselingType, schedule, student, userConsent, request.requestContent()
        );
        reservation.approve(counselorId, now);
        counselingReservationRepository.save(reservation);

        AssignmentAndSession created = createInitialAssignmentAndSession(reservation, schedule, counselorId, now);

        eventPublisher.publishEvent(CounselingReservationDecisionEvent.confirmed(
                reservation.getCounselingReservationId(),
                student.getUserId(),
                schedule.getStartsAt(),
                schedule.getEndsAt(),
                schedule.getLocation()
        ));

        return CounselorReservationDecisionResponse.from(reservation, created.assignment(), created.session());
    }

    /**
     * 기존 승인과 대행 예약 생성이 공유하는 "승인된 예약의 최초 배정·1회기 생성" 절차다.
     * 1회기 저장이 실패하면(유니크 제약, 강제 예외 등) 이 메서드를 호출한 트랜잭션 전체가
     * 롤백되어 방금 만든 예약 승인과 배정도 함께 취소된다.
     */
    private AssignmentAndSession createInitialAssignmentAndSession(
            CounselingReservation reservation,
            CounselingSchedule schedule,
            Integer counselorId,
            Instant now
    ) {
        CounselingAssignment assignment = CounselingAssignment.create(
                reservation,
                schedule.getCounselor(),
                counselorId,
                null,
                now
        );
        counselingAssignmentRepository.save(assignment);

        CounselingSession firstSession = CounselingSession.createFirst(
                assignment, schedule.getStartsAt(), schedule.getEndsAt(), counselorId
        );
        counselingSessionRepository.save(firstSession);

        return new AssignmentAndSession(assignment, firstSession);
    }

    private record AssignmentAndSession(CounselingAssignment assignment, CounselingSession session) {
    }

    /**
     * 잠금 조회(studentId 조건 없이 예약 행만 잠금) 후, 이 예약이 참조하는 일정의 담당 상담사가
     * 요청자 본인인지 여기서 검증한다. 일정이 없거나(CENTER) 다른 상담사의 일정이면 모두
     * RESERVATION_NOT_FOUND로 통일해, 존재 여부나 담당자 정보를 노출하지 않는다.
     */
    private CounselingReservation getOwnedReservationForUpdate(Integer reservationId, Integer counselorId) {
        CounselingReservation reservation = counselingReservationRepository
                .findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        CounselingSchedule schedule = reservation.getCounselingSchedule();
        if (schedule == null || !schedule.getCounselor().getUserId().equals(counselorId)) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        return reservation;
    }

    /**
     * 잠근 예약이 현재 역할 범위가 허용하는 유형인지 확인한다. 다른 유형 예약임을 알려주면
     * "내 담당 예약인데 유형이 안 맞는다"는 사실 자체가 노출되므로, 소유권 실패와 동일하게
     * RESERVATION_NOT_FOUND로 응답을 통일한다.
     */
    private void ensureTypeInScope(CounselManagementAccessPolicy.Scope scope, CounselingReservation reservation) {
        boolean allowed = counselManagementAccessPolicy.allows(
                scope,
                reservation.getCounselingType().getTypeCode(),
                reservation.getCounselingType().getApplicationRoute()
        );
        if (!allowed) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }
    }
}
