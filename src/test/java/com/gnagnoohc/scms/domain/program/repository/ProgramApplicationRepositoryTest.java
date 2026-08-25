package com.gnagnoohc.scms.domain.program.repository;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * findAllByProgramIdAndStatus의 keyword 검색이 LIKE 와일드카드 문자(%, _)를 실제 문자로 다루는지 검증한다.
 * ProgramApplicationService.escapeLikeKeyword가 만들어주는 것과 같은 형태(!로 이스케이프된) 키워드를
 * 리포지토리에 직접 넘겨, 그 문자가 임의 문자열/한 글자에 대응하는 와일드카드로 오동작하지 않는지 확인한다.
 */
@SpringBootTest
class ProgramApplicationRepositoryTest {

    @Autowired
    private ProgramApplicationRepository applicationRepository;

    @Autowired
    private ExtracurricularProgramRepository programRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CommonCodeRepository commonCodeRepository;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Test
    void findAllByProgramIdAndStatus_whenKeywordHasEscapedPercent_matchesOnlyLiteralValue() throws Exception {
        ExtracurricularProgram program = saveProgram();
        ProgramApplication target = saveApplication(program, saveStudent("100%유저", "2021%%0001"));
        saveApplication(program, saveStudent("일반유저", "20210002"));

        // "100%" 자체를 찾고 싶다는 뜻으로 서비스가 넘겨주는 이스케이프된 키워드("100!%")를 그대로 전달한다.
        Page<ProgramApplication> result = applicationRepository.findAllByProgramIdAndStatus(
                program.getProgramId(), null, "100!%", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getApplicationId()).isEqualTo(target.getApplicationId());
    }

    /**
     * status/keyword가 둘 다 null인, 참여관리 화면을 필터 없이 처음 열 때의 경로. 실제 Postgres에서는
     * CONCAT('%', ?, '%')의 ? 타입을 추론 못해 "function lower(bytea) does not exist"로 500이 났었다
     * (CAST(:keyword AS string)로 수정). H2는 이 타입 추론 문제를 재현하지 않아 이 테스트만으로는 그
     * 회귀를 못 잡지만, 최소한 null/null 조합에서 로직 자체(전체 조회)가 깨지지 않는지는 검증한다.
     */
    @Test
    void findAllByProgramIdAndStatus_whenStatusAndKeywordBothNull_returnsAll() throws Exception {
        ExtracurricularProgram program = saveProgram();
        ProgramApplication first = saveApplication(program, saveStudent("전체조회학생1", "ALL-STU-0001"));
        ProgramApplication second = saveApplication(program, saveStudent("전체조회학생2", "ALL-STU-0002"));

        Page<ProgramApplication> result = applicationRepository.findAllByProgramIdAndStatus(
                program.getProgramId(), null, null, PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(ProgramApplication::getApplicationId)
                .containsExactlyInAnyOrder(first.getApplicationId(), second.getApplicationId());
    }

    @Test
    void findAllByProgramIdAndStatus_whenKeywordHasEscapedUnderscore_matchesOnlyLiteralValue() throws Exception {
        ExtracurricularProgram program = saveProgram();
        ProgramApplication target = saveApplication(program, saveStudent("김철수", "2021_0001"));
        saveApplication(program, saveStudent("김철숙", "20210099"));

        // "2021_0001" 자체를 찾고 싶다는 뜻으로 서비스가 넘겨주는 이스케이프된 키워드("2021!_0001")를 그대로 전달한다.
        Page<ProgramApplication> result = applicationRepository.findAllByProgramIdAndStatus(
                program.getProgramId(), null, "2021!_0001", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getApplicationId()).isEqualTo(target.getApplicationId());
    }

    /**
     * ProgramService.list가 목록 카드의 myApplicationStatus를 채울 때 이 쿼리를 쓴다. CANCELLED는
     * apply()가 재신청(reviveApplication) 대상으로 취급하는 "신청 안 한 것"과 동등한 상태라, 결과에서
     * 제외되어야 FE가 재신청 가능한 프로그램에서도 신청 버튼을 정상적으로 보여줄 수 있다.
     */
    @Test
    void findMyApplicationStatusesByProgramIds_excludesCancelledApplication() throws Exception {
        ExtracurricularProgram program = saveProgram();
        AppUser student = saveStudent("취소학생", "CANCEL-STU-0001");
        saveApplication(program, student, "CANCELLED");

        List<ProgramApplicationRepository.MyApplicationStatusProjection> result =
                applicationRepository.findMyApplicationStatusesByProgramIds(
                        student.getUserId(), List.of(program.getProgramId()));

        assertThat(result).isEmpty();
    }

    @Test
    void findMyApplicationStatusesByProgramIds_includesActiveApplication() throws Exception {
        ExtracurricularProgram program = saveProgram();
        AppUser student = saveStudent("신청학생", "ACTIVE-STU-0001");
        saveApplication(program, student, "APPLIED");

        List<ProgramApplicationRepository.MyApplicationStatusProjection> result =
                applicationRepository.findMyApplicationStatusesByProgramIds(
                        student.getUserId(), List.of(program.getProgramId()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("APPLIED");
    }

    private ExtracurricularProgram saveProgram() throws Exception {
        // System.nanoTime()은 플랫폼마다 기준점이 달라(리눅스 CI 컨테이너는 부팅 후 경과시간이라 자릿수가 훨씬 짧을 수 있음)
        // 자릿수를 가정한 substring이 깨질 수 있으므로, 길이가 고정된 UUID 기반 접미사를 쓴다.
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        CommonCode operatingUnitCode = saveCommonCode("DEPARTMENT", "D200-T-" + suffix);
        CommonCode programTypeCode = saveCommonCode("PROGRAM_TYPE", "PT100-T-" + suffix);
        Competency competency = competencyRepository.save(Competency.createTop(
                "CP-" + suffix, "테스트역량", "Test Competency", null, 1, 1));
        AppUser manager = saveStudent("담당자", "MGR-" + suffix);

        Constructor<ExtracurricularProgram> constructor = ExtracurricularProgram.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ExtracurricularProgram program = constructor.newInstance();
        Instant now = Instant.now();
        ReflectionTestUtils.setField(program, "operatingUnitCode", operatingUnitCode);
        ReflectionTestUtils.setField(program, "programTypeCode", programTypeCode);
        ReflectionTestUtils.setField(program, "competency", competency);
        ReflectionTestUtils.setField(program, "managerUser", manager);
        ReflectionTestUtils.setField(program, "programName", "검색 테스트 프로그램");
        ReflectionTestUtils.setField(program, "recruitmentStartsAt", now);
        ReflectionTestUtils.setField(program, "recruitmentEndsAt", now.plusSeconds(3600));
        ReflectionTestUtils.setField(program, "operationStartsAt", now.plusSeconds(3600));
        ReflectionTestUtils.setField(program, "operationEndsAt", now.plusSeconds(7200));
        ReflectionTestUtils.setField(program, "capacity", 10);
        return programRepository.save(program);
    }

    private AppUser saveStudent(String userName, String universityNo) throws Exception {
        Constructor<AppUser> constructor = AppUser.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        AppUser student = constructor.newInstance();
        ReflectionTestUtils.setField(student, "userName", userName);
        ReflectionTestUtils.setField(student, "universityNo", universityNo);
        ReflectionTestUtils.setField(student, "userType", "STUDENT");
        ReflectionTestUtils.setField(student, "passwordHash", "test-hash");
        return appUserRepository.save(student);
    }

    private ProgramApplication saveApplication(ExtracurricularProgram program, AppUser student) throws Exception {
        return saveApplication(program, student, "APPLIED");
    }

    private ProgramApplication saveApplication(
            ExtracurricularProgram program, AppUser student, String applicationStatus) throws Exception {
        Constructor<ProgramApplication> constructor = ProgramApplication.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ProgramApplication application = constructor.newInstance();
        ReflectionTestUtils.setField(application, "program", program);
        ReflectionTestUtils.setField(application, "student", student);
        ReflectionTestUtils.setField(application, "applicationStatus", applicationStatus);
        return applicationRepository.save(application);
    }

    private CommonCode saveCommonCode(String codeGroup, String code) throws Exception {
        Constructor<CommonCode> constructor = CommonCode.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CommonCode commonCode = constructor.newInstance();
        ReflectionTestUtils.setField(commonCode, "codeGroup", codeGroup);
        ReflectionTestUtils.setField(commonCode, "code", code);
        ReflectionTestUtils.setField(commonCode, "codeName", code);
        ReflectionTestUtils.setField(commonCode, "createdBy", 1);
        return commonCodeRepository.save(commonCode);
    }
}
