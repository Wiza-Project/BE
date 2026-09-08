package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingTypeResponse;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상담 유형 목록 조회 유스케이스를 담당한다. 화면(학생 신청 / 상담사 일정 등록)마다 노출 조건이 다르다.
 *
 * <p>{@code readOnly = true}는 이 서비스가 데이터를 변경하지 않는 조회 전용 트랜잭션임을 명시한다.
 * 영속성 조회와 응답 변환을 트랜잭션 안에서 끝내므로 컨트롤러가 엔티티를 다루지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounselingTypeService {

    // 상담사가 일정을 열 수 있는 신청 경로. 예약 로직(CounselingReservationService)도 같은 문자열로 분기한다.
    private static final String APPLICATION_ROUTE_DIRECT = "DIRECT";

    private final CounselingTypeRepository counselingTypeRepository;
    private final CounselManagementAccessPolicy counselManagementAccessPolicy;

    /**
     * 학생 신청 화면용: 활성이면서 DIRECT(온라인 신청)인 유형만 코드 오름차순으로 조회해 외부 응답 전용 DTO로 변환한다.
     *
     * <p>CENTER(센터 접수) 온라인 접수는 현재 MVP 범위 밖이며 별도 정책이 확정되기 전까지 학생 신청 경로에서 제외한다.
     * enum·시드·데이터는 유지하고 노출만 차단하므로, CENTER 접수 정책이 확정되면 DIRECT 필터만 풀면 복원된다.
     * 지금은 상담사용 {@link #getSchedulableCounselingTypes(Integer)}와 노출 대상이 같지만, 권한과 용도가 달라 메서드는 분리해 둔다.</p>
     */
    public List<CounselingTypeResponse> getActiveCounselingTypes() {
        return counselingTypeRepository
                .findAllByActiveTrueAndApplicationRouteOrderByTypeCodeAsc(APPLICATION_ROUTE_DIRECT).stream()
                // 엔티티를 그대로 반환하지 않고 API 계약에 허용된 필드만 DTO에 담는다.
                .map(CounselingTypeResponse::from)
                .toList();
    }

    /**
     * 상담사 일정 등록 화면용: 일정을 붙일 수 있는 DIRECT 유형 중 호출자의 현재 역할 범위가
     * 허용하는 유형만 조회한다. ST300 단독(CAREER_ONLY) 상담사는 CS200(진로상담) 한 건만 받는다.
     * 상담 유형은 기준정보라 민감정보가 아니므로, 새 저장소 메서드 없이 기존 활성 DIRECT 목록을
     * 그대로 재사용하고 정책 허용 여부로만 걸러낸다(예약·회기·기록처럼 조회 조건 자체를
     * 제한해야 하는 목록과는 다르다).
     */
    public List<CounselingTypeResponse> getSchedulableCounselingTypes(Integer counselorId) {
        CounselManagementAccessPolicy.Scope scope = counselManagementAccessPolicy.requireScope(counselorId);
        return counselingTypeRepository
                .findAllByActiveTrueAndApplicationRouteOrderByTypeCodeAsc(APPLICATION_ROUTE_DIRECT).stream()
                .filter(type -> counselManagementAccessPolicy.allows(
                        scope, type.getTypeCode(), type.getApplicationRoute()
                ))
                .map(CounselingTypeResponse::from)
                .toList();
    }
}
