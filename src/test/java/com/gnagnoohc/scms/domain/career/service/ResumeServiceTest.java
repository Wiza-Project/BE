package com.gnagnoohc.scms.domain.career.service;

import com.gnagnoohc.scms.domain.career.dto.resume.ResumeCreateRequestDTO;
import com.gnagnoohc.scms.domain.career.dto.resume.ResumeUpdateRequestDTO;
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
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    private static final Integer STUDENT_ID = 1;

    @Mock
    CareerDocumentRepository careerDocumentRepository;

    @Mock
    AppUserRepository appUserRepository;

    @Mock
    CareerDocumentAccessHelper careerDocumentAccessHelper;

    @InjectMocks
    ResumeService resumeService;

    @Test
    void createResume_whenConcurrentFirstVersionInsertFails_throwsResumeAlreadyExists() throws Exception {
        AppUser student = newInstance(AppUser.class);
        ResumeCreateRequestDTO request = new ResumeCreateRequestDTO();
        ReflectionTestUtils.setField(request, "documentTitle", "이력서");
        when(appUserRepository.findByIdForUpdate(STUDENT_ID)).thenReturn(Optional.of(student));
        when(careerDocumentRepository.findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(
                STUDENT_ID, CareerDocument.TYPE_RESUME)).thenReturn(Optional.empty());
        when(careerDocumentRepository.saveAndFlush(any(CareerDocument.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint uq_career_document_student_type_version"));

        assertThatThrownBy(() -> resumeService.createResume(STUDENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESUME_ALREADY_EXISTS);
    }

    @Test
    void updateResume_whenTargetIsNotLatestVersion_rejectsUpdate() throws Exception {
        AppUser student = newInstance(AppUser.class);
        CareerDocument oldVersion = CareerDocument.createResume(student, 1, "이전 제목", null);
        CareerDocument latestVersion = CareerDocument.createResume(student, 2, "최신 제목", null);
        ReflectionTestUtils.setField(oldVersion, "careerDocumentId", 10);
        ReflectionTestUtils.setField(latestVersion, "careerDocumentId", 20);
        ResumeUpdateRequestDTO request = new ResumeUpdateRequestDTO();
        ReflectionTestUtils.setField(request, "documentTitle", "수정 제목");
        when(appUserRepository.findByIdForUpdate(STUDENT_ID)).thenReturn(Optional.of(student));
        when(careerDocumentAccessHelper.getOwnedResume(STUDENT_ID, 10)).thenReturn(oldVersion);
        when(careerDocumentRepository.findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(
                STUDENT_ID, CareerDocument.TYPE_RESUME)).thenReturn(Optional.of(latestVersion));

        assertThatThrownBy(() -> resumeService.updateResume(STUDENT_ID, 10, request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESUME_NOT_LATEST_VERSION);
        assertThat(oldVersion.getDocumentTitle()).isEqualTo("이전 제목");
    }

    @Test
    void createResume_whenIntegrityViolationIsNotVersionConstraint_rethrowsOriginalException() throws Exception {
        AppUser student = newInstance(AppUser.class);
        ResumeCreateRequestDTO request = new ResumeCreateRequestDTO();
        ReflectionTestUtils.setField(request, "documentTitle", "이력서");
        DataIntegrityViolationException exception = new DataIntegrityViolationException("document_title must not be null");
        when(appUserRepository.findByIdForUpdate(STUDENT_ID)).thenReturn(Optional.of(student));
        when(careerDocumentRepository.findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(
                STUDENT_ID, CareerDocument.TYPE_RESUME)).thenReturn(Optional.empty());
        when(careerDocumentRepository.saveAndFlush(any(CareerDocument.class))).thenThrow(exception);

        assertThatThrownBy(() -> resumeService.createResume(STUDENT_ID, request))
                .isSameAs(exception);
    }

    @Test
    void updateResume_locksStudentBeforeCheckingLatestVersion() throws Exception {
        AppUser student = newInstance(AppUser.class);
        CareerDocument document = CareerDocument.createResume(student, 1, "이력서", null);
        ReflectionTestUtils.setField(document, "careerDocumentId", 10);
        setAuditTimes(document);
        ResumeUpdateRequestDTO request = new ResumeUpdateRequestDTO();
        ReflectionTestUtils.setField(request, "documentTitle", "수정 제목");
        when(appUserRepository.findByIdForUpdate(STUDENT_ID)).thenReturn(Optional.of(student));
        when(careerDocumentAccessHelper.getOwnedResume(STUDENT_ID, 10)).thenReturn(document);
        when(careerDocumentRepository.findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(
                STUDENT_ID, CareerDocument.TYPE_RESUME)).thenReturn(Optional.of(document));

        resumeService.updateResume(STUDENT_ID, 10, request);

        inOrder(appUserRepository, careerDocumentAccessHelper, careerDocumentRepository)
                .verify(appUserRepository).findByIdForUpdate(STUDENT_ID);
    }

    @Test
    void createNextVersion_locksSameStudentBeforeCreatingSnapshot() throws Exception {
        AppUser student = newInstance(AppUser.class);
        CareerDocument base = CareerDocument.createResume(student, 1, "이력서", null);
        CareerDocument saved = CareerDocument.createResume(student, 2, "이력서", null);
        ReflectionTestUtils.setField(base, "careerDocumentId", 10);
        ReflectionTestUtils.setField(saved, "careerDocumentId", 20);
        setAuditTimes(saved);
        when(appUserRepository.findByIdForUpdate(STUDENT_ID)).thenReturn(Optional.of(student));
        when(careerDocumentAccessHelper.getOwnedResume(STUDENT_ID, 10)).thenReturn(base);
        when(careerDocumentRepository.findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(
                STUDENT_ID, CareerDocument.TYPE_RESUME)).thenReturn(Optional.of(base));
        when(careerDocumentRepository.saveAndFlush(any(CareerDocument.class))).thenReturn(saved);

        resumeService.createNextVersion(STUDENT_ID, 10);

        inOrder(appUserRepository, careerDocumentAccessHelper, careerDocumentRepository)
                .verify(appUserRepository).findByIdForUpdate(STUDENT_ID);
    }

    private static <T> T newInstance(Class<T> type) throws ReflectiveOperationException {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void setAuditTimes(CareerDocument document) {
        Instant now = Instant.now();
        ReflectionTestUtils.setField(document, "createdAt", now);
        ReflectionTestUtils.setField(document, "updatedAt", now);
    }
}
