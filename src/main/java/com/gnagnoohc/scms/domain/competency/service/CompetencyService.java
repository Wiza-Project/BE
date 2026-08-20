package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.CompetencyDisplayOrderRequest;
import com.gnagnoohc.scms.domain.competency.dto.CompetencyRegisterRequest;
import com.gnagnoohc.scms.domain.competency.dto.CompetencyResponse;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

    public List<CompetencyResponse> changeDisplayOrder(Integer competencyId, CompetencyDisplayOrderRequest request) {
        Competency competency = competencyRepository.findById(competencyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPETENCY_NOT_FOUND));

        Integer previousOrder = competency.getDisplayOrder();
        Integer newOrder = request.displayOrder();

        // 요청한 순서를 이미 쓰고 있는 다른 역량이 있는지 확인 (있으면 스왑 대상)
        Optional<Competency> conflicting = competencyRepository
                .findByParentCompetencyIsNullAndDisplayOrderAndCompetencyIdNot(newOrder, competencyId);

        competency.changeDisplayOrder(newOrder);

        if (conflicting.isEmpty()) {
            return List.of(CompetencyResponse.from(competency));
        }

        // 거절하지 않고 스왑하는 이유: 역량이 6개(1~6 전부 사용중)로 다 차면
        // 어떤 역량이든 자기 값 외에는 항상 "이미 사용중"이라 거절 방식으로는 영구히 재배치가 불가능해짐
        Competency swapped = conflicting.get();
        swapped.changeDisplayOrder(previousOrder);
        return List.of(CompetencyResponse.from(competency), CompetencyResponse.from(swapped));
    }
}
