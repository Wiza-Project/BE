package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.request.CounselingReservationCancelRequest;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingReservationDetailResponse;
import com.gnagnoohc.scms.domain.counsel.dto.request.CounselingReservationRequest;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingReservationResponse;
import com.gnagnoohc.scms.domain.counsel.dto.request.CounselingReservationScheduleChangeRequest;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingAssignment;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSchedule;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingType;
import com.gnagnoohc.scms.domain.counsel.repository.CounselUserRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingAssignmentRepository;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingReservationRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 학생의 DIRECT(온라인 신청) 직접 예약을 생성하고 본인 예약만 조회한다.
 * CENTER(센터 접수) 온라인 접수는 현재 MVP 범위 밖이며 별도 정책이 확정되기 전까지 거절한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounselingReservationService {

    private static final String DIRECT_ROUTE = "DIRECT";
    private static final String CENTER_ROUTE = "CENTER";

    private final CounselUserRepository counselUserRepository;
    private final CounselingTypeRepository counselingTypeRepository;
    private final CounselingReservationRepository counselingReservationRepository;
    private final CounselingAssignmentRepository counselingAssignmentRepository;
    private final ConsentVerifier consentVerifier;
    private final CounselingScheduleService counselingScheduleService;

    /**
     * 학생 행을 먼저 잠가 같은 학생의 서로 다른 일정 예약도 순서대로 검증한다.
     * 직접 예약은 이어서 일정 행을 잠가 정원과 마감 조건을 최종 확인한다.
     * 동의 검증은 상담 도메인 자체 리포지토리가 아니라 user 도메인의 공통 ConsentVerifier로 처리한다.
     * 동의 소유권·모듈(COUNSELING)·유형(PERSONAL_INFO)·철회 여부·정책 유효기간을 한 곳에서 검사하게 해,
     * 도메인마다 같은 검증을 다시 구현하다 조건을 하나 빠뜨리는 실수를 막기 위해서다.
     * consentId는 DTO에서 @NotNull로 강제되므로 여기서 null 여부를 따로 분기하지 않는다.
     */
    @Transactional
    public CounselingReservationResponse create(
            CounselingReservationRequest request,
            Integer studentId
    ) {
        Instant now = Instant.now();
        AppUser student = getActiveStudentForUpdate(studentId);
        CounselingType counselingType = getActiveCounselingType(request.counselingTypeId());
        // 실패 시 다른 학생의 동의인지, 철회됐는지, 만료됐는지를 구분해 알려주지 않고
        // 전부 동일한 FORBIDDEN(A004)으로 응답한다. 이유를 세분화해 알려주면 존재하지 않는 동의 ID로
        // 시도해보며 다른 학생의 동의 이력 존재 여부를 추측하는 데 악용될 수 있기 때문이다.
        UserConsent userConsent = consentVerifier.requireOwnedValidConsent(
                request.consentId(),
                studentId,
                ConsentModuleCode.COUNSELING,
                ConsentType.PERSONAL_INFO,
                now
        );
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
     * 취소 대상이 승인(APPROVED)까지 갔던 예약이면, 예약이 만든 배정도 그대로 활성 상태로 남아
     * "취소된 예약인데 활성 배정이 있다"는 모순이 생긴다. 이를 막기 위해 같은 트랜잭션 안에서
     * 그 예약의 활성 배정을 함께 종료(ended_at 세팅)한다. reservation.cancel()이 상태를 CANCELED로
     * 바꾸고 나면 "취소 직전에 APPROVED였는지" 더 이상 구분할 수 없으므로, wasApproved는 반드시
     * cancel() 호출 전에 읽어 둔다.
     */
    @Transactional
    public CounselingReservationResponse cancel(
            Integer reservationId,
            Integer studentId,
            CounselingReservationCancelRequest request
    ) {
        Instant now = Instant.now();
        CounselingReservation reservation = getReservationForUpdate(reservationId, studentId);
        boolean wasApproved = reservation.isApproved();
        reservation.cancel(request.cancellationReason(), now);
        if (wasApproved) {
            CounselingAssignment activeAssignment = counselingAssignmentRepository
                    .findByCounselingReservationCounselingReservationIdAndEndedAtIsNull(reservationId)
                    // 승인된 예약에 활성 배정이 없는 것은 데이터 모순이므로 조용히 넘어가지 않고
                    // 예외로 트랜잭션 전체를 롤백시켜 잘못된 취소가 커밋되지 않게 한다.
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
            activeAssignment.end(now);
        }
        return CounselingReservationResponse.from(reservation);
    }

    /**
     * create와 같은 잠금 순서(학생 행 → 예약 행 → 새 일정 행)를 지켜 본인 시간중복 불변식을 같은 지점에서 직렬화한다.
     * 새 일정은 create와 동일한 재검증(유형·마감·정원·상담사·본인 시간중복)을 다시 거치되,
     * 본인 시간중복 검사에서는 아직 옛 일정을 참조 중인 이 예약 자기 자신을 제외한다.
     * 잠금 순서를 항상 "학생 → 예약 → 일정"으로 고정하는 이유는, 두 트랜잭션이 서로 반대 순서로
     * 행을 잠그면 각자 상대가 쥔 잠금을 기다리며 영원히 멈추는 교착상태(deadlock)가 생길 수
     * 있기 때문이다. cancel()과 마찬가지로 {@code @Transactional}이므로 중간에 예외가 나면 전체가 롤백된다.
     *
     * 예약 행을 잠근 직후, 아직 새 일정 행을 잠그기 전에 request.expectedScheduleId()와 DB의 실제
     * 현재 일정을 비교한다(stale 검사). 같은 예약을 서로 다른 새 일정으로 동시에 수정하는 두 요청은
     * 둘 다 같은 expectedScheduleId(원래 일정)를 보내는데, 먼저 커밋한 쪽이 일정을 바꾸고 나면
     * 뒤에 도착한 요청은 예약 잠금을 얻은 뒤 비교에서 걸려 S013으로 실패한다. 이 비교와 아래 동일
     * 일정 검사를 모두 새 일정 잠금 이전에 끝내는 이유는, 불필요한 일정 잠금을 피하면서 두 검사가
     * 예약 행 잠금 하나로 직렬화되게 하기 위해서다.
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
        Integer currentScheduleId = reservation.getCounselingSchedule().getCounselingScheduleId();
        // stale 검사: 화면이 기억하던 일정과 DB의 실제 현재 일정이 다르면, 그 사이에 다른 요청이
        // 먼저 이 예약의 일정을 바꾼 것이다. 이 시점에는 아직 어떤 필드도 바꾸지 않았으므로 그대로 던지면 된다.
        if (!currentScheduleId.equals(request.expectedScheduleId())) {
            throw new BusinessException(ErrorCode.RESERVATION_SCHEDULE_CONFLICT);
        }
        // 동일 일정 무변경 거절: 예상 일정과 새로 옮겨갈 일정이 같으면 실제로는 아무것도 바뀌지 않는
        // 요청이므로 changeReason도 저장하지 않고 400으로 거절한다.
        if (currentScheduleId.equals(request.scheduleId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "현재 예약 일정과 다른 일정을 선택해 주세요.");
        }
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
            return counselingScheduleService.requireReservableDirectSchedule(
                    scheduleId, counselingType, studentId, now, excludeReservationId
            );
        }
        // CENTER(센터 접수) 온라인 접수는 현재 MVP 범위 밖이며 별도 정책이 확정되기 전까지 거절한다.
        // 프론트 필터만으로는 유형 ID를 직접 보낸 요청을 막지 못하므로 서버가 최종 방어선으로 거절한다.
        // 상담사 일정 등록의 CENTER 거절(CounselingScheduleService)과 같은 400 C001(INVALID_INPUT)로 통일한다.
        // enum·시드는 유지하므로 CENTER 접수 정책이 확정되면 REQUESTED 예약 생성 로직을 복원한다.
        if (CENTER_ROUTE.equals(counselingType.getApplicationRoute())) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "센터 접수(CENTER) 상담은 아직 신청할 수 없습니다."
            );
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }

}
