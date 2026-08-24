package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingReservationCancelRequest;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingReservationDetailResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingReservationRequest;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingReservationResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselingReservationScheduleChangeRequest;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSchedule;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingType;
import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingConsentRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingReservationRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingScheduleRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingTypeRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 학생의 DIRECT(온라인 신청) 직접 예약을 생성하고 본인 예약만 조회한다.
 * CENTER(센터 접수)형 신청은 체크리스트 12번(상담센터 접수 처리) 구현 전까지 거절한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounselingReservationService {

    private static final String DIRECT_ROUTE = "DIRECT";
    private static final String CENTER_ROUTE = "CENTER";

    private final CounselUserRepository counselUserRepository;
    private final CounselingTypeRepository counselingTypeRepository;
    private final CounselingScheduleRepository counselingScheduleRepository;
    private final CounselingReservationRepository counselingReservationRepository;
    private final CounselingConsentRepository counselingConsentRepository;

    /**
     * 학생 행을 먼저 잠가 같은 학생의 서로 다른 일정 예약도 순서대로 검증한다.
     * 직접 예약은 이어서 일정 행을 잠가 정원과 마감 조건을 최종 확인한다.
     */
    @Transactional
    public CounselingReservationResponse create(
            CounselingReservationRequest request,
            Integer studentId
    ) {
        Instant now = Instant.now();
        AppUser student = getActiveStudentForUpdate(studentId);
        CounselingType counselingType = getActiveCounselingType(request.counselingTypeId());
        UserConsent userConsent = getValidConsent(request.consentId(), studentId, now);
        CounselingSchedule counselingSchedule = getScheduleForReservation(
                request.scheduleId(),
                counselingType,
                studentId,
                now,
                null
        );

        CounselingReservation reservation = CounselingReservation.create(
                counselingType,
                counselingSchedule,
                student,
                userConsent,
                request.requestContent()
        );
        return CounselingReservationResponse.from(counselingReservationRepository.save(reservation));
    }

    public PageResponse<CounselingReservationResponse> getReservations(
            Integer studentId,
            int page,
            int size
    ) {
        ensureActiveStudent(studentId);
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return PageResponse.from(counselingReservationRepository
                .findAllByStudentUserId(studentId, pageRequest)
                .map(CounselingReservationResponse::from));
    }

    /**
     * 예약 행을 잠근 뒤 엔티티가 허용 상태(REQUESTED, APPROVED)와 일정 마감 전인지 검증하고 취소 처리한다.
     * 행 잠금과 상태 가드 덕분에 같은 예약에 대한 동시 취소 요청은 두 번째부터 실패한다.
     * {@code @Transactional}이 붙어 있으므로 메서드 안에서 BusinessException이 던져지면 스프링이
     * 트랜잭션 전체를 롤백한다. reservation.cancel()이 바꾼 필드(reservationStatus 등)는
     * 트랜잭션이 정상 종료돼 커밋될 때 비로소 UPDATE 쿼리로 반영되므로, 예외가 나면 DB에는
     * 아무 흔적도 남지 않는다(부분 반영 없음).
     */
    @Transactional
    public CounselingReservationResponse cancel(
            Integer reservationId,
            Integer studentId,
            CounselingReservationCancelRequest request
    ) {
        Instant now = Instant.now();
        CounselingReservation reservation = getReservationForUpdate(reservationId, studentId);
        reservation.cancel(request.cancellationReason(), now);
        return CounselingReservationResponse.from(reservation);
    }

    /**
     * create와 같은 잠금 순서(학생 행 → 예약 행 → 새 일정 행)를 지켜 본인 시간중복 불변식을 같은 지점에서 직렬화한다.
     * 새 일정은 create와 동일한 재검증(유형·마감·정원·상담사·본인 시간중복)을 다시 거치되,
     * 본인 시간중복 검사에서는 아직 옛 일정을 참조 중인 이 예약 자기 자신을 제외한다.
     * 잠금 순서를 항상 "학생 → 예약 → 일정"으로 고정하는 이유는, 두 트랜잭션이 서로 반대 순서로
     * 행을 잠그면 각자 상대가 쥔 잠금을 기다리며 영원히 멈추는 교착상태(deadlock)가 생길 수
     * 있기 때문이다. cancel()과 마찬가지로 {@code @Transactional}이므로 중간에 예외가 나면 전체가 롤백된다.
     */
    @Transactional
    public CounselingReservationResponse changeSchedule(
            Integer reservationId,
            Integer studentId,
            CounselingReservationScheduleChangeRequest request
    ) {
        Instant now = Instant.now();
        getActiveStudentForUpdate(studentId);
        CounselingReservation reservation = getReservationForUpdate(reservationId, studentId);
        // 새 일정 행을 잠그기 전에 먼저 현재 예약이 변경 가능한 상태·기한인지 확인해 불필요한 잠금을 피한다.
        reservation.ensureChangeable(now);
        CounselingSchedule newSchedule = getScheduleForReservation(
                request.scheduleId(),
                reservation.getCounselingType(),
                studentId,
                now,
                reservationId
        );
        reservation.changeSchedule(newSchedule, request.changeReason(), now);
        return CounselingReservationResponse.from(reservation);
    }

    /**
     * cancel(), changeSchedule()이 공통으로 쓰는 조회다.
     * studentId 조건을 리포지토리 쿼리 자체에 포함시켜, 존재하지 않는 예약과 남의 예약을
     * 애플리케이션 코드에서 따로 분기하지 않고 리포지토리 한 곳에서 걸러낸다.
     */
    private CounselingReservation getReservationForUpdate(Integer reservationId, Integer studentId) {
        return counselingReservationRepository
                .findByCounselingReservationIdAndStudentUserIdForUpdate(reservationId, studentId)
                // 다른 학생의 예약인지와 없는 예약인지를 구분하지 않아 예약 존재를 노출하지 않는다.
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    public CounselingReservationDetailResponse getReservation(
            Integer reservationId,
            Integer studentId
    ) {
        ensureActiveStudent(studentId);
        CounselingReservation reservation = counselingReservationRepository
                .findByCounselingReservationIdAndStudentUserId(reservationId, studentId)
                // 다른 학생의 예약인지와 없는 예약인지를 구분하지 않아 예약 존재를 노출하지 않는다.
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        return CounselingReservationDetailResponse.from(reservation);
    }

    private AppUser getActiveStudentForUpdate(Integer studentId) {
        AppUser student = counselUserRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (!isActiveStudent(student)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return student;
    }

    private void ensureActiveStudent(Integer studentId) {
        if (!counselUserRepository.isActiveStudent(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean isActiveStudent(AppUser student) {
        return "STUDENT".equals(student.getUserType())
                && "ACTIVE".equals(student.getAccountStatus());
    }

    private CounselingType getActiveCounselingType(Integer counselingTypeId) {
        return counselingTypeRepository
                .findByCounselingTypeIdAndActiveTrue(counselingTypeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private UserConsent getValidConsent(Integer consentId, Integer studentId, Instant now) {
        if (consentId == null) {
            return null;
        }
        return counselingConsentRepository.findValidByIdAndStudentId(consentId, studentId, now)
                // 다른 학생의 동의나 철회 이력의 존재는 공개하지 않는다.
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    /**
     * 상담 유형의 신청 경로(DIRECT/CENTER)에 따라 일정을 구하는 방식을 분기한다.
     * excludeReservationId는 일정 변경(changeSchedule)일 때만 값이 들어오고, 새 일정의 본인
     * 시간중복 검사에서 "지금 바꾸려는 이 예약 자신"을 겹침 대상에서 빼는 데 쓰인다.
     * 신규 신청(create)은 뺄 예약이 없으므로 null을 넘긴다.
     */
    private CounselingSchedule getScheduleForReservation(
            Integer scheduleId,
            CounselingType counselingType,
            Integer studentId,
            Instant now,
            Integer excludeReservationId
    ) {
        if (DIRECT_ROUTE.equals(counselingType.getApplicationRoute())) {
            return getAvailableDirectSchedule(scheduleId, counselingType, studentId, now, excludeReservationId);
        }
        // CENTER(센터 접수)는 체크리스트 12번(상담센터 접수 처리) 구현 전까지 학생 신청 경로에서 제외한다.
        // 프론트 필터만으로는 유형 ID를 직접 보낸 요청을 막지 못하므로 서버가 최종 방어선으로 거절한다.
        // 상담사 일정 등록의 CENTER 거절(CounselingScheduleService)과 같은 400 C001(INVALID_INPUT)로 통일한다.
        // enum·시드는 유지하므로 12번 착수 시 REQUESTED 예약 생성 로직을 복원한다.
        if (CENTER_ROUTE.equals(counselingType.getApplicationRoute())) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "센터 접수(CENTER) 상담은 아직 신청할 수 없습니다."
            );
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }

    /**
     * DIRECT(온라인 직접 신청) 일정 하나가 "지금 이 학생이 예약해도 되는 일정"인지 검증한다.
     * 유형 일치·마감 전·정원 여유·담당 상담사 활성·본인 시간 중복 없음, 이 다섯 조건을 각각
     * boolean으로 먼저 구한 뒤 한 번에 검사한다. 어느 조건에서 걸렸는지 클라이언트에 따로
     * 알려주지 않고 SCHEDULE_NOT_AVAILABLE 하나로 묶어서 던지는 이유는, 예를 들어 "정원 마감"과
     * "이미 다른 사람이 예약함"을 구분해 알려주면 남의 예약 현황을 추측하는 데 악용될 수 있어서다.
     */
    private CounselingSchedule getAvailableDirectSchedule(
            Integer scheduleId,
            CounselingType counselingType,
            Integer studentId,
            Instant now,
            Integer excludeReservationId
    ) {
        if (scheduleId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        CounselingSchedule schedule = counselingScheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_AVAILABLE));
        boolean matchingType = schedule.getCounselingType().getCounselingTypeId()
                .equals(counselingType.getCounselingTypeId());
        boolean beforeDeadline = schedule.getBookingDeadline() == null
                || schedule.getBookingDeadline().isAfter(now);
        boolean capacityAvailable = counselingReservationRepository
                .countOccupiedReservations(scheduleId) < schedule.getCapacity();
        boolean activeCounselor = "ACTIVE".equals(schedule.getCounselor().getAccountStatus())
                && counselUserRepository.hasCounselorRole(schedule.getCounselor().getUserId());
        // 변경(재배정)에서는 아직 옛 일정을 참조 중인 이 예약 자기 자신을 겹침 비교에서 빼야 한다.
        // 그렇지 않으면 옛 일정과 항상 겹쳐 새 일정으로 절대 바꿀 수 없다.
        boolean overlapsActiveReservation = excludeReservationId == null
                ? counselingReservationRepository.existsOverlappingActiveReservation(
                        studentId,
                        schedule.getStartsAt(),
                        schedule.getEndsAt()
                )
                : counselingReservationRepository.existsOverlappingActiveReservationExcluding(
                        studentId,
                        excludeReservationId,
                        schedule.getStartsAt(),
                        schedule.getEndsAt()
                );
        if (!matchingType
                || !schedule.isOpen()
                || !schedule.getStartsAt().isAfter(now)
                || !beforeDeadline
                || !capacityAvailable
                || !activeCounselor
                || overlapsActiveReservation) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
        }
        return schedule;
    }
}
