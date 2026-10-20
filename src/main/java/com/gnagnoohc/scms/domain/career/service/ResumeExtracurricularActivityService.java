package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.extracurricular.ResumeExtracurricularActivityResponse;
import com.gnagnoohc.scms.domain.career.repository.ResumeExtracurricularActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 이력서 화면의 비교과 수료 이력 조회.
 * 실제 값 적재는 {@link com.gnagnoohc.scms.domain.career.listener.ResumeExtracurricularActivityEventListener}
 * (실시간) 및 {@link com.gnagnoohc.scms.domain.career.support.ResumeExtracurricularActivityBackfillRunner}
 * (백필)가 담당하고, 이 서비스는 읽기 모델을 조회하는 역할만 한다.
 */
@Service
@RequiredArgsConstructor
public class ResumeExtracurricularActivityService {

    private final ResumeExtracurricularActivityRepository resumeExtracurricularActivityRepository;

    @Transactional(readOnly = true)
    public List<ResumeExtracurricularActivityResponse> getMyActivities(Integer studentId) {
        return resumeExtracurricularActivityRepository.findAllByStudentIdOrderByOperationEndedAtDesc(studentId).stream()
                .map(ResumeExtracurricularActivityResponse::from)
                .toList();
    }
}
