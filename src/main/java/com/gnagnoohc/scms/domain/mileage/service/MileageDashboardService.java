package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import com.gnagnoohc.scms.domain.mileage.DTO.MileageDashboardResponse;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.repository.CommonCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/** 대시보드 화면에 필요한 마일리지 집계와 정책 진행도를 조합한다. */
public class MileageDashboardService {

    private static final int RECENT_ITEM_LIMIT = 5;
    private static final int SEMESTER_TREND_LIMIT = 4;
    private static final String SEMESTER_CODE_GROUP = "SEMESTER";

    private final MileageTransactionRepository mileageTransactionRepository;
    private final CompetencyRepository competencyRepository;
    private final MileageAcademicPeriodService mileageAcademicPeriodService;
    private final CommonCodeRepository commonCodeRepository;
    private final MileageSemesterCodeValidator mileageSemesterCodeValidator;

    /**
     * 로그인한 학생의 선택 학기 기준 대시보드 데이터를 생성한다.
     * 누적 점수는 전체 확정 거래, 학기 점수·분포는 선택 학기에 귀속된 확정 거래를 사용한다.
     */
    public MileageDashboardResponse getDashboard(
            Integer studentId,
            String semesterCode
    ) {
        String selectedSemesterCode = mileageSemesterCodeValidator.requireSemesterCode(semesterCode);
        MileageAcademicPeriodService.PeriodBounds periodBounds =
                mileageAcademicPeriodService.resolveCurrentPeriodBounds();

        BigDecimal currentSemesterPoints = valueOrZero(
                mileageTransactionRepository.sumPostedPointsByStudentAndPeriod(
                        studentId,
                        periodBounds.startAt(),
                        periodBounds.endAt(),
                        selectedSemesterCode));
        BigDecimal annualPoints = valueOrZero(
                mileageTransactionRepository.sumPostedPointsByStudentAndAllSemester(
                        studentId, periodBounds.startAt(), periodBounds.endAt()));
        BigDecimal cumulativePoints = valueOrZero(
                mileageTransactionRepository.sumPostedPointsByStudent(studentId));

        PageRequest recentItems = PageRequest.of(0, RECENT_ITEM_LIMIT);

        var competencyBreakdown = getCompetencyBreakdown(
                studentId,
                periodBounds.startAt(),
                periodBounds.endAt(),
                selectedSemesterCode);

        var semesterTrend = getSemesterTrend(
                studentId,
                selectedSemesterCode,
                periodBounds);

        var recentTransactions = getRecentTransactions(studentId, recentItems);

        return new MileageDashboardResponse(
                new MileageDashboardResponse.Period(selectedSemesterCode),
                new MileageDashboardResponse.Summary(
                        currentSemesterPoints,
                        annualPoints,
                        cumulativePoints,
                        mileageTransactionRepository.findLastPostedAt(studentId)),
                competencyBreakdown,
                semesterTrend,
                recentTransactions
        );
    }

    /** 대시보드 외의 화면에서도 사용할 수 있도록 최근 거래 미리보기를 제공한다. */
    public List<MileageDashboardResponse.TransactionSummary> getRecentTransactions(Integer studentId) {
        return getRecentTransactions(studentId, PageRequest.of(0, RECENT_ITEM_LIMIT));
    }

    /** 활성 최상위 핵심역량을 모두 반환해 점수가 0인 역량도 차트에 표시한다. */
    private List<MileageDashboardResponse.CompetencySummary> getCompetencyBreakdown(
            Integer studentId,
            Instant periodStart,
            Instant periodEnd,
            String semesterCode
    ) {
        Map<Integer, BigDecimal> pointsByCompetencyId = mileageTransactionRepository
                .findCompetencyBreakdown(studentId, periodStart, periodEnd, semesterCode)
                .stream()
                .collect(Collectors.toMap(
                        MileageTransactionRepository.CompetencySummaryProjection::getCompetencyId,
                        item -> valueOrZero(item.getPoints())));

        List<Competency> topLevelCompetencies = competencyRepository
                .findByParentCompetencyIsNullAndActiveTrueOrderByDisplayOrderAsc();

        if (topLevelCompetencies.isEmpty()) {
            return mileageTransactionRepository
                    .findCompetencyBreakdown(studentId, periodStart, periodEnd, semesterCode)
                    .stream()
                    .map(item -> new MileageDashboardResponse.CompetencySummary(
                            item.getCompetencyId(), item.getCompetencyName(), item.getPoints()))
                    .toList();
        }

        return topLevelCompetencies.stream()
                .map(competency -> new MileageDashboardResponse.CompetencySummary(
                        competency.getCompetencyId(),
                        competency.getCompetencyName(),
                        pointsByCompetencyId.getOrDefault(
                                competency.getCompetencyId(), BigDecimal.ZERO)))
                .toList();
    }

    /** 선택 학사기간 안의 학기별 적립 점수를 백엔드 학기 정의 순서로 반환한다. */
    private List<MileageDashboardResponse.SemesterTrendSummary> getSemesterTrend(
            Integer studentId,
            String semesterCode,
            MileageAcademicPeriodService.PeriodBounds periodBounds
    ) {
        Map<String, BigDecimal> pointsBySemester = mileageTransactionRepository
                .findSemesterTrendByStudent(
                        studentId,
                        periodBounds.startAt(),
                        periodBounds.endAt(),
                        semesterCode)
                .stream()
                .collect(Collectors.toMap(
                        item -> mileageSemesterCodeValidator.normalize(item.getSemesterCode()),
                        item -> valueOrZero(item.getPoints()),
                        BigDecimal::add));

        List<CommonCode> definedSemesters = commonCodeRepository
                .findByCodeGroupAndActiveTrueOrderBySortOrderAsc(SEMESTER_CODE_GROUP)
                .stream()
                .filter(code -> !mileageSemesterCodeValidator.isAllSemesterCode(code.getCode()))
                .toList();
        Map<String, String> definedCodeByNormalizedCode = definedSemesters
                .stream()
                .collect(Collectors.toMap(
                        code -> mileageSemesterCodeValidator.normalize(code.getCode()),
                        CommonCode::getCode,
                        (first, ignored) -> first));
        Map<String, Integer> semesterOrderByCode = definedSemesters.stream()
                .collect(Collectors.toMap(
                        code -> mileageSemesterCodeValidator.normalize(code.getCode()),
                        CommonCode::getSortOrder,
                        (first, ignored) -> first));

        List<MileageDashboardResponse.SemesterTrendSummary> trend = new ArrayList<>();
        definedCodeByNormalizedCode.forEach((normalizedCode, definedCode) -> trend.add(
                new MileageDashboardResponse.SemesterTrendSummary(
                        definedCode,
                        pointsBySemester.getOrDefault(normalizedCode, BigDecimal.ZERO))));

        // 공통코드에 아직 반영되지 않은 기존 정책 코드도 데이터 유실 없이 마지막에 보존한다.
        pointsBySemester.forEach((code, points) -> {
            if (!definedCodeByNormalizedCode.containsKey(code)) {
                trend.add(new MileageDashboardResponse.SemesterTrendSummary(
                        code, points));
            }
        });

        boolean selectedPeriodExists = trend.stream().anyMatch(item ->
                item.semesterCode().equalsIgnoreCase(semesterCode));
        if (!selectedPeriodExists) {
            trend.add(new MileageDashboardResponse.SemesterTrendSummary(
                    semesterCode, BigDecimal.ZERO));
        }

        return trend.stream()
                .sorted(Comparator
                        .comparingInt((MileageDashboardResponse.SemesterTrendSummary item) ->
                                semesterOrderByCode.getOrDefault(
                                        mileageSemesterCodeValidator.normalize(item.semesterCode()),
                                        Integer.MAX_VALUE))
                        .thenComparing(MileageDashboardResponse.SemesterTrendSummary::semesterCode))
                .skip(Math.max(0, trend.size() - SEMESTER_TREND_LIMIT))
                .toList();
    }

    /** 거래 원장 조회 결과를 최근 내역 카드에 필요한 필드만으로 변환한다. */
    private List<MileageDashboardResponse.TransactionSummary> getRecentTransactions(
            Integer studentId,
            PageRequest pageRequest
    ) {
        return mileageTransactionRepository
                .findRecentTransactions(studentId, pageRequest)
                .stream()
                .map(item -> new MileageDashboardResponse.TransactionSummary(
                        item.getTransactionId(),
                        item.getActivityName(),
                        item.getTransactionType(),
                        item.getPoints(),
                        item.getTransactionStatus(),
                        item.getOccurredAt()))
                .toList();
    }

    /** 집계 쿼리 결과가 null일 때 화면 계산에 사용할 0점으로 보정한다. */
    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
