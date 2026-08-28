package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageActivityTypeRegisterRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageActivityTypeResponseDTO;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.repository.MileageActivityTypeRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MileageActivityTypeService {

    private final MileageActivityTypeRepository activityTypeRepository;
    private final CompetencyRepository competencyRepository;

    @Transactional
    public MileageActivityTypeResponseDTO register(MileageActivityTypeRegisterRequestDTO request, Integer staffId) {
        Competency competency = competencyRepository.findById(request.competencyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPETENCY_NOT_FOUND));

        String activityCode = request.activityCode().trim();
        if (activityTypeRepository.existsByActivityCode(activityCode)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 사용 중인 활동 코드입니다.");
        }

        MileageActivityType activityType = MileageActivityType.create(
                competency,
                activityCode,
                request.categoryCode().trim(),
                request.activityName().trim(),
                request.earningRoute().trim(),
                staffId
        );

        try {
            return MileageActivityTypeResponseDTO.from(activityTypeRepository.saveAndFlush(activityType));
        } catch (DataIntegrityViolationException exception) {
            if (isActivityCodeConstraintViolation(exception)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 사용 중인 활동 코드입니다.");
            }
            throw exception;
        }
    }

    private boolean isActivityCodeConstraintViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String message = cause == null ? exception.getMessage() : cause.getMessage();
        if (message == null) {
            return false;
        }

        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        return normalizedMessage.contains("activity_code")
                && (normalizedMessage.contains("duplicate") || normalizedMessage.contains("unique"));
    }

    // 정책 등록 화면의 활동 유형 드롭다운용. 비활성화된 유형은 노출하지 않는다.
    public List<MileageActivityTypeResponseDTO> listActive() {
        return activityTypeRepository.findAllByActiveTrueOrderByActivityNameAsc()
                .stream()
                .map(MileageActivityTypeResponseDTO::from)
                .toList();
    }
}
