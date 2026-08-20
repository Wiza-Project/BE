package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.domain.program.dto.ProgramRegisterRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.ProgramRegisterResponseDTO;
import com.gnagnoohc.scms.domain.program.dto.ProgramUpdateRequestDTO;
import com.gnagnoohc.scms.domain.program.dto.ProgramUpdateResponseDTO;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramStatus;
import com.gnagnoohc.scms.domain.program.repository.CompetencyOptionRepository;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import com.gnagnoohc.scms.domain.program.dto.ProgramListItemResponseDTO;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramServiceTest {

    @Mock
    ExtracurricularProgramRepository programRepository;

    @Mock
    CompetencyOptionRepository competencyOptionRepository;

    @Mock
    CommonCodeRepository commonCodeRepository;

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
    void update_whenRecruitmentPeriodOngoing_succeeds_andResponseStatusLabelReflectsEntity() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserId()).thenReturn(100);

        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, managerUser, now.plusSeconds(3600), ProgramStatus.DRAFT);

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
                null, 1, 2, 3, null,
                "수정된 프로그램명", "설명",
                recruitmentStartsAt, recruitmentEndsAt, operationStartsAt, operationEndsAt,
                20, null
        );

        ProgramUpdateResponseDTO response = programService.update(1, request, 100);

        assertThat(response.programStatus()).isEqualTo("모집중");
    }

    @Test
    void update_whenRecruitmentEnded_throwsProgramNotEditable() throws Exception {
        AppUser managerUser = mock(AppUser.class);
        when(managerUser.getUserId()).thenReturn(100);

        Instant now = Instant.now();
        ExtracurricularProgram program = buildProgramFixture(
                1, managerUser, now.minusSeconds(3600), ProgramStatus.DRAFT);

        when(programRepository.findById(1)).thenReturn(Optional.of(program));

        ProgramUpdateRequestDTO request = new ProgramUpdateRequestDTO(
                null, 1, 2, 3, null,
                "수정된 프로그램명", "설명",
                now.minusSeconds(7200), now.minusSeconds(3600),
                now.minusSeconds(3600), now.minusSeconds(1800),
                20, null
        );

        assertThatThrownBy(() -> programService.update(1, request, 100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_EDITABLE);
    }

    @Test
    void list_mapsSearchResultPageToPageResponseOfListItemDto() throws Exception {
        CommonCode operatingUnitCode = buildCommonCodeFixture(11, "DEPARTMENT", "D200");
        ReflectionTestUtils.setField(operatingUnitCode, "codeName", "비교과운영부서");
        CommonCode programTypeCode = buildCommonCodeFixture(22, "PROGRAM_TYPE", "PT100");
        ReflectionTestUtils.setField(programTypeCode, "codeName", "학습");

        ExtracurricularProgram program = buildProgramFixture(
                1, null, Instant.now().plusSeconds(3600), ProgramStatus.DRAFT);
        ReflectionTestUtils.setField(program, "programName", "프로그램명");
        ReflectionTestUtils.setField(program, "capacity", 10);
        ReflectionTestUtils.setField(program, "operatingUnitCode", operatingUnitCode);
        ReflectionTestUtils.setField(program, "programTypeCode", programTypeCode);

        Pageable pageable = PageRequest.of(0, 20);
        when(programRepository.search(ProgramStatus.DRAFT, "프로그램", pageable))
                .thenReturn(new PageImpl<>(List.of(program), pageable, 1));

        PageResponse<ProgramListItemResponseDTO> response =
                programService.list(ProgramStatus.DRAFT, "프로그램", pageable);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        ProgramListItemResponseDTO item = response.content().get(0);
        assertThat(item.programId()).isEqualTo(1);
        assertThat(item.programName()).isEqualTo("프로그램명");
        assertThat(item.operatingUnitCodeName()).isEqualTo("비교과운영부서");
        assertThat(item.programTypeCodeName()).isEqualTo("학습");
        assertThat(item.programStatus()).isEqualTo("DRAFT");
        assertThat(item.programStatusLabel()).isEqualTo("모집중");
    }

    // ExtracurricularProgram은 protected 기본 생성자만 있고 setter/빌더가 없어(네이티브 SQL로만 값을 채우는 구조),
    // 테스트 픽스처는 리플렉션으로 생성한 뒤 필요한 필드만 직접 채워 넣는다.
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

    // CommonCode도 ExtracurricularProgram과 동일하게 protected 기본 생성자만 있고 setter가 없어,
    // 위 buildProgramFixture와 같은 방식으로 리플렉션을 통해 픽스처를 만든다.
    private CommonCode buildCommonCodeFixture(Integer codeId, String codeGroup, String code) throws Exception {
        Constructor<CommonCode> constructor = CommonCode.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CommonCode commonCode = constructor.newInstance();
        ReflectionTestUtils.setField(commonCode, "codeId", codeId);
        ReflectionTestUtils.setField(commonCode, "codeGroup", codeGroup);
        ReflectionTestUtils.setField(commonCode, "code", code);
        return commonCode;
    }
}
