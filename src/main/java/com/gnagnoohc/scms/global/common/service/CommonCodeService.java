package com.gnagnoohc.scms.global.common.service;

import com.gnagnoohc.scms.global.common.dto.CommonCodeResponseDTO;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
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

    /**
     * 코드 하나를 화면 표시용 한글명으로 매핑 (예: getCodeName("PROGRAM_TYPE", "PT100") → "학습")
     *
     * 매핑이 없으면 예외를 던지지 않고 원본 code를 그대로 돌려준다 — 화면 표시 용도라
     * 시딩 누락 하나로 API 전체가 500 나는 것보다는 코드 원문이라도 보이는 게 낫다는 판단.
     */
    @Transactional(readOnly = true)
    public String getCodeName(String codeGroup, String code) {
        if (code == null) {
            return null;
        }
        return commonCodeRepository.findByCodeGroupAndCode(codeGroup, code)
                .map(CommonCode::getCodeName)
                .orElseGet(() -> {
                    log.warn("공통코드 매핑 없음 — codeGroup={}, code={}", codeGroup, code);
                    return code;
                });
    }

    /**
     * 목록 응답에서 여러 건을 한글명으로 바꿀 때 건마다 쿼리 나가는 걸(N+1) 막기 위한 배치 버전.
     * 그룹 전체를 한 번 조회해 code → codeName 맵으로 돌려주고, 호출부에서 get()만 반복한다.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getCodeNameMap(String codeGroup) {
        return commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc(codeGroup).stream()
                .collect(Collectors.toMap(CommonCode::getCode, CommonCode::getCodeName));
    }
}
