package com.gnagnoohc.scms.domain.career.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gnagnoohc.scms.domain.career.dto.resume.ResumeContentDTO;
import com.gnagnoohc.scms.domain.career.dto.resume.ResumeCreateRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.resume.ResumeResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.resume.ResumeSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.resume.ResumeUpdateRequestDTO;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학생 이력서의 버전 스냅샷을 관리한다. 자동 연동 데이터는 별도 조립 계층에서 더한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResumeService {

    private final CareerDocumentRepository careerDocumentRepository;
    private final AppUserRepository appUserRepository;
    private final CareerDocumentAccessHelper careerDocumentAccessHelper;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public PageResponse<ResumeSummaryResponseDTO> getMyResumes(Integer studentUserId, Pageable pageable) {
        Page<CareerDocument> page = careerDocumentRepository
                .findByStudent_UserIdAndDocumentType(studentUserId, CareerDocument.TYPE_RESUME, pageable);
        return PageResponse.from(page.map(this::toSummary));
    }

    public ResumeResponseDTO getMyResume(Integer studentUserId, Integer careerDocumentId) {
        return toResponse(careerDocumentAccessHelper.getOwnedResume(studentUserId, careerDocumentId));
    }

    public ResumeResponseDTO getMyLatestResume(Integer studentUserId) {
        CareerDocument document = careerDocumentRepository
                .findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(studentUserId, CareerDocument.TYPE_RESUME)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESUME_NOT_FOUND));
        return toResponse(document);
    }

    @Transactional
    public ResumeResponseDTO createResume(Integer studentUserId, ResumeCreateRequestDTO request) {
        AppUser student = appUserRepository.findById(studentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (careerDocumentRepository.findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(
                studentUserId, CareerDocument.TYPE_RESUME).isPresent()) {
            throw new BusinessException(ErrorCode.RESUME_ALREADY_EXISTS);
        }
        return saveNewVersion(student, 1, request.getDocumentTitle(), request.getContentData());
    }

    /** 임시 저장은 선택한 버전을 제자리에서 갱신한다. */
    @Transactional
    public ResumeResponseDTO updateResume(Integer studentUserId, Integer careerDocumentId, ResumeUpdateRequestDTO request) {
        CareerDocument document = careerDocumentAccessHelper.getOwnedResume(studentUserId, careerDocumentId);
        document.updateContent(request.getDocumentTitle(), toJson(request.getContentData()), false);
        return toResponse(document);
    }

    /** 확정 저장은 현재 내용을 기준으로 다음 버전 스냅샷을 만든다. */
    @Transactional
    public ResumeResponseDTO createNextVersion(Integer studentUserId, Integer careerDocumentId) {
        CareerDocument base = careerDocumentAccessHelper.getOwnedResume(studentUserId, careerDocumentId);
        int nextVersion = careerDocumentRepository
                .findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(studentUserId, CareerDocument.TYPE_RESUME)
                .map(document -> document.getVersionNo() + 1)
                .orElse(base.getVersionNo() + 1);
        try {
            return toResponse(careerDocumentRepository.save(base.createNextVersion(nextVersion)));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DOCUMENT_VERSION_CONFLICT);
        }
    }

    @Transactional
    public void deleteResume(Integer studentUserId, Integer careerDocumentId) {
        careerDocumentRepository.delete(careerDocumentAccessHelper.getOwnedResume(studentUserId, careerDocumentId));
    }

    private ResumeResponseDTO saveNewVersion(AppUser student, int versionNo, String title, ResumeContentDTO contentData) {
        try {
            return toResponse(careerDocumentRepository.save(
                    CareerDocument.createResume(student, versionNo, title, toJson(contentData))));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DOCUMENT_VERSION_CONFLICT);
        }
    }

    private JsonNode toJson(ResumeContentDTO contentData) {
        return contentData == null ? null : objectMapper.valueToTree(contentData);
    }

    private ResumeContentDTO toContent(JsonNode contentData) {
        return contentData == null || contentData.isNull() ? null
                : objectMapper.convertValue(contentData, ResumeContentDTO.class);
    }

    private ResumeResponseDTO toResponse(CareerDocument document) {
        return ResumeResponseDTO.builder()
                .careerDocumentId(document.getCareerDocumentId())
                .documentTitle(document.getDocumentTitle())
                .versionNo(document.getVersionNo())
                .contentData(toContent(document.getContentData()))
                .createdAt(DateTimeUtils.toKstOffsetDateTime(document.getCreatedAt()))
                .updatedAt(DateTimeUtils.toKstOffsetDateTime(document.getUpdatedAt()))
                .build();
    }

    private ResumeSummaryResponseDTO toSummary(CareerDocument document) {
        return ResumeSummaryResponseDTO.builder()
                .careerDocumentId(document.getCareerDocumentId())
                .documentTitle(document.getDocumentTitle())
                .versionNo(document.getVersionNo())
                .updatedAt(DateTimeUtils.toKstOffsetDateTime(document.getUpdatedAt()))
                .build();
    }
}
