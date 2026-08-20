package com.gnagnoohc.scms.global.common.service;

import com.gnagnoohc.scms.global.common.dto.CommonCodeResponseDTO;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommonCodeService {

    private final CommonCodeRepository commonCodeRepository;

    @Transactional(readOnly = true)
    public List<CommonCodeResponseDTO> getCodes(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc(groupCode.trim())
                .stream()
                .map(CommonCodeResponseDTO::from)
                .toList();
    }
}
