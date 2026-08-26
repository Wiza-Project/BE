package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramRegisterRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramAdminListItemResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramDetailResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramFileUploadResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramRegisterResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.request.ProgramUpdateRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramUpdateResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.domain.program.entity.ProgramSession;
import com.gnagnoohc.scms.domain.program.entity.ProgramStatus;
import com.gnagnoohc.scms.domain.program.repository.CompetencyOptionRepository;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramSessionRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.entity.FileGroup;
import com.gnagnoohc.scms.global.common.entity.StoredFile;
import com.gnagnoohc.scms.global.common.helper.FileUploadValidator;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.common.repository.FileGroupRepository;
import com.gnagnoohc.scms.global.common.service.FileGroupService;
import com.gnagnoohc.scms.global.common.service.FileStorageService;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import com.gnagnoohc.scms.domain.program.dto.response.ProgramListItemResponseDTO;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramServiceTest {

    @Mock
    ExtracurricularProgramRepository programRepository;

    @Mock
    CompetencyOptionRepository competencyOptionRepository;

    @Mock
    CommonCodeRepository commonCodeRepository;

    @Mock
    ProgramSessionRepository programSessionRepository;

    @Mock
    ProgramApplicationRepository applicationRepository;

    @Mock
    FileGroupRepository fileGroupRepository;

    @Mock
    FileGroupService fileGroupService;

    @Mock
    FileStorageService fileStorageService;

    @Spy
    FileUploadValidator fileUploadValidator = new FileUploadValidator();

    @InjectMocks
    ProgramService programService;

    @Test
    void register_setsInitialStatusToDraft_andResponseHasKoreanLabel() throws Exception {
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        when(programRepository.insertProgram(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), statusCaptor.capture(), any()
        )).thenReturn(1);

        Instant recruitmentStartsAt = Instant.now();
        Instant recruitmentEndsAt = recruitmentStartsAt.plusSeconds(3600);
        Instant operationStartsAt = recruitmentEndsAt;
        Instant operationEndsAt = operationStartsAt.plusSeconds(3600);

        ProgramRegisterRequestDTO request = new ProgramRegisterRequestDTO(
                null, 1, 2, 3, null,
                "프로그램명", "설명",
                recruitmentStartsAt, recruitmentEndsAt, operationStartsAt, operationEndsAt,
                10, null
        );

        ProgramRegisterResponseDTO response = programService.register(request, 100, 11);

        assertThat(statusCaptor.getValue()).isEqualTo("DRAFT");
        assertThat(response.programStatus()).isEqualTo("모집중");
    }

    @Test
    void register_whenOperatingUnitAndProgramTypeCodeIdsAreNull_appliesDefaults() throws Exception {
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("PROGRAM_TYPE"))
                .thenReturn(List.of(buildCommonCodeFixture(22, "PROGRAM_TYPE", "PT100")));

        ArgumentCaptor<Integer> operatingUnitCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> programTypeCaptor = ArgumentCaptor.forClass(Integer.class);
        when(programRepository.insertProgram(
                any(), operatingUnitCaptor.capture(), programTypeCaptor.capture(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(1);

        Instant recruitmentStartsAt = Instant.now();
        Instant recruitmentEndsAt = recruitmentStartsAt.plusSeconds(3600);
        Instant operationStartsAt = recruitmentEndsAt;
        Instant operationEndsAt = operationStartsAt.plusSeconds(3600);

        ProgramRegisterRequestDTO request = new ProgramRegisterRequestDTO(
                null, null, null, 3, null,
                "프로그램명", "설명",
                recruitmentStartsAt, recruitmentEndsAt, operationStartsAt, operationEndsAt,
                10, null
        );

        programService.register(request, 100, 11);

        assertThat(operatingUnitCaptor.getValue()).isEqualTo(11);
        assertThat(programTypeCaptor.getValue()).isEqualTo(22);
    }

    @Test
    void register_whenDepartmentIsNotOperatingDepartment_throwsDepartmentForbidden() throws Exception {
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        Instant recruitmentStartsAt = Instant.now();
        Instant recruitmentEndsAt = recruitmentStartsAt.plusSeconds(3600);
        Instant operationStartsAt = recruitmentEndsAt;
        Instant operationEndsAt = operationStartsAt.plusSeconds(3600);

        ProgramRegisterRequestDTO request = new ProgramRegisterRequestDTO(
                null, 1, 2, 3, null,
                "프로그램명", "설명",
                recruitmentStartsAt, recruitmentEndsAt, operationStartsAt, operationEndsAt,
                10, null
        );

        assertThatThrownBy(() -> programService.register(request, 100, 99))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_FORBIDDEN);
    }

    @Test
    void register_whenDepartmentCodeIdIsNull_throwsDepartmentForbidden() {
        Instant recruitmentStartsAt = Instant.now();
        Instant recruitmentEndsAt = recruitmentStartsAt.plusSeconds(3600);
        Instant operationStartsAt = recruitmentEndsAt;
        Instant operationEndsAt = operationStartsAt.plusSeconds(3600);

        ProgramRegisterRequestDTO request = new ProgramRegisterRequestDTO(
                null, 1, 2, 3, null,
                "프로그램명", "설명",
                recruitmentStartsAt, recruitmentEndsAt, operationStartsAt, operationEndsAt,
                10, null
        );

        assertThatThrownBy(() -> programService.register(request, 100, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_FORBIDDEN);
    }

    @Test
    void register_whenFileGroupUniqueConstraintViolated_throwsFileGroupAlreadyLinked() throws Exception {
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        FileGroup fileGroup = FileGroup.create();
        ReflectionTestUtils.setField(fileGroup, "fileGroupId", 77);
        when(fileGroupRepository.findById(77)).thenReturn(Optional.of(fileGroup));
        StoredFile storedFile = StoredFile.builder()
                .originalFileName("운영계획서.pdf").createdBy(100).build();
        when(fileGroupService.getFiles(fileGroup)).thenReturn(List.of(storedFile));
        // 애플리케이션 계층 검사는 통과(false)하지만, 그 직후의 실제 INSERT 시점에는 동시 요청으로
        // 이미 다른 프로그램에 연결돼버린 레이스 케이스를 재현한다.
        when(programRepository.existsByFileGroup_FileGroupId(77)).thenReturn(false);
        when(programRepository.insertProgram(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_extracurricular_program_file_group_id\""));

        Instant recruitmentStartsAt = Instant.now();
        Instant recruitmentEndsAt = recruitmentStartsAt.plusSeconds(3600);
        Instant operationStartsAt = recruitmentEndsAt;
        Instant operationEndsAt = operationStartsAt.plusSeconds(3600);

        ProgramRegisterRequestDTO request = new ProgramRegisterRequestDTO(
                77, 1, 2, 3, null,
                "프로그램명", "설명",
                recruitmentStartsAt, recruitmentEndsAt, operationStartsAt, operationEndsAt,
                10, null
        );

        assertThatThrownBy(() -> programService.register(request, 100, 11))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_FILE_GROUP_ALREADY_LINKED);
    }

    @Test
    void uploadOperationPlan_withPdfFile_createsFileGroupAndStoresFile() throws Exception {
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        FileGroup fileGroup = FileGroup.create();
        ReflectionTestUtils.setField(fileGroup, "fileGroupId", 77);
        when(fileGroupService.createGroup()).thenReturn(fileGroup);

        MultipartFile file = new MockMultipartFile(
                "file", "운영계획서.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46, '-', '1', '.', '4'}); // "%PDF-1.4"

        ProgramFileUploadResponseDTO response = programService.uploadOperationPlan(file, 100, 11);

        assertThat(response.fileGroupId()).isEqualTo(77);
        assertThat(response.fileName()).isEqualTo("운영계획서.pdf");
        verify(fileStorageService).store(file, fileGroup, 100);
    }

    @Test
    void uploadOperationPlan_withNonPdfFile_throwsInvalidFileType() throws Exception {
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        MultipartFile file = new MockMultipartFile(
                "file", "poster.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0}); // JPEG magic bytes

        assertThatThrownBy(() -> programService.uploadOperationPlan(file, 100, 11))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_TYPE);

        verifyNoInteractions(fileGroupService, fileStorageService);
    }

    @Test
    void uploadOperationPlan_whenDepartmentIsNotOperatingDepartment_throwsDepartmentForbidden() throws Exception {
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        MultipartFile file = new MockMultipartFile(
                "file", "운영계획서.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46, '-', '1', '.', '4'});

        assertThatThrownBy(() -> programService.uploadOperationPlan(file, 100, 99))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_FORBIDDEN);

        verifyNoInteractions(fileGroupService, fileStorageService);
    }

    @Test
    void update_whenRecruitmentPeriodOngoing_succeeds_andResponseStatusLabelReflectsEntity() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserId()).thenReturn(100);

        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, managerUser, now.plusSeconds(3600), ProgramStatus.DRAFT);
        ReflectionTestUtils.setField(program, "operatingUnitCode", buildCommonCodeFixture(11, "DEPARTMENT", "D200"));

        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(programRepository.updateProgram(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(1);

        Instant recruitmentStartsAt = now;
        Instant recruitmentEndsAt = now.plusSeconds(1800);
        Instant operationStartsAt = recruitmentEndsAt;
        Instant operationEndsAt = operationStartsAt.plusSeconds(3600);

        ProgramUpdateRequestDTO request = new ProgramUpdateRequestDTO(
                null, 2, 3, null,
                "수정된 프로그램명", "설명",
                recruitmentStartsAt, recruitmentEndsAt, operationStartsAt, operationEndsAt,
                20, null
        );

        ProgramUpdateResponseDTO response = programService.update(1, request, 100, 11);

        assertThat(response.programStatus()).isEqualTo("모집중");
    }

    @Test
    void update_ignoresRequestOperatingUnitCodeId_alwaysKeepsProgramsExistingValue() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserId()).thenReturn(100);

        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, managerUser, now.plusSeconds(3600), ProgramStatus.DRAFT);
        ReflectionTestUtils.setField(program, "operatingUnitCode", buildCommonCodeFixture(11, "DEPARTMENT", "D200"));

        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        ArgumentCaptor<Integer> operatingUnitCaptor = ArgumentCaptor.forClass(Integer.class);
        when(programRepository.updateProgram(
                any(), any(), operatingUnitCaptor.capture(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(1);

        Instant recruitmentStartsAt = now;
        Instant recruitmentEndsAt = now.plusSeconds(1800);
        Instant operationStartsAt = recruitmentEndsAt;
        Instant operationEndsAt = operationStartsAt.plusSeconds(3600);

        ProgramUpdateRequestDTO request = new ProgramUpdateRequestDTO(
                null, 2, 3, null,
                "수정된 프로그램명", "설명",
                recruitmentStartsAt, recruitmentEndsAt, operationStartsAt, operationEndsAt,
                20, null
        );

        programService.update(1, request, 100, 11);

        assertThat(operatingUnitCaptor.getValue()).isEqualTo(11);
    }

    @Test
    void update_whenFileGroupUniqueConstraintViolated_throwsFileGroupAlreadyLinked() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserId()).thenReturn(100);

        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, managerUser, now.plusSeconds(3600), ProgramStatus.DRAFT);
        ReflectionTestUtils.setField(program, "operatingUnitCode", buildCommonCodeFixture(11, "DEPARTMENT", "D200"));
        when(programRepository.findById(1)).thenReturn(Optional.of(program));

        FileGroup fileGroup = FileGroup.create();
        ReflectionTestUtils.setField(fileGroup, "fileGroupId", 77);
        when(fileGroupRepository.findById(77)).thenReturn(Optional.of(fileGroup));
        StoredFile storedFile = StoredFile.builder()
                .originalFileName("운영계획서.pdf").createdBy(100).build();
        when(fileGroupService.getFiles(fileGroup)).thenReturn(List.of(storedFile));
        // register 테스트와 동일하게, 애플리케이션 계층 검사는 통과(false)하지만 UPDATE 시점에
        // 동시 요청으로 이미 다른 프로그램에 연결돼버린 레이스 케이스를 재현한다.
        when(programRepository.existsByFileGroup_FileGroupIdAndProgramIdNot(77, 1)).thenReturn(false);
        when(programRepository.updateProgram(
                any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_extracurricular_program_file_group_id\""));

        Instant recruitmentStartsAt = now;
        Instant recruitmentEndsAt = now.plusSeconds(1800);
        Instant operationStartsAt = recruitmentEndsAt;
        Instant operationEndsAt = operationStartsAt.plusSeconds(3600);

        ProgramUpdateRequestDTO request = new ProgramUpdateRequestDTO(
                77, 2, 3, null,
                "수정된 프로그램명", "설명",
                recruitmentStartsAt, recruitmentEndsAt, operationStartsAt, operationEndsAt,
                20, null
        );

        assertThatThrownBy(() -> programService.update(1, request, 100, 11))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_FILE_GROUP_ALREADY_LINKED);
    }

    @Test
    void update_whenRecruitmentEnded_throwsProgramNotEditable() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserId()).thenReturn(100);

        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, managerUser, now.minusSeconds(3600), ProgramStatus.DRAFT);

        when(programRepository.findById(1)).thenReturn(Optional.of(program));

        ProgramUpdateRequestDTO request = new ProgramUpdateRequestDTO(
                null, 2, 3, null,
                "수정된 프로그램명", "설명",
                now.minusSeconds(7200), now.minusSeconds(3600),
                now.minusSeconds(3600), now.minusSeconds(1800),
                20, null
        );

        assertThatThrownBy(() -> programService.update(1, request, 100, 11))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_EDITABLE);
    }

    @Test
    void update_whenDepartmentIsNotOperatingDepartment_throwsDepartmentForbidden() throws Exception {
        AppUser managerUser = mock(AppUser.class);

        ExtracurricularProgram program = buildProgramFixture(
                1, managerUser, Instant.now().plusSeconds(3600), ProgramStatus.DRAFT);

        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        Instant now = Instant.now();
        ProgramUpdateRequestDTO request = new ProgramUpdateRequestDTO(
                null, 2, 3, null,
                "수정된 프로그램명", "설명",
                now, now.plusSeconds(1800), now.plusSeconds(1800), now.plusSeconds(3600),
                20, null
        );

        assertThatThrownBy(() -> programService.update(1, request, 100, 99))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_FORBIDDEN);
    }

    @Test
    void delete_whenDepartmentIsOperatingDepartment_succeeds() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserId()).thenReturn(100);

        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, managerUser, now.plusSeconds(3600), ProgramStatus.DRAFT);

        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(programRepository.deleteProgram(eq(1), any())).thenReturn(1);

        programService.delete(1, 100, 11);

        verify(programRepository).deleteProgram(eq(1), any());
    }

    @Test
    void delete_whenDepartmentIsNotOperatingDepartment_throwsDepartmentForbidden() throws Exception {
        AppUser managerUser = mock(AppUser.class);

        ExtracurricularProgram program = buildProgramFixture(
                1, managerUser, Instant.now().plusSeconds(3600), ProgramStatus.DRAFT);

        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(commonCodeRepository.findByCodeGroupAndActiveTrueOrderBySortOrderAsc("DEPARTMENT"))
                .thenReturn(List.of(buildCommonCodeFixture(11, "DEPARTMENT", "D200")));

        assertThatThrownBy(() -> programService.delete(1, 100, 99))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_FORBIDDEN);
    }

    @Test
    void list_mapsSearchResultPageToPageResponseOfListItemDto() throws Exception {
        CommonCode operatingUnitCode = buildCommonCodeFixture(11, "DEPARTMENT", "D200");
        ReflectionTestUtils.setField(operatingUnitCode, "codeName", "비교과운영부서");
        CommonCode programTypeCode = buildCommonCodeFixture(22, "PROGRAM_TYPE", "PT100");
        ReflectionTestUtils.setField(programTypeCode, "codeName", "학습");
        Competency competency = buildCompetencyFixture(33, "리더십");

        ExtracurricularProgram program = buildProgramFixture(
                1, null, Instant.now().plusSeconds(3600), ProgramStatus.DRAFT);
        ReflectionTestUtils.setField(program, "programName", "프로그램명");
        ReflectionTestUtils.setField(program, "capacity", 10);
        ReflectionTestUtils.setField(program, "operatingUnitCode", operatingUnitCode);
        ReflectionTestUtils.setField(program, "programTypeCode", programTypeCode);
        ReflectionTestUtils.setField(program, "competency", competency);

        Pageable pageable = PageRequest.of(0, 20);
        when(programRepository.search(ProgramStatus.DRAFT, "프로그램", null, pageable))
                .thenReturn(new PageImpl<>(List.of(program), pageable, 1));
        ProgramApplicationRepository.ProgramApplicantCount applicantCountFixture = buildApplicantCountFixture(1, 4L);
        when(applicationRepository.countActiveApplicantsByProgramIds(List.of(1)))
                .thenReturn(List.of(applicantCountFixture));
        when(applicationRepository.findMyApplicationStatusesByProgramIds(100, List.of(1)))
                .thenReturn(List.of());

        PageResponse<ProgramListItemResponseDTO> response =
                programService.list(ProgramStatus.DRAFT, "프로그램", null, 100, pageable);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        ProgramListItemResponseDTO item = response.content().get(0);
        assertThat(item.programId()).isEqualTo(1);
        assertThat(item.programName()).isEqualTo("프로그램명");
        assertThat(item.operatingUnitCodeName()).isEqualTo("비교과운영부서");
        assertThat(item.programTypeCodeName()).isEqualTo("학습");
        assertThat(item.competencyName()).isEqualTo("리더십");
        assertThat(item.programStatus()).isEqualTo("DRAFT");
        assertThat(item.programStatusLabel()).isEqualTo("모집중");
        assertThat(item.applicantCount()).isEqualTo(4L);
        assertThat(item.remainingCapacity()).isEqualTo(6);
        assertThat(item.myApplicationStatus()).isNull();
    }

    @Test
    void list_whenStudentAlreadyApplied_fillsMyApplicationStatus() throws Exception {
        CommonCode operatingUnitCode = buildCommonCodeFixture(11, "DEPARTMENT", "D200");
        CommonCode programTypeCode = buildCommonCodeFixture(22, "PROGRAM_TYPE", "PT100");
        Competency competency = buildCompetencyFixture(33, "리더십");

        ExtracurricularProgram program = buildProgramFixture(
                1, null, Instant.now().plusSeconds(3600), ProgramStatus.DRAFT);
        ReflectionTestUtils.setField(program, "programName", "프로그램명");
        ReflectionTestUtils.setField(program, "capacity", 10);
        ReflectionTestUtils.setField(program, "operatingUnitCode", operatingUnitCode);
        ReflectionTestUtils.setField(program, "programTypeCode", programTypeCode);
        ReflectionTestUtils.setField(program, "competency", competency);

        ProgramApplicationRepository.ProgramApplicantCount applicantCountFixture = buildApplicantCountFixture(1, 1L);
        ProgramApplicationRepository.MyApplicationStatusProjection myApplicationStatusFixture =
                buildMyApplicationStatusFixture(1, "APPLIED");

        Pageable pageable = PageRequest.of(0, 20);
        when(programRepository.search(null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(program), pageable, 1));
        when(applicationRepository.countActiveApplicantsByProgramIds(List.of(1)))
                .thenReturn(List.of(applicantCountFixture));
        when(applicationRepository.findMyApplicationStatusesByProgramIds(100, List.of(1)))
                .thenReturn(List.of(myApplicationStatusFixture));

        PageResponse<ProgramListItemResponseDTO> response =
                programService.list(null, null, null, 100, pageable);

        ProgramListItemResponseDTO item = response.content().get(0);
        assertThat(item.myApplicationStatus()).isEqualTo("APPLIED");
        assertThat(item.myApplicationStatusLabel()).isEqualTo("신청완료");
    }

    @Test
    void listMine_scopesSearchToManagerUserId() throws Exception {
        CommonCode operatingUnitCode = buildCommonCodeFixture(11, "DEPARTMENT", "D200");
        CommonCode programTypeCode = buildCommonCodeFixture(22, "PROGRAM_TYPE", "PT100");

        ExtracurricularProgram program = buildProgramFixture(
                1, null, Instant.now().plusSeconds(3600), ProgramStatus.DRAFT);
        ReflectionTestUtils.setField(program, "programName", "프로그램명");
        ReflectionTestUtils.setField(program, "capacity", 10);
        ReflectionTestUtils.setField(program, "completionRate", new java.math.BigDecimal("80"));
        ReflectionTestUtils.setField(program, "operatingUnitCode", operatingUnitCode);
        ReflectionTestUtils.setField(program, "programTypeCode", programTypeCode);

        Pageable pageable = PageRequest.of(0, 20);
        when(programRepository.searchByManager(eq(100), any(), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(program), pageable, 1));
        when(applicationRepository.countActiveApplicantsByProgramIds(List.of(1)))
                .thenReturn(List.of());

        PageResponse<ProgramAdminListItemResponseDTO> response =
                programService.listMine(100, null, null, null, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).applicantCount()).isEqualTo(0L);
        verify(programRepository).searchByManager(100, null, null, null, pageable);
    }

    @Test
    void getDetail_returnsProgramWithSessionsAndApplicantCount() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserName()).thenReturn("담당자명");

        CommonCode operatingUnitCode = buildCommonCodeFixture(11, "DEPARTMENT", "D200");
        CommonCode programTypeCode = buildCommonCodeFixture(22, "PROGRAM_TYPE", "PT100");
        Competency competency = buildCompetencyFixture(33, "리더십");

        ExtracurricularProgram program = buildProgramFixture(
                1, managerUser, Instant.now().plusSeconds(3600), ProgramStatus.DRAFT);
        ReflectionTestUtils.setField(program, "programName", "프로그램명");
        ReflectionTestUtils.setField(program, "capacity", 10);
        ReflectionTestUtils.setField(program, "operatingUnitCode", operatingUnitCode);
        ReflectionTestUtils.setField(program, "programTypeCode", programTypeCode);
        ReflectionTestUtils.setField(program, "competency", competency);

        ProgramSession session = mock(ProgramSession.class);
        when(session.getProgram()).thenReturn(program);
        when(session.getSessionNo()).thenReturn(1);

        when(programRepository.findDetailById(1)).thenReturn(Optional.of(program));
        when(programSessionRepository.findByProgram_ProgramIdOrderBySessionNoAsc(1))
                .thenReturn(List.of(session));
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(eq(1), any()))
                .thenReturn(3L);
        when(applicationRepository.findByProgram_ProgramIdAndStudent_UserId(1, 100))
                .thenReturn(Optional.empty());

        ProgramDetailResponseDTO response = programService.getDetail(1, 100);

        assertThat(response.programId()).isEqualTo(1);
        assertThat(response.competencyName()).isEqualTo("리더십");
        assertThat(response.managerUserName()).isEqualTo("담당자명");
        assertThat(response.applicantCount()).isEqualTo(3L);
        assertThat(response.remainingCapacity()).isEqualTo(7);
        assertThat(response.sessions()).hasSize(1);
        assertThat(response.myApplicationStatus()).isNull();
        assertThat(response.fileName()).isNull();
    }

    @Test
    void getDetail_whenFileGroupPresent_returnsFileName() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserName()).thenReturn("담당자명");

        CommonCode operatingUnitCode = buildCommonCodeFixture(11, "DEPARTMENT", "D200");
        CommonCode programTypeCode = buildCommonCodeFixture(22, "PROGRAM_TYPE", "PT100");
        Competency competency = buildCompetencyFixture(33, "리더십");

        ExtracurricularProgram program = buildProgramFixture(
                1, managerUser, Instant.now().plusSeconds(3600), ProgramStatus.DRAFT);
        ReflectionTestUtils.setField(program, "programName", "프로그램명");
        ReflectionTestUtils.setField(program, "capacity", 10);
        ReflectionTestUtils.setField(program, "operatingUnitCode", operatingUnitCode);
        ReflectionTestUtils.setField(program, "programTypeCode", programTypeCode);
        ReflectionTestUtils.setField(program, "competency", competency);

        FileGroup fileGroup = FileGroup.create();
        ReflectionTestUtils.setField(fileGroup, "fileGroupId", 501);
        ReflectionTestUtils.setField(program, "fileGroup", fileGroup);

        StoredFile storedFile = StoredFile.builder().originalFileName("운영계획서.pdf").build();

        when(programRepository.findDetailById(1)).thenReturn(Optional.of(program));
        when(programSessionRepository.findByProgram_ProgramIdOrderBySessionNoAsc(1))
                .thenReturn(List.of());
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(eq(1), any()))
                .thenReturn(0L);
        when(applicationRepository.findByProgram_ProgramIdAndStudent_UserId(1, 100))
                .thenReturn(Optional.empty());
        when(fileGroupService.getFiles(fileGroup)).thenReturn(List.of(storedFile));

        ProgramDetailResponseDTO response = programService.getDetail(1, 100);

        assertThat(response.fileGroupId()).isEqualTo(501);
        assertThat(response.fileName()).isEqualTo("운영계획서.pdf");
    }

    @Test
    void getDetail_whenStudentCancelledApplication_myApplicationStatusIsNull() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserName()).thenReturn("담당자명");

        CommonCode operatingUnitCode = buildCommonCodeFixture(11, "DEPARTMENT", "D200");
        CommonCode programTypeCode = buildCommonCodeFixture(22, "PROGRAM_TYPE", "PT100");
        Competency competency = buildCompetencyFixture(33, "리더십");

        ExtracurricularProgram program = buildProgramFixture(
                1, managerUser, Instant.now().plusSeconds(3600), ProgramStatus.DRAFT);
        ReflectionTestUtils.setField(program, "programName", "프로그램명");
        ReflectionTestUtils.setField(program, "capacity", 10);
        ReflectionTestUtils.setField(program, "operatingUnitCode", operatingUnitCode);
        ReflectionTestUtils.setField(program, "programTypeCode", programTypeCode);
        ReflectionTestUtils.setField(program, "competency", competency);

        when(programRepository.findDetailById(1)).thenReturn(Optional.of(program));
        when(programSessionRepository.findByProgram_ProgramIdOrderBySessionNoAsc(1))
                .thenReturn(List.of());
        when(applicationRepository.countByProgram_ProgramIdAndApplicationStatusIn(eq(1), any()))
                .thenReturn(0L);
        when(applicationRepository.findByProgram_ProgramIdAndStudent_UserId(1, 100))
                .thenReturn(Optional.of(buildApplicationFixture(5, "CANCELLED")));

        ProgramDetailResponseDTO response = programService.getDetail(1, 100);

        assertThat(response.myApplicationStatus()).isNull();
        assertThat(response.myApplicationStatusLabel()).isNull();
    }

    @Test
    void getDetail_whenProgramNotFound_throwsProgramNotFound() {
        when(programRepository.findDetailById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programService.getDetail(999, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    @Test
    void downloadOperationPlan_withFileGroup_returnsLoadedFile() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(
                1, mock(AppUser.class), Instant.now().plusSeconds(3600), ProgramStatus.DRAFT);

        FileGroup fileGroup = FileGroup.create();
        ReflectionTestUtils.setField(fileGroup, "fileGroupId", 501);
        ReflectionTestUtils.setField(program, "fileGroup", fileGroup);

        StoredFile storedFile = StoredFile.builder().originalFileName("운영계획서.pdf").build();
        ReflectionTestUtils.setField(storedFile, "storedFileId", 900);

        FileStorageService.LoadedFile loadedFile =
                new FileStorageService.LoadedFile(mock(org.springframework.core.io.Resource.class),
                        "운영계획서.pdf", "application/pdf");

        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(fileGroupService.getFiles(fileGroup)).thenReturn(List.of(storedFile));
        when(fileStorageService.load(900)).thenReturn(loadedFile);

        FileStorageService.LoadedFile result = programService.downloadOperationPlan(1);

        assertThat(result).isSameAs(loadedFile);
    }

    @Test
    void downloadOperationPlan_whenProgramNotFound_throwsProgramNotFound() {
        when(programRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> programService.downloadOperationPlan(999))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    @Test
    void downloadOperationPlan_whenNoFileGroup_throwsResourceNotFound() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(
                1, mock(AppUser.class), Instant.now().plusSeconds(3600), ProgramStatus.DRAFT);

        when(programRepository.findById(1)).thenReturn(Optional.of(program));

        assertThatThrownBy(() -> programService.downloadOperationPlan(1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void downloadOperationPlan_whenNoStoredFile_throwsResourceNotFound() throws Exception {
        ExtracurricularProgram program = buildProgramFixture(
                1, mock(AppUser.class), Instant.now().plusSeconds(3600), ProgramStatus.DRAFT);

        FileGroup fileGroup = FileGroup.create();
        ReflectionTestUtils.setField(fileGroup, "fileGroupId", 501);
        ReflectionTestUtils.setField(program, "fileGroup", fileGroup);

        when(programRepository.findById(1)).thenReturn(Optional.of(program));
        when(fileGroupService.getFiles(fileGroup)).thenReturn(List.of());

        assertThatThrownBy(() -> programService.downloadOperationPlan(1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * ExtracurricularProgram은 protected 기본 생성자만 있고 setter/빌더가 없어(네이티브 SQL로만 값을 채우는 구조),
     * 테스트 픽스처는 리플렉션으로 생성한 뒤 필요한 필드만 직접 채워 넣는다.
     */
    private ExtracurricularProgram buildProgramFixture(
            Integer programId, AppUser managerUser, Instant recruitmentEndsAt, ProgramStatus status) throws Exception {
        Constructor<ExtracurricularProgram> constructor = ExtracurricularProgram.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ExtracurricularProgram program = constructor.newInstance();
        ReflectionTestUtils.setField(program, "programId", programId);
        ReflectionTestUtils.setField(program, "managerUser", managerUser);
        ReflectionTestUtils.setField(program, "recruitmentEndsAt", recruitmentEndsAt);
        ReflectionTestUtils.setField(program, "programStatus", status);
        return program;
    }

    /**
     * CommonCode도 ExtracurricularProgram과 동일하게 protected 기본 생성자만 있고 setter가 없어,
     * 위 buildProgramFixture와 같은 방식으로 리플렉션을 통해 픽스처를 만든다.
     */
    private CommonCode buildCommonCodeFixture(Integer codeId, String codeGroup, String code) throws Exception {
        Constructor<CommonCode> constructor = CommonCode.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CommonCode commonCode = constructor.newInstance();
        ReflectionTestUtils.setField(commonCode, "codeId", codeId);
        ReflectionTestUtils.setField(commonCode, "codeGroup", codeGroup);
        ReflectionTestUtils.setField(commonCode, "code", code);
        return commonCode;
    }

    // Competency도 위 픽스처들과 동일한 이유(protected 기본 생성자, setter 없음)로 리플렉션을 사용한다.
    private Competency buildCompetencyFixture(Integer competencyId, String competencyName) throws Exception {
        Constructor<Competency> constructor = Competency.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Competency competency = constructor.newInstance();
        ReflectionTestUtils.setField(competency, "competencyId", competencyId);
        ReflectionTestUtils.setField(competency, "competencyName", competencyName);
        return competency;
    }

    // ProgramApplicantCount는 인터페이스 projection이라 목(mock)으로 값을 채운다.
    private ProgramApplicationRepository.ProgramApplicantCount buildApplicantCountFixture(
            Integer programId, Long count) {
        ProgramApplicationRepository.ProgramApplicantCount fixture =
                mock(ProgramApplicationRepository.ProgramApplicantCount.class);
        when(fixture.getProgramId()).thenReturn(programId);
        when(fixture.getCount()).thenReturn(count);
        return fixture;
    }

    // ProgramApplication도 protected 기본 생성자만 있고 setter/빌더가 없어(위 픽스처들과 같은 이유)
    // 리플렉션으로 만든다. getDetail의 myApplicationStatus 필터링 테스트에는 상태값만 있으면 충분하다.
    private ProgramApplication buildApplicationFixture(Integer applicationId, String applicationStatus) throws Exception {
        Constructor<ProgramApplication> constructor = ProgramApplication.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ProgramApplication application = constructor.newInstance();
        ReflectionTestUtils.setField(application, "applicationId", applicationId);
        ReflectionTestUtils.setField(application, "applicationStatus", applicationStatus);
        return application;
    }

    // MyApplicationStatusProjection도 인터페이스 projection이라 목(mock)으로 값을 채운다.
    private ProgramApplicationRepository.MyApplicationStatusProjection buildMyApplicationStatusFixture(
            Integer programId, String status) {
        ProgramApplicationRepository.MyApplicationStatusProjection fixture =
                mock(ProgramApplicationRepository.MyApplicationStatusProjection.class);
        when(fixture.getProgramId()).thenReturn(programId);
        when(fixture.getStatus()).thenReturn(status);
        return fixture;
    }
}
