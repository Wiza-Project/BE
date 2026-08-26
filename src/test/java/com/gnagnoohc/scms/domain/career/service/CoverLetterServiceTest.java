package com.gnagnoohc.scms.domain.career.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnagnoohc.scms.domain.career.dto.coverletter.CoverLetterCreateRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.coverletter.CoverLetterQuestionDTO;
import com.gnagnoohc.scms.domain.career.dto.coverletter.CoverLetterResponseDTO;
import com.gnagnoohc.scms.domain.career.dto.coverletter.CoverLetterUpdateRequestDTO;
import com.gnagnoohc.scms.domain.career.entity.CareerDocument;
import com.gnagnoohc.scms.domain.career.helper.CareerDocumentAccessHelper;
import com.gnagnoohc.scms.domain.career.repository.CareerDocumentRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoverLetterServiceTest {

    @Mock
    CareerDocumentRepository careerDocumentRepository;

    @Mock
    AppUserRepository appUserRepository;

    @Mock
    CareerDocumentAccessHelper careerDocumentAccessHelper;

    @InjectMocks
    CoverLetterService coverLetterService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createCoverLetter_whenNoneExists_createsVersion1() {
        AppUser student = buildStudentFixture(100, "20240001", "홍길동");
        CoverLetterCreateRequestDTO requestDTO = buildRequestDTO();

        when(appUserRepository.findById(100)).thenReturn(Optional.of(student));
        when(careerDocumentRepository.findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(100, CareerDocument.TYPE_COVER_LETTER))
                .thenReturn(Optional.empty());
        when(careerDocumentRepository.saveAndFlush(any(CareerDocument.class)))
                .thenAnswer(invocation -> {
                    CareerDocument saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "careerDocumentId", 1);
                    return saved;
                });

        CoverLetterResponseDTO response = coverLetterService.createCoverLetter(100, requestDTO);

        assertThat(response.getCareerDocumentId()).isEqualTo(1);
        assertThat(response.getVersionNo()).isEqualTo(1);
        assertThat(response.getQuestions()).hasSize(1);
        assertThat(response.getQuestions().get(0).getCharacterCount()).isEqualTo("답변 본문".length()); // 서버가 answer 길이로 재계산하는지 검증
    }

    @Test
    void createCoverLetter_whenAlreadyExists_throwsAlreadyExists() {
        AppUser student = buildStudentFixture(100, "20240001", "홍길동");
        CareerDocument existing = CareerDocument.createCoverLetter(student, 1, "기존 자소서", null, false);

        when(appUserRepository.findById(100)).thenReturn(Optional.of(student));
        when(careerDocumentRepository.findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(100, CareerDocument.TYPE_COVER_LETTER))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> coverLetterService.createCoverLetter(100, buildRequestDTO()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COVER_LETTER_ALREADY_EXISTS);

        verify(careerDocumentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createCoverLetter_whenVersionInsertConflicts_throwsVersionConflict() {
        AppUser student = buildStudentFixture(100, "20240001", "홍길동");

        when(appUserRepository.findById(100)).thenReturn(Optional.of(student));
        when(careerDocumentRepository.findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(100, CareerDocument.TYPE_COVER_LETTER))
                .thenReturn(Optional.empty());
        when(careerDocumentRepository.saveAndFlush(any(CareerDocument.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate version"));

        assertThatThrownBy(() -> coverLetterService.createCoverLetter(100, buildRequestDTO()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_VERSION_CONFLICT);
    }

    @Test
    void updateCoverLetter_whenOwned_updatesContentAndRecomputesCharacterCount() {
        AppUser student = buildStudentFixture(100, "20240001", "홍길동");
        CareerDocument document = CareerDocument.createCoverLetter(student, 1, "이전 제목", null, false);
        ReflectionTestUtils.setField(document, "careerDocumentId", 1);

        when(careerDocumentAccessHelper.getOwnedCoverLetter(100, 1)).thenReturn(document);

        CoverLetterUpdateRequestDTO requestDTO = new CoverLetterUpdateRequestDTO();
        ReflectionTestUtils.setField(requestDTO, "documentTitle", "수정된 제목");
        ReflectionTestUtils.setField(requestDTO, "questions", List.of(
                CoverLetterQuestionDTO.builder().questionId("Q1").question("지원 동기").answer("짧은 답변").characterCount(999).build()
        ));
        ReflectionTestUtils.setField(requestDTO, "aiAssistanceUsed", true);

        CoverLetterResponseDTO response = coverLetterService.updateCoverLetter(100, 1, requestDTO);

        assertThat(response.getDocumentTitle()).isEqualTo("수정된 제목");
        assertThat(response.isAiAssistanceUsed()).isTrue();
        assertThat(response.getQuestions().get(0).getCharacterCount()).isEqualTo("짧은 답변".length());
    }

    @Test
    void createNextVersion_incrementsFromLatestVersionNo() {
        AppUser student = buildStudentFixture(100, "20240001", "홍길동");
        CareerDocument oldVersion = CareerDocument.createCoverLetter(student, 1, "버전1", toContentData(), false);
        ReflectionTestUtils.setField(oldVersion, "careerDocumentId", 1);
        CareerDocument latestVersion = CareerDocument.createCoverLetter(student, 3, "버전3", toContentData(), false);

        when(careerDocumentAccessHelper.getOwnedCoverLetter(100, 1)).thenReturn(oldVersion);
        when(careerDocumentRepository.findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(100, CareerDocument.TYPE_COVER_LETTER))
                .thenReturn(Optional.of(latestVersion));
        when(careerDocumentRepository.saveAndFlush(any(CareerDocument.class)))
                .thenAnswer(invocation -> {
                    CareerDocument saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "careerDocumentId", 4);
                    return saved;
                });

        CoverLetterResponseDTO response = coverLetterService.createNextVersion(100, 1);

        assertThat(response.getVersionNo()).isEqualTo(4); // 최신 버전(3) 기준 +1, 대상이 과거 버전이어도 최신 기준으로 채번
    }

    @Test
    void deleteCoverLetter_whenOwned_deletesDocument() {
        AppUser student = buildStudentFixture(100, "20240001", "홍길동");
        CareerDocument document = CareerDocument.createCoverLetter(student, 1, "제목", null, false);
        ReflectionTestUtils.setField(document, "careerDocumentId", 1);

        when(careerDocumentAccessHelper.getOwnedCoverLetter(100, 1)).thenReturn(document);

        coverLetterService.deleteCoverLetter(100, 1);

        verify(careerDocumentRepository).delete(document);
    }

    @Test
    void getMyLatestCoverLetter_whenNoneExists_throwsNotFound() {
        when(careerDocumentRepository.findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(100, CareerDocument.TYPE_COVER_LETTER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> coverLetterService.getMyLatestCoverLetter(100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COVER_LETTER_NOT_FOUND);
    }

    private CoverLetterCreateRequestDTO buildRequestDTO() {
        CoverLetterCreateRequestDTO requestDTO = new CoverLetterCreateRequestDTO();
        ReflectionTestUtils.setField(requestDTO, "documentTitle", "2026 하반기 공채 자기소개서");
        ReflectionTestUtils.setField(requestDTO, "questions", List.of(
                CoverLetterQuestionDTO.builder().questionId("Q1").question("지원 동기").answer("답변 본문").characterCount(0).build()
        ));
        ReflectionTestUtils.setField(requestDTO, "aiAssistanceUsed", false);
        return requestDTO;
    }

    private JsonNode toContentData() {
        return objectMapper.createObjectNode();
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
