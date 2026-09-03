package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentHistoryResponse;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentHistoryQueryRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssessmentHistoryService {

    private final AssessmentHistoryQueryRepository assessmentHistoryQueryRepository;

    public PageResponse<AssessmentHistoryResponse> getHistory(Integer studentId, String keyword, Pageable pageable) {
        return PageResponse.from(assessmentHistoryQueryRepository.findHistory(studentId, keyword, pageable));
    }
}
