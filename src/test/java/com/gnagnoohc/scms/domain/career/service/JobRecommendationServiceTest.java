//package com.gnagnoohc.scms.domain.career.service;
//
//import com.gnagnoohc.scms.domain.career.entity.CompanyAccount;
//import com.gnagnoohc.scms.domain.career.entity.JobPosting;
//import com.gnagnoohc.scms.domain.career.entity.NcsStandard;
//import com.gnagnoohc.scms.domain.career.entity.StudentProfile;
//import com.gnagnoohc.scms.domain.career.repository.CompanyAccountRepository;
//import com.gnagnoohc.scms.domain.career.repository.JobPostingRepository;
//import com.gnagnoohc.scms.domain.career.repository.NcsStandardRepository;
//import com.gnagnoohc.scms.domain.career.repository.StudentProfileRepository;
//import com.gnagnoohc.scms.domain.user.entity.AppUser;
//import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
//import com.gnagnoohc.scms.global.common.entity.CommonCode;
//import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.TestPropertySource;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.time.Instant;
//import java.time.temporal.ChronoUnit;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@SpringBootTest
//@ActiveProfiles("local")
////@TestPropertySource(properties = {
////        "spring.datasource.url=jdbc:postgresql://localhost:DBPORT/DBNAME",
////        "spring.datasource.username=USERNAME",
////        "spring.datasource.password=PW" // 실제 로컬 DB 비밀번호로 지정
////})
//@Transactional
//class JobPostingRecommendationTest {
//
//    @Autowired
//    private JobPostingRepository jobPostingRepository;
//
//    @Autowired
//    private StudentProfileRepository studentProfileRepository;
//
//    @Autowired
//    private NcsStandardRepository ncsStandardRepository;
//
//    @Autowired
//    private CommonCodeRepository commonCodeRepository;
//
//    @Autowired
//    private CompanyAccountRepository companyAccountRepository;
//
//    @Autowired
//    private AppUserRepository appUserRepository;
//
//    @Test
//    @DisplayName("더미 채용공고가 정상 등록되고, 학생 희망 직무와 일치하는 공고의 상태가 PUBLISHED로 전환된다")
//    void saveAndReviewJobPosting_matchingNcs_success() {
//        // 1. ncs_standard 원장에서 실제 벡터가 있는 데이터 1건 추출
//        NcsStandard realNcs = ncsStandardRepository.findAll().stream()
//                .filter(ncs -> ncs.getEmbeddingVector() != null && ncs.getEmbeddingVector().length > 0)
//                .findFirst()
//                .orElseThrow(() -> new IllegalStateException("ncs_standard 테이블에 적재된 임베딩 벡터가 없습니다."));
//
//        // 2. 해당 NCS 직무의 CommonCode 확보
//        CommonCode targetNcsCommonCode = commonCodeRepository.findAll().stream()
//                .filter(c -> realNcs.getNcsCode().equals(c.getCode()))
//                .findFirst()
//                .orElseGet(() -> commonCodeRepository.findAll().stream()
//                        .filter(c -> "NCS_CODE".equals(c.getCodeGroup()) || c.getCode().startsWith("NC"))
//                        .findFirst()
//                        .orElseThrow(() -> new IllegalStateException("common_code 테이블에 NCS 직무 코드가 없습니다.")));
//
//        // 3. 테스트용 학생(ID: 10) 및 기업 계정 조회 (없으면 즉석 생성 주입)
//        AppUser student = appUserRepository.findById(10)
//                .orElseThrow(() -> new IllegalStateException("테스트 학생(10)이 존재하지 않습니다."));
//
//        CompanyAccount company = companyAccountRepository.findAll().stream()
//                .findFirst()
//                .orElseGet(() -> companyAccountRepository.save(
//                        CompanyAccount.builder()
//                                .loginId("test_company")
//                                .passwordHash("$2a$10$dummyHashValueForTest1234567890")
//                                .companyName("(주)테스트소프트")
//                                .representativeName("대표자")
//                                .businessRegistrationNo("123-45-67890")
//                                .contactName("채용담당자")
//                                .contactEmail("recruit@test.com")
//                                .contactPhone("010-1234-5678")
//                                .address("서울특별시 강남구 테헤란로 123")
//                                .verificationStatus("VERIFIED")
//                                .accountStatus("ACTIVE")
//                                .build()
//                ));
//
//        // 4. 학생 프로필에 해당 NCS 벡터 동기화
//        StudentProfile profile = studentProfileRepository.findById(student.getUserId())
//                .orElseGet(() -> StudentProfile.builder()
//                        .user(student)
//                        .studentGrade("4")
//                        .embeddingVector(realNcs.getEmbeddingVector())
//                        .build());
//        profile.updateEmbeddingVector(realNcs.getEmbeddingVector());
//        studentProfileRepository.save(profile);
//
//        // 5. 실제 JobPosting 엔티티 스펙에 맞춘 더미 공고 생성 (DRAFT 상태)
//        JobPosting dummyPosting = JobPosting.builder()
//                .companyAccount(company)
//                .ncsCode(targetNcsCommonCode)
//                .regionCode(null)
//                .postingTitle("AI 추천 맞춤 백엔드 개발자 채용")
//                .jobDescription("Spring Boot 및 AI 기반 매칭 시스템 개발")
//                .recruitmentCount(3)
//                .employmentType("REGULAR")
//                .salaryText("연봉 4,000만원 이상")
//                .qualificationData(null)
//                .applicationStartsAt(Instant.now())
//                .applicationEndsAt(Instant.now().plus(30, ChronoUnit.DAYS))
//                .postingType("GENERAL")
//                .benefitType("FLEXIBLE_TIME")
//                .build();
//
//        JobPosting savedPosting = jobPostingRepository.save(dummyPosting);
//        assertThat(savedPosting.getJobPostingId()).isNotNull();
//        assertThat(savedPosting.getPostingStatus()).isEqualTo("DRAFT");
//
//        // 6. 교직원 검수 승인 실행 (APPROVED -> PUBLISHED)
//        savedPosting.review("APPROVED", null, 1);
//        jobPostingRepository.flush();
//
//        // 7. 공고 상태 및 학생 프로필 벡터 정합성 최종 검증
//        JobPosting publishedPosting = jobPostingRepository.findById(savedPosting.getJobPostingId())
//                .orElseThrow(() -> new AssertionError("채용공고가 존재하지 않습니다."));
//
//        assertThat(publishedPosting.getPostingStatus()).isEqualTo("PUBLISHED");
//        assertThat(publishedPosting.getNcsCode().getCodeId()).isEqualTo(targetNcsCommonCode.getCodeId());
//        assertThat(profile.getEmbeddingVector()).isNotNull();
//        assertThat(profile.getEmbeddingVector().length).isEqualTo(realNcs.getEmbeddingVector().length);
//
//        System.out.println("====== [검증 성공] 등록된 공고 ID: " + publishedPosting.getJobPostingId()
//                + ", 게시 상태: " + publishedPosting.getPostingStatus()
//                + ", 연결된 NCS 직무: " + publishedPosting.getNcsCode().getCodeName() + " ======");
//    }
//}