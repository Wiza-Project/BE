package com.gnagnoohc.scms.domain.career.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnagnoohc.scms.domain.career.dto.portfolio.PortfolioAttachmentResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.portfolio.PortfolioCreateRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.portfolio.PortfolioResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.portfolio.PortfolioSummaryResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.portfolio.PortfolioUpdateRequestDTO;
import com.gnagnoohc.scms.domain.career.entity.CareerDocument;
import com.gnagnoohc.scms.domain.career.helper.CareerDocumentAccessHelper;
import com.gnagnoohc.scms.domain.career.repository.CareerDocumentRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.common.entity.FileGroup;
import com.gnagnoohc.scms.global.common.entity.StoredFile;
import com.gnagnoohc.scms.global.common.service.FileGroupService;
import com.gnagnoohc.scms.global.common.service.FileStorageService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 학생 포트폴리오 핵심 비즈니스 로직 서비스
 *
 * <p><strong>[아키텍처 가이드라인 및 처리 원칙]</strong></p>
 * <ul>
 *   <li><b>항목 관리:</b> 포트폴리오는 버전 이력이 아니라 서로 다른 항목의 나열이므로,
 *       {@code version_no}는 학생 기준 다음 항목 순번으로만 사용하고 항목 수정은 항상 제자리 수정(Dirty Checking)한다.</li>
 *   <li><b>첨부파일:</b> {@code FileGroupService}/{@code FileStorageService}를 재사용하여 검증·저장을 위임하고,
 *       이 서비스는 소유자 검증과 {@code CareerDocument.fileGroup} 연결만 책임진다.</li>
 *   <li><b>소유자 검증:</b> 조회/수정/삭제/첨부는 {@link CareerDocumentAccessHelper}로 본인 소유 여부를 확인한다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioService {

    private final CareerDocumentRepository careerDocumentRepository;
    private final AppUserRepository appUserRepository;
    private final CareerDocumentAccessHelper careerDocumentAccessHelper;
    private final FileGroupService fileGroupService;
    private final FileStorageService fileStorageService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * [학생] 본인의 포트폴리오 항목 목록을 최신 수정순으로 페이징 조회
     */
    public PageResponse<PortfolioSummaryResponseDTO> getMyPortfolios(Integer studentUserId, Pageable pageable) {
        Page<CareerDocumentRepository.PortfolioSummaryProjection> page = careerDocumentRepository
                .findPortfolioSummaries(studentUserId, CareerDocument.TYPE_PORTFOLIO, pageable);
        return PageResponse.from(page.map(this::mapToSummaryDTO));
    }

    /**
     * [학생] 본인 소유의 포트폴리오 항목 단건을 조회
     */
    public PortfolioResponseDTO getMyPortfolio(Integer studentUserId, Integer careerDocumentId) {
        CareerDocument document = careerDocumentAccessHelper.getOwnedPortfolio(studentUserId, careerDocumentId);
        return mapToResponseDTO(document);
    }

    /**
     * [학생] 포트폴리오 항목 신규 생성. 순번은 학생 기준 다음 번호로 자동 채번한다.
     */
    @Transactional
    public PortfolioResponseDTO createPortfolio(Integer studentUserId, PortfolioCreateRequestDTO requestDTO) {
        AppUser student = appUserRepository.findById(studentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        int nextVersionNo = careerDocumentRepository
                .findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(studentUserId, CareerDocument.TYPE_PORTFOLIO)
                .map(document -> document.getVersionNo() + 1)
                .orElse(1);

        JsonNode contentData = mapToJsonNode(requestDTO.getContentData());
        CareerDocument document = CareerDocument.createPortfolio(
                student, nextVersionNo, requestDTO.getDocumentTitle(), contentData, requestDTO.isAiAssistanceUsed());

        CareerDocument saved;
        try {
            // 버전 번호는 조회 후 계산되므로 동시 생성 시 유니크 충돌 가능성이 있다.
            // saveAndFlush로 예외를 이 범위에서 받아 공통 비즈니스 오류로 변환한다.
            saved = careerDocumentRepository.saveAndFlush(document);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DOCUMENT_VERSION_CONFLICT);
        }

        log.info("[PortfolioService] 포트폴리오 항목 생성 완료. studentUserId: {}, careerDocumentId: {}", studentUserId, saved.getCareerDocumentId());
        return mapToResponseDTO(saved);
    }

    /**
     * [학생] 본인 소유의 포트폴리오 항목 내용을 수정
     */
    @Transactional
    public PortfolioResponseDTO updatePortfolio(Integer studentUserId, Integer careerDocumentId, PortfolioUpdateRequestDTO requestDTO) {
        CareerDocument document = careerDocumentAccessHelper.getOwnedPortfolio(studentUserId, careerDocumentId);

        JsonNode contentData = mapToJsonNode(requestDTO.getContentData());
        document.updateContent(requestDTO.getDocumentTitle(), contentData, requestDTO.isAiAssistanceUsed());

        log.info("[PortfolioService] 포트폴리오 항목 수정 완료. careerDocumentId: {}", careerDocumentId);
        return mapToResponseDTO(document);
    }

    /**
     * [학생] 포트폴리오 항목의 공개 여부를 변경
     */
    @Transactional
    public PortfolioResponseDTO changeVisibility(Integer studentUserId, Integer careerDocumentId, boolean isPublic) {
        CareerDocument document = careerDocumentAccessHelper.getOwnedPortfolio(studentUserId, careerDocumentId);
        document.changeVisibility(isPublic);

        log.info("[PortfolioService] 포트폴리오 공개 여부 변경 완료. careerDocumentId: {}, isPublic: {}", careerDocumentId, isPublic);
        return mapToResponseDTO(document);
    }

    /**
     * [학생] 본인 소유의 포트폴리오 항목을 삭제 (첨부파일도 함께 제거)
     */
    @Transactional
    public void deletePortfolio(Integer studentUserId, Integer careerDocumentId) {
        CareerDocument document = careerDocumentAccessHelper.getOwnedPortfolio(studentUserId, careerDocumentId);

        if (document.getFileGroup() != null) {
            fileGroupService.getFiles(document.getFileGroup()).forEach(fileStorageService::delete);
        }
        careerDocumentRepository.delete(document);

        log.info("[PortfolioService] 포트폴리오 항목 삭제 완료. careerDocumentId: {}", careerDocumentId);
    }

    /**
     * [학생] 포트폴리오 항목에 첨부파일을 연결. FileGroup이 없으면 새로 만들어 연결한다.
     */
    @Transactional
    public PortfolioResponseDTO attachFiles(Integer studentUserId, Integer careerDocumentId, List<MultipartFile> files) {
        CareerDocument document = careerDocumentAccessHelper.getOwnedPortfolio(studentUserId, careerDocumentId);

        FileGroup fileGroup = document.getFileGroup();
        if (fileGroup == null) {
            fileGroup = fileGroupService.createGroup();
            document.attachFileGroup(fileGroup);
        }
        fileStorageService.storeAll(files, fileGroup, studentUserId);

        log.info("[PortfolioService] 포트폴리오 첨부파일 연결 완료. careerDocumentId: {}, 파일 수: {}", careerDocumentId, files.size());
        return mapToResponseDTO(document);
    }

    /**
     * [학생] 본인 소유 포트폴리오에 실제로 속한 첨부파일만 다운로드 정보를 조회
     */
    public FileStorageService.LoadedFile downloadAttachment(Integer studentUserId, Integer careerDocumentId, Integer storedFileId) {
        CareerDocument document = careerDocumentAccessHelper.getOwnedPortfolio(studentUserId, careerDocumentId);

        boolean belongsToDocument = document.getFileGroup() != null
                && fileGroupService.getFiles(document.getFileGroup()).stream()
                        .anyMatch(storedFile -> storedFile.getStoredFileId().equals(storedFileId));
        if (!belongsToDocument) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        return fileStorageService.load(storedFileId);
    }

    private JsonNode mapToJsonNode(Map<String, Object> map) {
        return map == null ? null : objectMapper.valueToTree(map);
    }

    private Map<String, Object> jsonNodeToMap(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isNull()) {
            return null;
        }
        return objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {});
    }

    private PortfolioResponseDTO mapToResponseDTO(CareerDocument document) {
        List<PortfolioAttachmentResponseDTO> attachments = document.getFileGroup() == null
                ? List.of()
                : fileGroupService.getFiles(document.getFileGroup()).stream()
                        .map(this::mapToAttachmentDTO)
                        .toList();

        return PortfolioResponseDTO.builder()
                .careerDocumentId(document.getCareerDocumentId())
                .studentUserId(document.getStudent().getUserId())
                .documentTitle(document.getDocumentTitle())
                .versionNo(document.getVersionNo())
                .contentData(jsonNodeToMap(document.getContentData()))
                .isPublic(document.isPublicDocument())
                .aiAssistanceUsed(document.isAiAssistanceUsed())
                .attachments(attachments)
                .createdAt(DateTimeUtils.toKstOffsetDateTime(document.getCreatedAt()))
                .updatedAt(DateTimeUtils.toKstOffsetDateTime(document.getUpdatedAt()))
                .build();
    }

    private PortfolioAttachmentResponseDTO mapToAttachmentDTO(StoredFile storedFile) {
        return PortfolioAttachmentResponseDTO.builder()
                .storedFileId(storedFile.getStoredFileId())
                .originalFileName(storedFile.getOriginalFileName())
                .contentType(storedFile.getContentType())
                .fileSize(storedFile.getFileSize())
                .build();
    }

    private PortfolioSummaryResponseDTO mapToSummaryDTO(CareerDocumentRepository.PortfolioSummaryProjection projection) {
        return PortfolioSummaryResponseDTO.builder()
                .careerDocumentId(projection.getCareerDocumentId())
                .documentTitle(projection.getDocumentTitle())
                .versionNo(projection.getVersionNo())
                .isPublic(projection.getIsPublic())
                .attachmentCount(Math.toIntExact(projection.getAttachmentCount()))
                .updatedAt(DateTimeUtils.toKstOffsetDateTime(projection.getUpdatedAt()))
                .build();
    }
}
