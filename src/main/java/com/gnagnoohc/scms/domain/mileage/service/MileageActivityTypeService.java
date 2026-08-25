package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageActivityTypeResponseDTO;
import com.gnagnoohc.scms.domain.mileage.repository.MileageActivityTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MileageActivityTypeService {

    private final MileageActivityTypeRepository activityTypeRepository;

    // 정책 등록 화면의 활동 유형 드롭다운용. 비활성화된 유형은 노출하지 않는다.
    public List<MileageActivityTypeResponseDTO> listActive() {
        return activityTypeRepository.findAllByActiveTrueOrderByActivityNameAsc()
                .stream()
                .map(MileageActivityTypeResponseDTO::from)
                .toList();
    }
}
