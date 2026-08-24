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

    private ExtracurricularProgram saveProgram() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        CommonCode operatingUnitCode = saveCommonCode("DEPARTMENT", "D200-TEST-" + suffix);
        CommonCode programTypeCode = saveCommonCode("PROGRAM_TYPE", "PT100-TEST-" + suffix);
        Competency competency = competencyRepository.save(Competency.createTop(
                "CP-" + suffix.substring(suffix.length() - 15), "테스트역량", "Test Competency", null, 1, 1));
        AppUser manager = saveStudent("담당자", "MANAGER-" + System.nanoTime());

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
        Constructor<ProgramApplication> constructor = ProgramApplication.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ProgramApplication application = constructor.newInstance();
        ReflectionTestUtils.setField(application, "program", program);
        ReflectionTestUtils.setField(application, "student", student);
        ReflectionTestUtils.setField(application, "applicationStatus", "APPLIED");
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
