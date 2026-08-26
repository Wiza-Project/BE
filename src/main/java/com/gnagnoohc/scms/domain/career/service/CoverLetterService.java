package com.gnagnoohc.scms.domain.career.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gnagnoohc.scms.domain.career.dto.coverletter.CoverLetterCreateRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.coverletter.CoverLetterQuestionDTO;
import com.gnagnoohc.scms.domain.career.dto.coverletter.CoverLetterResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.coverletter.CoverLetterSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.coverletter.CoverLetterUpdateRequestDTO;
import com.gnagnoohc.scms.domain.career.entity.CareerDocument;
import com.gnagnoohc.scms.domain.career.helper.CareerDocumentAccessHelper;
import com.gnagnoohc.scms.domain.career.repository.CareerDocumentRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 학생 자기소개서 핵심 비즈니스 로직 서비스
 *
 * <p><strong>[아키텍처 가이드라인 및 처리 원칙]</strong></p>
 * <ul>
 *   <li><b>버전 관리:</b> 최신 버전 row는 {@code updateContent()}로 직접 수정(Dirty Checking)하고,
 *       이력을 남기고 싶을 때는 {@code createNextVersion()}으로 새 row를 스냅샷 생성한다.</li>
 *   <li><b>JSONB 매핑:</b> 문항 목록({@code questions})은 {@code {"questions":[...]}} 구조의 JSONB로 저장하며,
 *       {@code characterCount}는 클라이언트 값을 신뢰하지 않고 서버가 답변 길이로 재계산한다.</li>
 *   <li><b>소유자 검증:</b> 문서 조회/수정/삭제는 {@link CareerDocumentAccessHelper}를 통해 본인 소유 여부를 확인한다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoverLetterService {

    private final CareerDocumentRepository careerDocumentRepository;
    private final AppUserRepository appUserRepository;
    private final CareerDocumentAccessHelper careerDocumentAccessHelper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * [학생] 본인의 자기소개서 목록(버전 이력)을 최신 버전순으로 페이징 조회
     */
    public PageResponse<CoverLetterSummaryResponseDTO> getMyCoverLetters(Integer studentUserId, Pageable pageable) {
        Page<CareerDocument> page = careerDocumentRepository
                .findByStudent_UserIdAndDocumentType(studentUserId, CareerDocument.TYPE_COVER_LETTER, pageable);
        return PageResponse.from(page.map(this::mapToSummaryDTO));
    }

    /**
     * [학생] 본인의 최신 버전 자기소개서를 조회
     */
    public CoverLetterResponseDTO getMyLatestCoverLetter(Integer studentUserId) {
        CareerDocument document = careerDocumentRepository
                .findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(studentUserId, CareerDocument.TYPE_COVER_LETTER)
                .orElseThrow(() -> new BusinessException(ErrorCode.COVER_LETTER_NOT_FOUND));
        return mapToResponseDTO(document);
    }

    /**
     * [학생] 본인 소유의 특정 버전 자기소개서를 조회
     */
    public CoverLetterResponseDTO getMyCoverLetter(Integer studentUserId, Integer careerDocumentId) {
        CareerDocument document = careerDocumentAccessHelper.getOwnedCoverLetter(studentUserId, careerDocumentId);
        return mapToResponseDTO(document);
    }

    /**
     * [학생] 자기소개서 최초 작성 (버전 1). 이미 작성된 이력이 있으면 예외를 던진다.
     */
    @Transactional
    public CoverLetterResponseDTO createCoverLetter(Integer studentUserId, CoverLetterCreateRequestDTO requestDTO) {
        AppUser student = appUserRepository.findById(studentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        boolean alreadyExists = careerDocumentRepository
                .findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(studentUserId, CareerDocument.TYPE_COVER_LETTER)
                .isPresent();
        if (alreadyExists) {
            throw new BusinessException(ErrorCode.COVER_LETTER_ALREADY_EXISTS);
        }

        JsonNode contentData = toContentData(requestDTO.getQuestions());
        CareerDocument document = CareerDocument.createCoverLetter(
                student, 1, requestDTO.getDocumentTitle(), contentData, requestDTO.isAiAssistanceUsed());

        CareerDocument saved = saveOrThrowConflict(document);
        log.info("[CoverLetterService] 자기소개서 최초 작성 완료. studentUserId: {}, careerDocumentId: {}", studentUserId, saved.getCareerDocumentId());
        return mapToResponseDTO(saved);
    }

    /**
     * [학생] 자기소개서 특정 버전의 내용을 직접 수정 (Dirty Checking, 새 버전을 만들지 않음)
     */
    @Transactional
    public CoverLetterResponseDTO updateCoverLetter(Integer studentUserId, Integer careerDocumentId, CoverLetterUpdateRequestDTO requestDTO) {
        CareerDocument document = careerDocumentAccessHelper.getOwnedCoverLetter(studentUserId, careerDocumentId);

        JsonNode contentData = toContentData(requestDTO.getQuestions());
        document.updateContent(requestDTO.getDocumentTitle(), contentData, requestDTO.isAiAssistanceUsed());

        log.info("[CoverLetterService] 자기소개서 수정 완료. careerDocumentId: {}", careerDocumentId);
        return mapToResponseDTO(document);
    }

    /**
     * [학생] 지정한 버전의 현재 내용을 스냅샷하여 새 버전을 생성한다.
     * 과거 버전을 기준으로 호출하면 그 내용을 최신 버전으로 복원하는 효과도 낸다.
     */
    @Transactional
    public CoverLetterResponseDTO createNextVersion(Integer studentUserId, Integer careerDocumentId) {
        CareerDocument baseDocument = careerDocumentAccessHelper.getOwnedCoverLetter(studentUserId, careerDocumentId);

        Integer latestVersionNo = careerDocumentRepository
                .findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(studentUserId, CareerDocument.TYPE_COVER_LETTER)
                .map(CareerDocument::getVersionNo)
                .orElse(baseDocument.getVersionNo());

        CareerDocument nextVersion = baseDocument.createNextVersion(latestVersionNo + 1);
        CareerDocument saved = saveOrThrowConflict(nextVersion);

        log.info("[CoverLetterService] 자기소개서 새 버전 생성 완료. studentUserId: {}, newVersionNo: {}", studentUserId, saved.getVersionNo());
        return mapToResponseDTO(saved);
    }

    /**
     * [학생] 본인 소유의 특정 버전 자기소개서를 삭제
     */
    @Transactional
    public void deleteCoverLetter(Integer studentUserId, Integer careerDocumentId) {
        CareerDocument document = careerDocumentAccessHelper.getOwnedCoverLetter(studentUserId, careerDocumentId);
        careerDocumentRepository.delete(document);
        log.info("[CoverLetterService] 자기소개서 삭제 완료. careerDocumentId: {}", careerDocumentId);
    }

    private CareerDocument saveOrThrowConflict(CareerDocument document) {
        try {
            return careerDocumentRepository.save(document);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DOCUMENT_VERSION_CONFLICT);
        }
    }

    private JsonNode toContentData(List<CoverLetterQuestionDTO> questions) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode questionsNode = root.putArray("questions");
        for (CoverLetterQuestionDTO question : questions) {
            ObjectNode node = questionsNode.addObject();
            node.put("questionId", question.getQuestionId());
            node.put("question", question.getQuestion());
            node.put("answer", question.getAnswer());
            node.put("characterCount", question.getAnswer() != null ? question.getAnswer().length() : 0);
        }
        return root;
    }

    private List<CoverLetterQuestionDTO> fromContentData(JsonNode contentData) {
        if (contentData == null || !contentData.has("questions")) {
            return List.of();
        }
        List<CoverLetterQuestionDTO> questions = new ArrayList<>();
        for (JsonNode node : contentData.get("questions")) {
            questions.add(CoverLetterQuestionDTO.builder()
                    .questionId(node.path("questionId").asText(null))
                    .question(node.path("question").asText(null))
                    .answer(node.path("answer").isMissingNode() || node.path("answer").isNull() ? null : node.path("answer").asText())
                    .characterCount(node.path("characterCount").asInt(0))
                    .build());
        }
        return questions;
    }

    private CoverLetterResponseDTO mapToResponseDTO(CareerDocument document) {
        return CoverLetterResponseDTO.builder()
                .careerDocumentId(document.getCareerDocumentId())
                .studentUserId(document.getStudent().getUserId())
                .documentTitle(document.getDocumentTitle())
                .versionNo(document.getVersionNo())
                .questions(fromContentData(document.getContentData()))
                .aiAssistanceUsed(document.isAiAssistanceUsed())
                .createdAt(DateTimeUtils.toKstOffsetDateTime(document.getCreatedAt()))
                .updatedAt(DateTimeUtils.toKstOffsetDateTime(document.getUpdatedAt()))
                .build();
    }

    private CoverLetterSummaryResponseDTO mapToSummaryDTO(CareerDocument document) {
        return CoverLetterSummaryResponseDTO.builder()
                .careerDocumentId(document.getCareerDocumentId())
                .documentTitle(document.getDocumentTitle())
                .versionNo(document.getVersionNo())
                .aiAssistanceUsed(document.isAiAssistanceUsed())
                .updatedAt(DateTimeUtils.toKstOffsetDateTime(document.getUpdatedAt()))
                .build();
    }
}
