package com.gnagnoohc.scms.domain.career.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnagnoohc.scms.domain.career.dto.preference.JobPreferenceRequestDTO;
import com.gnagnoohc.scms.domain.career.entity.JobPreference;
import com.gnagnoohc.scms.domain.career.entity.NcsStandard;
import com.gnagnoohc.scms.domain.career.entity.StudentProfile;
import com.gnagnoohc.scms.domain.career.repository.JobPreferenceRepository;
import com.gnagnoohc.scms.domain.career.repository.NcsStandardRepository;
import com.gnagnoohc.scms.domain.career.repository.StudentProfileRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
//@TestPropertySource(properties = {
//        "spring.datasource.url=jdbc:postgresql://localhost:DBPORT/DBNAME",
//        "spring.datasource.username=USERNAME",
//        "spring.datasource.password=PW" // 실제 로컬 DB 비밀번호로 지정
//})
@Transactional
class JobPreferenceServiceTest {

    @Autowired
    private JobPreferenceService jobPreferenceService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private CommonCodeRepository commonCodeRepository;

    @Autowired
    private NcsStandardRepository ncsStandardRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private JobPreferenceRepository jobPreferenceRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("기적재된 NcsStandard의 실제 벡터가 StudentProfile에 정상 복사/동기화된다 (Jackson 역직렬화)")
    void upsertPreference_syncRealNcsVector_success() throws Exception {
        // 1. 테스트용 학생 계정 조회
        AppUser student = appUserRepository.findById(10)
                .orElseThrow(() -> new IllegalStateException("테스트용 학생(ID: 10)이 존재하지 않습니다."));

        // 2. common_code 원장에서 NCS 직무 공통코드 1건 조회 (PT100 같은 일반 공통코드 배제)
        CommonCode targetCommonCode = commonCodeRepository.findAll().stream()
                .filter(c -> "NCS_CODE".equals(c.getCodeGroup()) || c.getCode().startsWith("NCS") || (c.getCode().length() == 8 && c.getCode().matches("\\d+")))
                .findFirst()
                .orElseGet(() -> commonCodeRepository.findAll().stream()
                        .filter(c -> c.getCodeGroup() != null && c.getCodeGroup().contains("NCS"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("common_code 테이블에 NCS 직무 코드가 없습니다.")));

        // 3. 해당 코드의 NcsStandard가 없으면 기존 벡터를 가진 NcsStandard의 벡터를 임시로 셋팅
        NcsStandard realNcs = ncsStandardRepository.findByNcsCode(targetCommonCode.getCode())
                .orElseGet(() -> {
                    NcsStandard sample = ncsStandardRepository.findAll().stream()
                            .filter(ncs -> ncs.getEmbeddingVector() != null && ncs.getEmbeddingVector().length > 0)
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("ncs_standard 테이블에 적재된 임베딩 벡터가 없습니다."));

                    NcsStandard mapped = NcsStandard.of(targetCommonCode.getCode(), targetCommonCode.getCodeName(), "테스트 매핑 직무");
                    mapped.updateEmbeddingVector(sample.getEmbeddingVector());
                    return ncsStandardRepository.save(mapped);
                });

        // 4. Request DTO 바디 구성 (CommonCode PK 전달)
        String requestJson = String.format("""
                {
                  "ncsStandardId": %d,
                  "preferredRegionCodeId": null,
                  "preferredEmploymentType": "REGULAR",
                  "minimumSalary": 36000000.00
                }
                """, targetCommonCode.getCodeId());

        JobPreferenceRequestDTO requestDTO = objectMapper.readValue(requestJson, JobPreferenceRequestDTO.class);

        // 5. 실행
        jobPreferenceService.upsertPreference(student.getUserId(), requestDTO);

        // 6. 검증: JobPreference 정형 데이터 저장 확인
        JobPreference savedPreference = jobPreferenceRepository.findByStudent_UserId(student.getUserId())
                .orElseThrow(() -> new AssertionError("JobPreference가 저장되지 않았습니다."));
        assertThat(savedPreference.getPreferredEmploymentType()).isEqualTo("REGULAR");

        // 7. 검증: StudentProfile 벡터 동기화 확인
        StudentProfile studentProfile = studentProfileRepository.findById(student.getUserId())
                .orElseThrow(() -> new AssertionError("StudentProfile이 저장되지 않았습니다."));
        assertThat(studentProfile.getEmbeddingVector()).isNotNull();
        assertThat(studentProfile.getEmbeddingVector().length).isEqualTo(realNcs.getEmbeddingVector().length);

        System.out.println("====== [테스트 성공] StudentProfile 벡터 차원 수: " + studentProfile.getEmbeddingVector().length + " ======");
    }
}