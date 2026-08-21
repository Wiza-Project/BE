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
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 접근입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "토큰이 만료되었습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "A004", "권한이 없습니다."),
    DEPARTMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "A005", "해당 업무를 수행할 수 있는 부서가 아닙니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "A006", "세션이 만료되었습니다. 다시 로그인해주세요."),

    // ── 사용자 ────────────────────────────────────────────────────
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "U002", "이미 사용 중인 아이디입니다."),
    PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "U003", "아이디 또는 비밀번호가 일치하지 않습니다."),
    ACCOUNT_DORMANT(HttpStatus.FORBIDDEN, "U004", "장기간 로그인 이력이 없어 휴면 계정으로 전환되었습니다. 본인확인 후 휴면 해제가 필요합니다."),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "U005", "잠금 처리된 계정입니다. 관리자에게 문의하세요."),
    ACCOUNT_WITHDRAWN(HttpStatus.FORBIDDEN, "U006", "탈퇴 처리된 계정입니다."),

    // ── 비교과프로그램 ────────────────────────────────────────────
    PROGRAM_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "비교과 프로그램을 찾을 수 없습니다."),
    PROGRAM_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "P002", "프로그램 분류를 찾을 수 없습니다."),
    APPLICATION_PERIOD_CLOSED(HttpStatus.BAD_REQUEST, "P003", "신청 기간이 아닙니다."),
    PROGRAM_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "P004", "모집 정원이 초과되었습니다."),
    ALREADY_APPLIED(HttpStatus.CONFLICT, "P005", "이미 신청한 프로그램입니다."),
    NOT_COMPLETED(HttpStatus.BAD_REQUEST, "P006", "이수하지 않아 수료증을 발급할 수 없습니다."),
    PROGRAM_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "P007", "모집/운영 기간이 올바르지 않습니다."),
    OPERATING_UNIT_NOT_FOUND(HttpStatus.NOT_FOUND, "P008", "운영 단위 정보를 찾을 수 없습니다."),
    // 모집이 이미 종료된 프로그램을 수정하려고 할 때 사용하는 에러코드.
    PROGRAM_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "P009", "모집이 종료된 프로그램은 수정할 수 없습니다."),
    // 모집이 이미 종료된 프로그램을 삭제하려고 할 때 사용하는 에러코드.
    PROGRAM_NOT_DELETABLE(HttpStatus.BAD_REQUEST, "P010", "모집이 종료된 프로그램은 삭제할 수 없습니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "P011", "신청 내역을 찾을 수 없습니다."),
    // 이미 승인 또는 반려 처리된 신청을 다시 승인/반려하려고 할 때 사용하는 에러코드.
    APPLICATION_ALREADY_PROCESSED(HttpStatus.CONFLICT, "P012", "이미 승인 또는 반려 처리된 신청입니다."),
    PROGRAM_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "P013", "프로그램 회차를 찾을 수 없습니다."),
    // 같은 프로그램에 이미 존재하는 회차 번호로 등록하려고 할 때 사용하는 에러코드.
    DUPLICATE_SESSION_NO(HttpStatus.CONFLICT, "P014", "이미 등록된 회차 번호입니다."),
    // 승인되지 않은(APPLIED/WAITLISTED/REJECTED) 신청 건에 출석을 기록하려고 할 때 사용하는 에러코드.
    APPLICATION_NOT_APPROVED(HttpStatus.BAD_REQUEST, "P015", "승인된 신청 건이 아니어서 출석을 기록할 수 없습니다."),
    // 모집이 이미 종료된 신청 건을 취소하려고 할 때 사용하는 에러코드.
    APPLICATION_NOT_CANCELABLE(HttpStatus.BAD_REQUEST, "P016", "모집이 종료되어 신청을 취소할 수 없습니다."),
    // 이미 반려되었거나 취소된 신청을 다시 취소하려고 할 때 사용하는 에러코드.
    APPLICATION_ALREADY_CANCELED(HttpStatus.CONFLICT, "P017", "이미 취소되었거나 반려된 신청입니다."),
    // QR 출석체크 토큰이 서명 위조/만료/programId·sessionId 불일치 등으로 유효하지 않을 때 사용하는 에러코드.
    QR_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "P018", "QR 코드가 유효하지 않거나 만료되었습니다."),
    // 승인(APPROVED)되지 않은 신청 건으로 만족도 설문을 제출하려고 할 때 사용하는 에러코드.
    SURVEY_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "P019", "승인된 신청 건만 만족도 설문을 제출할 수 있습니다."),

    // ── 핵심역량/진단 ─────────────────────────────────────────────
    COMPETENCY_NOT_FOUND(HttpStatus.NOT_FOUND, "Q001", "핵심역량 정보를 찾을 수 없습니다."),
    DIAGNOSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "Q002", "진단검사를 찾을 수 없습니다."),
    DIAGNOSIS_PERIOD_CLOSED(HttpStatus.BAD_REQUEST, "Q003", "진단검사 기간이 아닙니다."),
    DIAGNOSIS_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "Q004", "이미 응답을 제출한 진단검사입니다."),
    INCOMPLETE_ANSWER(HttpStatus.BAD_REQUEST, "Q005", "응답하지 않은 문항이 있습니다."),
    COMPETENCY_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "Q006", "핵심역량은 최대 6개까지 등록할 수 있습니다."),

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
    SURVEY_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "J003", "이미 제출한 만족도 조사입니다."),
    CANNOT_MODIFY_APPROVED_POSTING(HttpStatus.BAD_REQUEST, "J004", "이미 승인/게시된 공고는 수정할 수 없습니다."),
    JOB_POSTING_ALREADY_CLOSED(HttpStatus.BAD_REQUEST, "J005", "이미 마감된 채용공고입니다."),
    INVALID_REVIEW_STATUS(HttpStatus.BAD_REQUEST, "J006", "유효하지 않은 검수 상태값입니다."),
    INVALID_APPLICATION_PERIOD(HttpStatus.BAD_REQUEST, "J007", "신청 종료 일시는 시작 일시보다 빠를 수 없습니다."),
    COMPANY_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "J008", "존재하지 않는 기업 계정입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
