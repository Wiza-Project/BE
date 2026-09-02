package com.gnagnoohc.scms.domain.program.dto.application;

import java.math.BigDecimal;
import java.time.Instant;

// 학생 본인의 프로그램 참여 신청 현황 조회 응답 DTO. 신청 목록 화면 한 건에 해당한다.
public record ProgramApplicationSummaryResponseDTO(
        Integer applicationId,
        Integer programId,
        String programName,

        // 신청 상태 코드값. "APPLIED"/"WAITLISTED"/"APPROVED"/"REJECTED"/"CANCELLED".
        String applicationStatus,

        // 신청 상태의 한글 라벨.
        String applicationStatusLabel,

        // 대기 신청일 때만 값이 있는 대기순번. 그 외에는 null.
        Integer waitlistOrder,

        // 신청이 접수된 시각.
        Instant appliedAt,

        // 승인/반려 처리 시각. 아직 처리되지 않았으면 null.
        Instant processedAt,

        // 반려 사유. REJECTED가 아니면 null. 프론트가 반려 사유를 보여주는 데 사용한다.
        String decisionReason,

        // 학생이 스스로 취소한 시각. 취소하지 않았으면 null.
        Instant canceledAt,

        // 취소 사유. 취소하지 않았거나 사유를 입력하지 않았으면 null.
        String cancellationReason,

        // 이수 판정 결과. "COMPLETED"/"FAILED". 아직 판정 전이면 null.
        String completionStatus,

        // 수료증 발급 번호. 미발급이면 null. 프론트가 수료증 출력 가능 여부를 판단하는 데 사용한다.
        String certificateNo,

        Instant certificateIssuedAt,

        // 이 신청 건에 기록된 전체 출석 대상 회차 수. 출석 기록이 하나도 없으면 0.
        Integer totalAttendanceCount,

        // 그중 출석("PRESENT")으로 기록된 회차 수.
        Integer presentAttendanceCount,

        // presentAttendanceCount / totalAttendanceCount * 100. 출석 기록이 없으면 null.
        Double attendanceRate,

        // 이 신청 건으로 실제 확정(POSTED) 적립된 마일리지 점수. 아직 적립되지 않았으면 null.
        BigDecimal earnedMileagePoints,

        // 조회 시점 기준 이 프로그램의 잔여 정원(capacity - APPLIED/APPROVED 건수, 0 미만이면 0).
        // 취소 후 재방문 시에도 프론트가 재신청/대기/신청불가 버튼을 판단할 수 있도록 목록 조회에도 함께 내려준다.
        int remainingCapacity,

        // 이 프로그램의 모집 마감 시각. 프론트가 "신청 불가"(마감 지남) 여부를 판단하는 데 사용한다.
        Instant recruitmentEndsAt
) {
}
