package com.gnagnoohc.scms.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 체계
 *   C###  공통
 *   A###  인증/인가
 *   U###  사용자
 *   P###  비교과프로그램  (프로세스 P1100, P1200)
 *   Q###  핵심역량/진단   (P2100, P2200)
 *   S###  상담            (P3100)
 *   M###  마일리지        (P4100)
 *   J###  취창업          (P5100)
 *
 * 새 코드를 추가하면 프론트 담당자에게 반드시 공유하세요.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── 공통 ──────────────────────────────────────────────────────
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C002", "요청한 데이터를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C003", "허용되지 않은 요청 방식입니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "C004", "파일 업로드에 실패했습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "서버 오류가 발생했습니다."),

    // ── 인증/인가 ─────────────────────────────────────────────────
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "로그인이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "토큰이 만료되었습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "A004", "권한이 없습니다."),
    DEPARTMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "A005", "해당 업무를 수행할 수 있는 부서가 아닙니다."),

    // ── 사용자 ────────────────────────────────────────────────────
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "U002", "이미 사용 중인 아이디입니다."),
    PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "U003", "아이디 또는 비밀번호가 일치하지 않습니다."),

    // ── 비교과프로그램 ────────────────────────────────────────────
    PROGRAM_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "비교과 프로그램을 찾을 수 없습니다."),
    PROGRAM_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "P002", "프로그램 분류를 찾을 수 없습니다."),
    APPLICATION_PERIOD_CLOSED(HttpStatus.BAD_REQUEST, "P003", "신청 기간이 아닙니다."),
    PROGRAM_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "P004", "모집 정원이 초과되었습니다."),
    ALREADY_APPLIED(HttpStatus.CONFLICT, "P005", "이미 신청한 프로그램입니다."),
    NOT_COMPLETED(HttpStatus.BAD_REQUEST, "P006", "이수하지 않아 수료증을 발급할 수 없습니다."),
    PROGRAM_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "P007", "모집/운영 기간이 올바르지 않습니다."),
    OPERATING_UNIT_NOT_FOUND(HttpStatus.NOT_FOUND, "P008", "운영 단위 정보를 찾을 수 없습니다."),

    // ── 핵심역량/진단 ─────────────────────────────────────────────
    COMPETENCY_NOT_FOUND(HttpStatus.NOT_FOUND, "Q001", "핵심역량 정보를 찾을 수 없습니다."),
    DIAGNOSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "Q002", "진단검사를 찾을 수 없습니다."),
    DIAGNOSIS_PERIOD_CLOSED(HttpStatus.BAD_REQUEST, "Q003", "진단검사 기간이 아닙니다."),
    DIAGNOSIS_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "Q004", "이미 응답을 제출한 진단검사입니다."),
    INCOMPLETE_ANSWER(HttpStatus.BAD_REQUEST, "Q005", "응답하지 않은 문항이 있습니다."),

    // ── 상담 ──────────────────────────────────────────────────────
    COUNSELOR_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "상담사를 찾을 수 없습니다."),
    SCHEDULE_NOT_AVAILABLE(HttpStatus.CONFLICT, "S002", "이미 예약된 시간입니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "S003", "상담 예약을 찾을 수 없습니다."),
    CANNOT_CANCEL_CONFIRMED(HttpStatus.BAD_REQUEST, "S004", "확정된 상담은 취소할 수 없습니다."),

    // ── 마일리지 ──────────────────────────────────────────────────
    MILEAGE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "마일리지 항목을 찾을 수 없습니다."),
    MILEAGE_ALREADY_CLAIMED(HttpStatus.CONFLICT, "M002", "이미 실적을 신청한 항목입니다."),
    INSUFFICIENT_MILEAGE(HttpStatus.BAD_REQUEST, "M003", "장학금 지급 기준 점수에 미달합니다."),
    MILEAGE_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "M004", "마일리지 정책을 찾을 수 없습니다."),

    // ── 취창업 ────────────────────────────────────────────────────
    JOB_POSTING_NOT_FOUND(HttpStatus.NOT_FOUND, "J001", "구인공고를 찾을 수 없습니다."),
    PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, "J002", "포트폴리오를 찾을 수 없습니다."),
    SURVEY_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "J003", "이미 제출한 만족도 조사입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
