package com.gnagnoohc.scms.domain.career.helper;

import com.gnagnoohc.scms.domain.career.entity.CareerDocument;
import com.gnagnoohc.scms.domain.career.repository.CareerDocumentRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 취창업 문서(자기소개서/포트폴리오)의 소유자 검증 헬퍼.
 *
 * <p>본인 소유가 아니거나 존재하지 않는 문서는 동일한 NOT_FOUND 예외로 응답하여
 * 다른 학생의 문서 존재 여부가 노출되지 않도록 한다.</p>
 */
@Component
@RequiredArgsConstructor
public class CareerDocumentAccessHelper {

    private final CareerDocumentRepository careerDocumentRepository;

    public CareerDocument getOwnedCoverLetter(Integer studentUserId, Integer careerDocumentId) {
        return careerDocumentRepository.findOwnedDocument(careerDocumentId, studentUserId, CareerDocument.TYPE_COVER_LETTER)
                .orElseThrow(() -> new BusinessException(ErrorCode.COVER_LETTER_NOT_FOUND));
    }

    public CareerDocument getOwnedPortfolio(Integer studentUserId, Integer careerDocumentId) {
        return careerDocumentRepository.findOwnedDocument(careerDocumentId, studentUserId, CareerDocument.TYPE_PORTFOLIO)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));
    }
}
