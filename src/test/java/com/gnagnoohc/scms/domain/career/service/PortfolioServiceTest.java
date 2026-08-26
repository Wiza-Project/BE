package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.portfolio.PortfolioCreateRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.portfolio.PortfolioResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.portfolio.PortfolioUpdateRequestDTO;
import com.gnagnoohc.scms.domain.career.entity.CareerDocument;
import com.gnagnoohc.scms.domain.career.helper.CareerDocumentAccessHelper;
import com.gnagnoohc.scms.domain.career.repository.CareerDocumentRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.entity.FileGroup;
import com.gnagnoohc.scms.global.common.entity.StoredFile;
import com.gnagnoohc.scms.global.common.service.FileGroupService;
import com.gnagnoohc.scms.global.common.service.FileStorageService;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    CareerDocumentRepository careerDocumentRepository;

    @Mock
    AppUserRepository appUserRepository;

    @Mock
    CareerDocumentAccessHelper careerDocumentAccessHelper;

    @Mock
    FileGroupService fileGroupService;

    @Mock
    FileStorageService fileStorageService;

    @InjectMocks
    PortfolioService portfolioService;

    @Test
    void createPortfolio_whenExistingItemsPresent_assignsNextVersionNo() {
        AppUser student = buildStudentFixture(100, "20240001", "홍길동");
        CareerDocument existingItem = CareerDocument.createPortfolio(student, 2, "이전 항목", null, false);

        when(appUserRepository.findById(100)).thenReturn(Optional.of(student));
        when(careerDocumentRepository.findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(100, CareerDocument.TYPE_PORTFOLIO))
                .thenReturn(Optional.of(existingItem));
        when(careerDocumentRepository.saveAndFlush(any(CareerDocument.class)))
                .thenAnswer(invocation -> {
                    CareerDocument saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "careerDocumentId", 10);
                    return saved;
                });

        PortfolioCreateRequestDTO requestDTO = new PortfolioCreateRequestDTO();
        ReflectionTestUtils.setField(requestDTO, "documentTitle", "졸업작품");
        ReflectionTestUtils.setField(requestDTO, "contentData", Map.of("summary", "학사관리 시스템"));
        ReflectionTestUtils.setField(requestDTO, "aiAssistanceUsed", false);

        PortfolioResponseDTO response = portfolioService.createPortfolio(100, requestDTO);

        assertThat(response.getVersionNo()).isEqualTo(3);
        assertThat(response.getContentData()).containsEntry("summary", "학사관리 시스템");
        assertThat(response.getIsPublic()).isFalse();
    }

    @Test
    void createPortfolio_whenFirstItem_assignsVersionNo1() {
        AppUser student = buildStudentFixture(100, "20240001", "홍길동");

        when(appUserRepository.findById(100)).thenReturn(Optional.of(student));
        when(careerDocumentRepository.findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(100, CareerDocument.TYPE_PORTFOLIO))
                .thenReturn(Optional.empty());
        when(careerDocumentRepository.saveAndFlush(any(CareerDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioCreateRequestDTO requestDTO = new PortfolioCreateRequestDTO();
        ReflectionTestUtils.setField(requestDTO, "documentTitle", "첫 항목");
        ReflectionTestUtils.setField(requestDTO, "aiAssistanceUsed", false);

        PortfolioResponseDTO response = portfolioService.createPortfolio(100, requestDTO);

        assertThat(response.getVersionNo()).isEqualTo(1);
    }

    @Test
    void updatePortfolio_whenOwned_updatesTitleAndContent() {
        AppUser student = buildStudentFixture(100, "20240001", "홍길동");
        CareerDocument document = CareerDocument.createPortfolio(student, 1, "이전 제목", null, false);
        ReflectionTestUtils.setField(document, "careerDocumentId", 1);

        when(careerDocumentAccessHelper.getOwnedPortfolio(100, 1)).thenReturn(document);

        PortfolioUpdateRequestDTO requestDTO = new PortfolioUpdateRequestDTO();
        ReflectionTestUtils.setField(requestDTO, "documentTitle", "수정된 제목");
        ReflectionTestUtils.setField(requestDTO, "contentData", Map.of("summary", "수정됨"));
        ReflectionTestUtils.setField(requestDTO, "aiAssistanceUsed", true);

        PortfolioResponseDTO response = portfolioService.updatePortfolio(100, 1, requestDTO);

        assertThat(response.getDocumentTitle()).isEqualTo("수정된 제목");
        assertThat(response.isAiAssistanceUsed()).isTrue();
    }

    @Test
    void changeVisibility_updatesPublicFlag() {
        AppUser student = buildStudentFixture(100, "20240001", "홍길동");
        CareerDocument document = CareerDocument.createPortfolio(student, 1, "제목", null, false);
        ReflectionTestUtils.setField(document, "careerDocumentId", 1);

        when(careerDocumentAccessHelper.getOwnedPortfolio(100, 1)).thenReturn(document);

        PortfolioResponseDTO response = portfolioService.changeVisibility(100, 1, true);

        assertThat(response.getIsPublic()).isTrue();
    }

    @Test
    void deletePortfolio_whenFileGroupExists_deletesAttachedFilesAndDocument() {
        AppUser student = buildStudentFixture(100, "20240001", "홍길동");
        CareerDocument document = CareerDocument.createPortfolio(student, 1, "제목", null, false);
        ReflectionTestUtils.setField(document, "careerDocumentId", 1);

        FileGroup fileGroup = buildFileGroupFixture(1);
        document.attachFileGroup(fileGroup);
        StoredFile storedFile = StoredFile.builder().fileGroup(fileGroup).originalFileName("a.pdf").storageKey("k").fileSize(1L).createdBy(100).build();

        when(careerDocumentAccessHelper.getOwnedPortfolio(100, 1)).thenReturn(document);
        when(fileGroupService.getFiles(fileGroup)).thenReturn(List.of(storedFile));

        portfolioService.deletePortfolio(100, 1);

        verify(fileStorageService).delete(storedFile);
        verify(careerDocumentRepository).delete(document);
    }

    @Test
    void attachFiles_whenNoFileGroupYet_createsGroupThenStores() {
        AppUser student = buildStudentFixture(100, "20240001", "홍길동");
        CareerDocument document = CareerDocument.createPortfolio(student, 1, "제목", null, false);
        ReflectionTestUtils.setField(document, "careerDocumentId", 1);

        FileGroup newGroup = buildFileGroupFixture(5);
        MultipartFile file = mockMultipartFile();

        when(careerDocumentAccessHelper.getOwnedPortfolio(100, 1)).thenReturn(document);
        when(fileGroupService.createGroup()).thenReturn(newGroup);
        when(fileGroupService.getFiles(newGroup)).thenReturn(List.of());

        portfolioService.attachFiles(100, 1, List.of(file));

        assertThat(document.getFileGroup()).isEqualTo(newGroup);
        verify(fileStorageService).storeAll(List.of(file), newGroup, 100);
    }

    @Test
    void downloadAttachment_whenFileNotInGroup_throwsResourceNotFound() {
        AppUser student = buildStudentFixture(100, "20240001", "홍길동");
        CareerDocument document = CareerDocument.createPortfolio(student, 1, "제목", null, false);
        FileGroup fileGroup = buildFileGroupFixture(1);
        document.attachFileGroup(fileGroup);

        when(careerDocumentAccessHelper.getOwnedPortfolio(100, 1)).thenReturn(document);
        when(fileGroupService.getFiles(fileGroup)).thenReturn(List.of());

        assertThatThrownBy(() -> portfolioService.downloadAttachment(100, 1, 999))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private MultipartFile mockMultipartFile() {
        return new MultipartFile() {
            @Override public String getName() { return "files"; }
            @Override public String getOriginalFilename() { return "a.pdf"; }
            @Override public String getContentType() { return "application/pdf"; }
            @Override public boolean isEmpty() { return false; }
            @Override public long getSize() { return 1L; }
            @Override public byte[] getBytes() { return new byte[0]; }
            @Override public java.io.InputStream getInputStream() { return java.io.InputStream.nullInputStream(); }
            @Override public void transferTo(java.io.File dest) { }
        };
    }

    private FileGroup buildFileGroupFixture(Integer fileGroupId) {
        FileGroup fileGroup = FileGroup.create();
        ReflectionTestUtils.setField(fileGroup, "fileGroupId", fileGroupId);
        return fileGroup;
    }

    private AppUser buildStudentFixture(Integer userId, String universityNo, String userName) {
        try {
            Constructor<AppUser> constructor = AppUser.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            AppUser student = constructor.newInstance();
            ReflectionTestUtils.setField(student, "userId", userId);
            ReflectionTestUtils.setField(student, "universityNo", universityNo);
            ReflectionTestUtils.setField(student, "userName", userName);
            return student;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
