package com.gnagnoohc.scms.domain.counsel.service;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingTypeResponse;
import com.gnagnoohc.scms.domain.counsel.repository.CounselingTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 학생에게 노출할 상담 유형 목록을 조회하는 유스케이스를 담당한다.
 *
 * <p>{@code readOnly = true}는 이 서비스가 데이터를 변경하지 않는 조회 전용 트랜잭션임을 명시한다.
 * 영속성 조회와 응답 변환을 트랜잭션 안에서 끝내므로 컨트롤러가 엔티티를 다루지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CounselingTypeService {

    private final CounselingTypeRepository counselingTypeRepository;

    /**
     * 활성 상담 유형을 코드 오름차순으로 조회해 외부 응답 전용 DTO로 변환한다.
     */
    public List<CounselingTypeResponse> getActiveCounselingTypes() {
        return counselingTypeRepository.findAllByActiveTrueOrderByTypeCodeAsc().stream()
                // 엔티티를 그대로 반환하지 않고 API 계약에 허용된 필드만 DTO에 담는다.
                .map(CounselingTypeResponse::from)
                .toList();
    }
}
