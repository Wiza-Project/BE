package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentResultResponse;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentResultResponse.CompetencyResult;
import com.gnagnoohc.scms.domain.competency.dto.RecommendedProgramsResponse;
import com.gnagnoohc.scms.domain.competency.dto.RecommendedProgramsResponse.RecommendedProgram;
import com.gnagnoohc.scms.domain.competency.dto.RecommendedProgramsResponse.WeakCompetencyGroup;
import com.gnagnoohc.scms.domain.competency.support.WeakCompetencySelector;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramStatus;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository.MyApplicationStatusProjection;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository.ProgramApplicantCount;
import com.gnagnoohc.scms.domain.program.service.ApplicationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 학생 본인의 진단 결과에서 취약 역량을 골라, 그 역량에 연계된 모집중 비교과 프로그램을 추천한다.
 * 추천 알고리즘은 "취약 역량에 매핑된 프로그램 조회" 수준으로만 둔다(competency package-info.java 체크리스트).
 *
 * <p>비교과(program) 도메인은 읽기 전용으로만 참조한다 — 이미 공개된 조회 메서드(학생용 프로그램 목록
 * 조회 {@code search})를 그대로 호출할 뿐, program 도메인 코드에 손대지 않는다. 반환값이 필요한 동기
 * 조회라 이벤트 방식이 맞지 않고, 다른 도메인의 조회 결과가 필요할 때 그 도메인 repository를 직접
 * 주입하는 관례를 따른다(MileageDashboardService가 CompetencyRepository를 주입하는 것과 같은 패턴).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssessmentRecommendationService {

    /**
     * 취약 역량 하나당 추천할 프로그램 건수. 기획서에 값이 없어 BE가 정했다(FE 공유 필요).
     * 그 역량에 연계된 모집중 프로그램이 이 수보다 많으면 그중 무작위로 이만큼만 고른다
     * (매 조회 요청마다 다시 뽑으므로 같은 응시라도 결과가 달라질 수 있다).
     */
    private static final int PROGRAMS_PER_COMPETENCY = 3;

    /**
     * 역량 하나에 대해 무작위 추출 대상으로 끌어올 후보의 상한. 한 핵심역량에 동시 모집 중인 비교과
     * 프로그램이 이보다 많을 일은 실무상 없으므로, 사실상 "그 역량의 모집중 프로그램 전부"를 뜻한다.
     */
    private static final int CANDIDATE_FETCH_LIMIT = 200;

    private final AssessmentResultService assessmentResultService;
    private final WeakCompetencySelector weakCompetencySelector;
    private final ExtracurricularProgramRepository programRepository;
    private final ProgramApplicationRepository programApplicationRepository;

    // 역량별 후보가 상한보다 많을 때 무작위로 골라내는 데만 쓴다. 시드 고정이 필요 없어(테스트 단언이
    // "후보 중 서로 다른 N개"라 시드와 무관) 주입하지 않고 그냥 둔다.
    private final Random random = new Random();

    public RecommendedProgramsResponse recommend(Integer attemptId, Integer studentId) {
        // 결과 조회 로직을 그대로 재사용한다 — 응시 소유권 검증(Q014)·미채점 차단(Q018)과
        // 역량별 환산점수 확보가 여기서 한 번에 처리된다.
        AssessmentResultResponse result = assessmentResultService.getResult(attemptId, studentId);

        List<CompetencyResult> weakCompetencies = weakCompetencySelector.select(result.scores());
        if (weakCompetencies.isEmpty()) {
            return RecommendedProgramsResponse.empty(attemptId);
        }

        // 취약 역량마다 "모집중(DRAFT) + 해당 역량 연계" 프로그램을 기존 학생용 목록 조회로 가져온다.
        Map<Integer, List<ExtracurricularProgram>> candidatesByCompetency = new LinkedHashMap<>();
        for (CompetencyResult weak : weakCompetencies) {
            candidatesByCompetency.put(weak.competencyId(), programRepository.search(
                    ProgramStatus.DRAFT, null, weak.competencyId(),
                    PageRequest.of(0, CANDIDATE_FETCH_LIMIT)).getContent());
        }

        List<ExtracurricularProgram> allCandidates = candidatesByCompetency.values().stream()
                .flatMap(List::stream)
                .toList();
        Map<Integer, Long> applicantCounts = countActiveApplicants(allCandidates);
        Map<Integer, String> myApplicationStatuses = findMyApplicationStatuses(allCandidates, studentId);

        List<WeakCompetencyGroup> groups = weakCompetencies.stream()
                .map(weak -> toGroup(weak, candidatesByCompetency.get(weak.competencyId()),
                        applicantCounts, myApplicationStatuses))
                .toList();

        return new RecommendedProgramsResponse(attemptId, groups);
    }

    private WeakCompetencyGroup toGroup(CompetencyResult weak, List<ExtracurricularProgram> programs,
                                        Map<Integer, Long> applicantCounts,
                                        Map<Integer, String> myApplicationStatuses) {
        List<RecommendedProgram> items = sample(programs, PROGRAMS_PER_COMPETENCY).stream()
                .map(program -> toProgram(program,
                        applicantCounts.getOrDefault(program.getProgramId(), 0L),
                        myApplicationStatuses.get(program.getProgramId())))
                .toList();
        return new WeakCompetencyGroup(weak.competencyId(), weak.competencyName(),
                weak.displayOrder(), weak.convertedScore(), items);
    }

    // 후보가 max 이하면 그대로(순서 유지), 많으면 무작위로 섞어 앞 max개만 남긴다.
    private List<ExtracurricularProgram> sample(List<ExtracurricularProgram> programs, int max) {
        if (programs.size() <= max) {
            return programs;
        }
        List<ExtracurricularProgram> shuffled = new ArrayList<>(programs);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, max);
    }

    private RecommendedProgram toProgram(ExtracurricularProgram program, long applicantCount,
                                         String myApplicationStatus) {
        // 정원을 이미 넘겼어도(취소분 반영 지연 등) 음수 대신 0으로 보정한다(ProgramListItemResponseDTO와 동일).
        int remainingCapacity = Math.max(program.getCapacity() - (int) applicantCount, 0);
        return new RecommendedProgram(
                program.getProgramId(),
                program.getProgramName(),
                program.getOperatingUnitCode().getCodeName(),
                program.getProgramTypeCode().getCodeName(),
                program.getCapacity(),
                applicantCount,
                remainingCapacity,
                program.getRecruitmentStartsAt(),
                program.getRecruitmentEndsAt(),
                program.getOperationStartsAt(),
                program.getOperationEndsAt(),
                program.getMileagePolicy() != null ? program.getMileagePolicy().getPoints() : null,
                myApplicationStatus,
                myApplicationStatus != null ? ApplicationStatus.valueOf(myApplicationStatus).getLabel() : null);
    }

    // 추천 후보 프로그램들의 정원 점유(신청완료/승인) 인원을 한 번의 GROUP BY 쿼리로 집계한다(N+1 방지).
    private Map<Integer, Long> countActiveApplicants(List<ExtracurricularProgram> programs) {
        if (programs.isEmpty()) {
            return Map.of();
        }
        List<Integer> programIds = programs.stream().map(ExtracurricularProgram::getProgramId).toList();
        return programApplicationRepository.countActiveApplicantsByProgramIds(programIds).stream()
                .collect(Collectors.toMap(ProgramApplicantCount::getProgramId, ProgramApplicantCount::getCount));
    }

    // 추천 후보 프로그램들에 대한 로그인 학생 본인의 신청 상태를 한 번의 쿼리로 배치 조회한다(N+1 방지).
    // 취소(CANCELLED)는 "신청 안 한 것"과 동일하게 취급돼 결과에 안 담긴다(재신청 가능한데 버튼을 숨기면 안 되므로).
    private Map<Integer, String> findMyApplicationStatuses(List<ExtracurricularProgram> programs, Integer studentId) {
        if (programs.isEmpty()) {
            return Map.of();
        }
        List<Integer> programIds = programs.stream().map(ExtracurricularProgram::getProgramId).toList();
        return programApplicationRepository.findMyApplicationStatusesByProgramIds(studentId, programIds).stream()
                .collect(Collectors.toMap(MyApplicationStatusProjection::getProgramId,
                        MyApplicationStatusProjection::getStatus));
    }
}
