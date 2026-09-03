package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResultResponse;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResultResponse.CompetencyResult;
import com.gnagnoohc.scms.domain.competency.dto.response.RecommendedProgramsResponse;
import com.gnagnoohc.scms.domain.competency.support.WeakCompetencySelector;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramStatus;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository.MyApplicationStatusProjection;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository.ProgramApplicantCount;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentRecommendationServiceTest {

    private static final Integer STUDENT_ID = 1;
    private static final Integer ATTEMPT_ID = 10;

    @Mock
    AssessmentResultService assessmentResultService;

    @Spy
    WeakCompetencySelector weakCompetencySelector = new WeakCompetencySelector();

    @Mock
    ExtracurricularProgramRepository programRepository;

    @Mock
    ProgramApplicationRepository programApplicationRepository;

    @InjectMocks
    AssessmentRecommendationService assessmentRecommendationService;

    private static <T> T newInstance(Class<T> type) throws ReflectiveOperationException {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static CompetencyResult score(int competencyId, int displayOrder, double converted) {
        return new CompetencyResult(competencyId, "역량" + competencyId, displayOrder,
                BigDecimal.valueOf(converted), null);
    }

    private static AssessmentResultResponse resultOf(List<CompetencyResult> scores) {
        return new AssessmentResultResponse(ATTEMPT_ID, 100, Instant.now(), BigDecimal.valueOf(70), true, scores);
    }

    private static Page<ExtracurricularProgram> pageOf(ExtracurricularProgram... programs) {
        return new PageImpl<>(List.of(programs));
    }

    // programRepository.search(DRAFT, null, competencyId, any) 스텁을 건다. 서비스가 취약 역량마다 이 형태로 호출한다.
    private void stubRecruitingPrograms(int competencyId, ExtracurricularProgram... programs) {
        when(programRepository.search(eq(ProgramStatus.DRAFT), isNull(), eq(competencyId), any(Pageable.class)))
                .thenReturn(pageOf(programs));
    }

    private static CommonCode commonCode(String name) throws ReflectiveOperationException {
        CommonCode code = newInstance(CommonCode.class);
        ReflectionTestUtils.setField(code, "codeName", name);
        return code;
    }

    private static Competency competency(int competencyId, int displayOrder) {
        Competency competency = Competency.createTop("C" + competencyId, "역량" + competencyId,
                null, null, displayOrder, 1);
        ReflectionTestUtils.setField(competency, "competencyId", competencyId);
        return competency;
    }

    private static ExtracurricularProgram program(int programId, Competency competency, int capacity)
            throws ReflectiveOperationException {
        ExtracurricularProgram program = newInstance(ExtracurricularProgram.class);
        ReflectionTestUtils.setField(program, "programId", programId);
        ReflectionTestUtils.setField(program, "programName", "프로그램" + programId);
        ReflectionTestUtils.setField(program, "competency", competency);
        ReflectionTestUtils.setField(program, "operatingUnitCode", commonCode("운영단위"));
        ReflectionTestUtils.setField(program, "programTypeCode", commonCode("유형"));
        ReflectionTestUtils.setField(program, "capacity", capacity);
        Instant now = Instant.now();
        ReflectionTestUtils.setField(program, "recruitmentStartsAt", now);
        ReflectionTestUtils.setField(program, "recruitmentEndsAt", now.plus(7, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(program, "operationStartsAt", now.plus(8, ChronoUnit.DAYS));
        ReflectionTestUtils.setField(program, "operationEndsAt", now.plus(20, ChronoUnit.DAYS));
        return program;
    }

    private static ProgramApplicantCount applicantCount(int programId, long count) {
        return new ProgramApplicantCount() {
            @Override public Integer getProgramId() { return programId; }
            @Override public Long getCount() { return count; }
        };
    }

    private static MyApplicationStatusProjection myStatus(int programId, String status) {
        return new MyApplicationStatusProjection() {
            @Override public Integer getProgramId() { return programId; }
            @Override public String getStatus() { return status; }
        };
    }

    @Test
    void recommend_groupsRecruitingProgramsUnderTwoWeakestCompetencies() throws Exception {
        when(assessmentResultService.getResult(ATTEMPT_ID, STUDENT_ID)).thenReturn(resultOf(List.of(
                score(1, 1, 40.0), score(2, 2, 80.0), score(3, 3, 55.0))));

        Competency weak1 = competency(1, 1);
        Competency weak3 = competency(3, 3);
        stubRecruitingPrograms(1, program(101, weak1, 10), program(102, weak1, 5));
        stubRecruitingPrograms(3, program(201, weak3, 8));
        when(programApplicationRepository.countActiveApplicantsByProgramIds(anyList()))
                .thenReturn(List.of(applicantCount(101, 2L)));
        when(programApplicationRepository.findMyApplicationStatusesByProgramIds(eq(STUDENT_ID), anyList()))
                .thenReturn(List.of(myStatus(201, "APPLIED")));

        RecommendedProgramsResponse response =
                assessmentRecommendationService.recommend(ATTEMPT_ID, STUDENT_ID);

        assertThat(response.attemptId()).isEqualTo(ATTEMPT_ID);
        // 더 취약한 역량(환산점수 40)이 앞, 그 다음 55.
        assertThat(response.weakCompetencies()).extracting(g -> g.competencyId()).containsExactly(1, 3);

        RecommendedProgramsResponse.WeakCompetencyGroup first = response.weakCompetencies().get(0);
        assertThat(first.convertedScore()).isEqualByComparingTo(BigDecimal.valueOf(40.0));
        // 역량당 후보가 상한(3) 이하면 무작위 추출 없이 순서 그대로 전부 내려간다.
        assertThat(first.programs()).extracting(p -> p.programId()).containsExactly(101, 102);
        assertThat(first.programs().get(0).applicantCount()).isEqualTo(2L);
        assertThat(first.programs().get(0).remainingCapacity()).isEqualTo(8);
        assertThat(first.programs().get(0).myApplicationStatus()).isNull();

        RecommendedProgramsResponse.WeakCompetencyGroup second = response.weakCompetencies().get(1);
        assertThat(second.programs()).extracting(p -> p.programId()).containsExactly(201);
        assertThat(second.programs().get(0).myApplicationStatus()).isEqualTo("APPLIED");
        assertThat(second.programs().get(0).myApplicationStatusLabel()).isEqualTo("신청완료");
        assertThat(second.programs().get(0).remainingCapacity()).isEqualTo(8);
    }

    @Test
    void recommend_whenMoreCandidatesThanLimit_picksThreeAtRandomFromThatCompetency() throws Exception {
        // 어떤 3개인지는 무작위라 고정하지 않고 "후보 중 서로 다른 3개"만 검증한다.
        when(assessmentResultService.getResult(ATTEMPT_ID, STUDENT_ID))
                .thenReturn(resultOf(List.of(score(1, 1, 30.0))));

        Competency weak1 = competency(1, 1);
        List<Integer> candidateIds = IntStream.rangeClosed(1, 7).map(i -> 300 + i).boxed().toList();
        ExtracurricularProgram[] sevenPrograms = candidateIds.stream()
                .map(id -> {
                    try {
                        return program(id, weak1, 10);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toArray(ExtracurricularProgram[]::new);
        stubRecruitingPrograms(1, sevenPrograms);
        when(programApplicationRepository.countActiveApplicantsByProgramIds(anyList())).thenReturn(List.of());
        when(programApplicationRepository.findMyApplicationStatusesByProgramIds(eq(STUDENT_ID), anyList()))
                .thenReturn(List.of());

        List<Integer> picked = assessmentRecommendationService.recommend(ATTEMPT_ID, STUDENT_ID)
                .weakCompetencies().get(0).programs().stream()
                .map(p -> p.programId())
                .toList();

        assertThat(picked).hasSize(3).doesNotHaveDuplicates().isSubsetOf(candidateIds);
    }

    @Test
    void recommend_whenCandidatesEqualLimit_returnsAllWithoutShuffling() throws Exception {
        when(assessmentResultService.getResult(ATTEMPT_ID, STUDENT_ID))
                .thenReturn(resultOf(List.of(score(1, 1, 30.0))));

        Competency weak1 = competency(1, 1);
        stubRecruitingPrograms(1, program(401, weak1, 10), program(402, weak1, 10), program(403, weak1, 10));
        when(programApplicationRepository.countActiveApplicantsByProgramIds(anyList())).thenReturn(List.of());
        when(programApplicationRepository.findMyApplicationStatusesByProgramIds(eq(STUDENT_ID), anyList()))
                .thenReturn(List.of());

        RecommendedProgramsResponse response =
                assessmentRecommendationService.recommend(ATTEMPT_ID, STUDENT_ID);

        assertThat(response.weakCompetencies().get(0).programs())
                .extracting(p -> p.programId())
                .containsExactly(401, 402, 403);
    }

    @Test
    void recommend_clampsRemainingCapacityToZeroWhenApplicantsExceedCapacity() throws Exception {
        when(assessmentResultService.getResult(ATTEMPT_ID, STUDENT_ID))
                .thenReturn(resultOf(List.of(score(1, 1, 30.0))));

        Competency weak1 = competency(1, 1);
        stubRecruitingPrograms(1, program(501, weak1, 5));   // 정원 5
        when(programApplicationRepository.countActiveApplicantsByProgramIds(anyList()))
                .thenReturn(List.of(applicantCount(501, 8L)));   // 신청 8 > 정원 5
        when(programApplicationRepository.findMyApplicationStatusesByProgramIds(eq(STUDENT_ID), anyList()))
                .thenReturn(List.of());

        RecommendedProgramsResponse.RecommendedProgram program =
                assessmentRecommendationService.recommend(ATTEMPT_ID, STUDENT_ID)
                        .weakCompetencies().get(0).programs().get(0);

        assertThat(program.applicantCount()).isEqualTo(8L);
        assertThat(program.remainingCapacity()).isZero();
    }

    @Test
    void recommend_mapsNullMileagePolicyToNullPoints() throws Exception {
        when(assessmentResultService.getResult(ATTEMPT_ID, STUDENT_ID))
                .thenReturn(resultOf(List.of(score(1, 1, 30.0))));

        Competency weak1 = competency(1, 1);
        stubRecruitingPrograms(1, program(502, weak1, 10));   // program() 헬퍼는 mileagePolicy를 안 넣으므로 null
        when(programApplicationRepository.countActiveApplicantsByProgramIds(anyList())).thenReturn(List.of());
        when(programApplicationRepository.findMyApplicationStatusesByProgramIds(eq(STUDENT_ID), anyList()))
                .thenReturn(List.of());

        RecommendedProgramsResponse.RecommendedProgram program =
                assessmentRecommendationService.recommend(ATTEMPT_ID, STUDENT_ID)
                        .weakCompetencies().get(0).programs().get(0);

        assertThat(program.mileagePoints()).isNull();
    }

    @Test
    void recommend_mapsWaitlistedAndRejectedStatusLabels() throws Exception {
        when(assessmentResultService.getResult(ATTEMPT_ID, STUDENT_ID))
                .thenReturn(resultOf(List.of(score(1, 1, 30.0))));

        Competency weak1 = competency(1, 1);
        stubRecruitingPrograms(1, program(601, weak1, 10), program(602, weak1, 10));
        when(programApplicationRepository.countActiveApplicantsByProgramIds(anyList())).thenReturn(List.of());
        when(programApplicationRepository.findMyApplicationStatusesByProgramIds(eq(STUDENT_ID), anyList()))
                .thenReturn(List.of(myStatus(601, "WAITLISTED"), myStatus(602, "REJECTED")));

        List<RecommendedProgramsResponse.RecommendedProgram> programs =
                assessmentRecommendationService.recommend(ATTEMPT_ID, STUDENT_ID)
                        .weakCompetencies().get(0).programs();

        assertThat(programs).filteredOn(p -> p.programId().equals(601)).singleElement().satisfies(p -> {
            assertThat(p.myApplicationStatus()).isEqualTo("WAITLISTED");
            assertThat(p.myApplicationStatusLabel()).isEqualTo("대기");
        });
        assertThat(programs).filteredOn(p -> p.programId().equals(602)).singleElement().satisfies(p -> {
            assertThat(p.myApplicationStatus()).isEqualTo("REJECTED");
            assertThat(p.myApplicationStatusLabel()).isEqualTo("반려");
        });
    }

    @Test
    void recommend_whenNoWeakCompetenciesScored_returnsEmptyWithoutQueryingPrograms() {
        when(assessmentResultService.getResult(ATTEMPT_ID, STUDENT_ID))
                .thenReturn(resultOf(List.of(
                        new CompetencyResult(1, "역량1", 1, null, null))));

        RecommendedProgramsResponse response =
                assessmentRecommendationService.recommend(ATTEMPT_ID, STUDENT_ID);

        assertThat(response.attemptId()).isEqualTo(ATTEMPT_ID);
        assertThat(response.weakCompetencies()).isEmpty();
        verify(programRepository, never()).search(any(), any(), any(), any());
    }

    @Test
    void recommend_whenNoRecruitingPrograms_returnsGroupsWithEmptyProgramLists() {
        when(assessmentResultService.getResult(ATTEMPT_ID, STUDENT_ID)).thenReturn(resultOf(List.of(
                score(1, 1, 40.0), score(2, 2, 55.0))));
        stubRecruitingPrograms(1);
        stubRecruitingPrograms(2);

        RecommendedProgramsResponse response =
                assessmentRecommendationService.recommend(ATTEMPT_ID, STUDENT_ID);

        assertThat(response.weakCompetencies()).hasSize(2);
        assertThat(response.weakCompetencies()).allSatisfy(group -> assertThat(group.programs()).isEmpty());
        // 후보가 없으면 신청자 수/내 신청상태 배치 조회도 하지 않는다.
        verify(programApplicationRepository, never()).countActiveApplicantsByProgramIds(anyList());
    }

    @Test
    void recommend_propagatesResultNotAvailableFromResultLookup() {
        when(assessmentResultService.getResult(ATTEMPT_ID, STUDENT_ID))
                .thenThrow(new BusinessException(ErrorCode.RESULT_NOT_AVAILABLE));

        assertThatThrownBy(() -> assessmentRecommendationService.recommend(ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESULT_NOT_AVAILABLE);
        verify(programRepository, never()).search(any(), any(), any(), any());
    }
}
