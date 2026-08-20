package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.CompetencyRegisterRequest;
import com.gnagnoohc.scms.domain.competency.dto.CompetencyResponse;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CompetencyService {

    private static final int MAX_TOP_LEVEL_COMPETENCY = 6;

    private final CompetencyRepository competencyRepository;

    public CompetencyResponse registerCompetency(CompetencyRegisterRequest request, Integer staffId) {
        long existingCount = competencyRepository.countByParentCompetencyIsNull();
        if (existingCount >= MAX_TOP_LEVEL_COMPETENCY) {
            throw new BusinessException(ErrorCode.COMPETENCY_LIMIT_EXCEEDED);
        }

        int sequence = (int) existingCount + 1;
        Competency competency = Competency.createTop(
                "C" + sequence,
                request.competencyName(),
                request.englishName(),
                request.description(),
                sequence,
                staffId
        );

        return CompetencyResponse.from(competencyRepository.save(competency));
    }
}
