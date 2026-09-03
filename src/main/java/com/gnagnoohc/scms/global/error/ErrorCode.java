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
 *   B###  공통 게시판(공지/FAQ)
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
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "C005", "허용되지 않은 파일 형식입니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "C006", "파일 용량이 허용치를 초과했습니다."),
    INVALID_FILE_NAME(HttpStatus.BAD_REQUEST, "C007", "파일명이 올바르지 않습니다."),
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

    // ── 동의(공통 모듈) ───────────────────────────────────────────
    USER_CONSENT_NOT_FOUND(HttpStatus.NOT_FOUND, "U007", "동의 이력을 찾을 수 없습니다."),
    CONSENT_ALREADY_WITHDRAWN(HttpStatus.CONFLICT, "U008", "이미 철회된 동의입니다."),
    REQUIRED_CONSENT_NOT_AGREED(HttpStatus.FORBIDDEN, "U009", "필수 동의 항목에 동의해야 이용할 수 있는 기능입니다."),
    CONSENT_VERSION_OUTDATED(HttpStatus.CONFLICT, "U010", "약관 또는 개인정보 처리방침이 개정되어 재동의가 필요합니다."),
    INVALID_CONSENT_POLICY(HttpStatus.BAD_REQUEST, "U011", "만료되었거나 비활성화된 동의 정책입니다."),
    CONSENT_SAVE_CONFLICT(HttpStatus.CONFLICT, "U012", "동의 처리가 동시에 요청되어 실패했습니다. 다시 시도해주세요."),

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
    // 승인(APPROVED)되지 않은 신청 건으로 만족도 설문을 제출하려고 할 때 사용하는 에러코드.
    SURVEY_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "P019", "승인된 신청 건만 만족도 설문을 제출할 수 있습니다."),
    // register()/update() 요청에 존재하지 않는 fileGroupId가 담겨왔을 때 사용하는 에러코드.
    PROGRAM_FILE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "P020", "첨부파일을 찾을 수 없습니다."),
    // 이미 다른 프로그램에 연결된 fileGroupId를 재사용하려고 할 때 사용하는 에러코드.
    PROGRAM_FILE_GROUP_ALREADY_LINKED(HttpStatus.CONFLICT, "P021", "이미 다른 프로그램에 연결된 첨부파일입니다."),
    // register() 요청에 회차(sessions)가 하나도 담겨오지 않았을 때 사용하는 에러코드.
    // 프론트가 이 케이스만 구분해 "회차 관리" 탭으로 안내하는 모달을 띄워야 해서 전용 코드로 분리했다.
    PROGRAM_SESSION_REQUIRED(HttpStatus.BAD_REQUEST, "P022", "회차는 최소 1개 이상 등록해야 합니다."),
    // update() 요청에 fileGroupId와 clearFileGroup=true가 동시에 담겨왔을 때(모순된 요청) 사용하는 에러코드.
    PROGRAM_FILE_GROUP_CONFLICT(HttpStatus.BAD_REQUEST, "P023", "fileGroupId와 clearFileGroup을 동시에 지정할 수 없습니다."),
    // 회차 등록/수정 시 locationType=DIRECT_INPUT인데 location이 비어있을 때 사용하는 에러코드.
    SESSION_LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "P024", "회차 장소를 입력해주세요."),
    // 회차 등록/수정 시 locationType=SAME_AS_PREVIOUS인데 참조할 이전 회차(또는 그 장소)가 없을 때 사용하는 에러코드.
    PREVIOUS_SESSION_LOCATION_NOT_FOUND(HttpStatus.BAD_REQUEST, "P025", "복사할 이전 회차의 장소 정보가 없습니다."),
    // 회차 등록 시 회차 번호가 1부터 빈 번호 없이 연속되지 않을 때 사용하는 에러코드.
    PROGRAM_SESSION_NO_NOT_CONTIGUOUS(HttpStatus.BAD_REQUEST, "P026", "회차 번호는 1부터 빠짐없이 연속되어야 합니다."),

    // ── 핵심역량/진단 ─────────────────────────────────────────────
    COMPETENCY_NOT_FOUND(HttpStatus.NOT_FOUND, "Q001", "핵심역량 정보를 찾을 수 없습니다."),
    DIAGNOSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "Q002", "진단검사를 찾을 수 없습니다."),
    DIAGNOSIS_PERIOD_CLOSED(HttpStatus.BAD_REQUEST, "Q003", "진단검사 기간이 아닙니다."),
    DIAGNOSIS_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "Q004", "이미 응답을 제출한 진단검사입니다."),
    INCOMPLETE_ANSWER(HttpStatus.BAD_REQUEST, "Q005", "응답하지 않은 문항이 있습니다."),
    COMPETENCY_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "Q006", "핵심역량은 최대 6개까지 등록할 수 있습니다."),
    ASSESSMENT_QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Q007", "진단문항을 찾을 수 없습니다."),
    // 이전 버전으로 대체되어 비활성화된 문항을 직접 수정하려고 할 때 사용하는 에러코드. 최신 버전만 수정 가능.
    INACTIVE_QUESTION_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "Q008", "비활성화된 문항은 수정할 수 없습니다."),
    ASSESSMENT_ROUND_NOT_FOUND(HttpStatus.NOT_FOUND, "Q009", "진단 회차를 찾을 수 없습니다."),
    // 같은 학년도·학기·진단구분(사전/사후)의 회차를 중복 개설하려고 할 때 사용하는 에러코드.
    DUPLICATE_ASSESSMENT_ROUND(HttpStatus.CONFLICT, "Q010", "같은 학년도·학기·진단구분의 회차가 이미 존재합니다."),
    INVALID_ASSESSMENT_PERIOD(HttpStatus.BAD_REQUEST, "Q011", "응시 시작일은 종료일보다 빨라야 합니다."),
    // 이미 응시(문항 응답)가 시작된 회차를 수정하려고 할 때 사용하는 에러코드.
    ASSESSMENT_ROUND_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "Q012", "이미 응시가 시작된 회차는 수정할 수 없습니다."),
    // 존재하지 않거나 본인(로그인한 학생) 소유가 아닌 attempt에 접근하려고 할 때 사용하는 에러코드(소유권 비노출을 위해 둘을 구분하지 않음).
    ASSESSMENT_ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "Q014", "응시 정보를 찾을 수 없습니다."),
    // 해당 회차의 문항 구성에 포함되지 않은 questionId로 응답을 저장하려고 할 때 사용하는 에러코드.
    QUESTION_NOT_IN_ROUND(HttpStatus.BAD_REQUEST, "Q015", "해당 회차에 속하지 않은 문항입니다."),
    // 문항의 response_options에 정의되지 않은 값으로 응답을 저장하려고 할 때 사용하는 에러코드.
    INVALID_RESPONSE_VALUE(HttpStatus.BAD_REQUEST, "Q016", "허용되지 않은 응답값입니다."),
    // 같은 문항에 대한 최초 응답 저장이 동시에 들어와 유니크 제약(uq_assessment_response_attempt_question)에
    // 걸렸을 때 사용하는 에러코드. 먼저 간 요청은 이미 저장 성공했으므로 클라이언트는 같은 요청을 재시도하면 된다.
    RESPONSE_SAVE_CONFLICT(HttpStatus.CONFLICT, "Q017", "저장이 동시에 처리되어 실패했습니다. 다시 시도해주세요."),
    // 아직 제출·채점되지 않은 attempt(assessment_score 미생성)로 결과 조회를 시도할 때 사용하는 에러코드.
    RESULT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "Q018", "아직 채점되지 않은 진단입니다."),
    // target_condition에 grades/majorCodeIds 외의 키가 있을 때 사용. 조용히 무시하면 대상자 집계가 잘못된다.
    ASSESSMENT_TARGET_CONDITION_UNSUPPORTED(HttpStatus.BAD_REQUEST, "Q019", "지원하지 않는 응시 대상 조건입니다."),
    // grades/majorCodeIds 값이 배열이 아니거나 원소가 정수가 아닐 때 사용. Q019(인식 못 하는 키)와 달리 키는 맞음.
    ASSESSMENT_TARGET_CONDITION_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "Q020", "저장된 응시 대상 조건의 형식이 올바르지 않습니다. 회차 등록/수정 화면에서 대상 조건을 다시 확인해주세요."),
    // 통계 조회 groupBy 파라미터가 GRADE/MAJOR 중 하나가 아닐 때 사용.
    ASSESSMENT_INVALID_GROUP_AXIS(HttpStatus.BAD_REQUEST, "Q021", "지원하지 않는 통계 그룹 기준입니다."),
    // 사전·사후 비교에서 같은 응시(attemptId)를 두 번 지정했을 때 사용. 비교 대상 두 회차는 서로 달라야 한다.
    ASSESSMENT_COMPARISON_SAME_ATTEMPT(HttpStatus.BAD_REQUEST, "Q022", "비교할 두 응시는 서로 달라야 합니다."),
    // 사전·사후 비교 대상 두 응시가 같은 학년도의 사전(PRE)·사후(POST) 한 쌍이 아닐 때 사용.
    // 같은 구분 2건이거나 학년도가 다르면 변화량 방향을 정할 근거가 없어 비교를 거부한다.
    ASSESSMENT_COMPARISON_NOT_PRE_POST_PAIR(HttpStatus.BAD_REQUEST, "Q023", "사전·사후 비교는 같은 학년도의 사전·사후 응시 한 쌍이어야 합니다."),
    // 문항이 하나도 매핑되지 않은 회차를 제출하려고 할 때 사용. 채점 대상이 없어 결과를 만들 수 없다.
    ASSESSMENT_ROUND_NO_QUESTIONS(HttpStatus.BAD_REQUEST, "Q024", "문항이 없는 회차는 제출할 수 없습니다."),

    // ── 상담 ──────────────────────────────────────────────────────
    COUNSELOR_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "상담사를 찾을 수 없습니다."),
    SCHEDULE_NOT_AVAILABLE(HttpStatus.CONFLICT, "S002", "이미 예약된 시간입니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "S003", "상담 예약을 찾을 수 없습니다."),
    CANNOT_CANCEL_CONFIRMED(HttpStatus.BAD_REQUEST, "S004", "확정된 상담은 취소할 수 없습니다."),
    ALREADY_PROCESSED_RESERVATION(HttpStatus.CONFLICT, "S005", "이미 처리된 상담 예약입니다."),
    ASSIGNMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "S006", "상담사 배정을 찾을 수 없습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "S007", "상담 회기를 찾을 수 없습니다."),
    SESSION_STATE_NOT_ALLOWED(HttpStatus.CONFLICT, "S008", "허용되지 않은 회기 상태입니다."),
    PRIVATE_RECORD_STATE_NOT_ALLOWED(HttpStatus.CONFLICT, "S009", "허용되지 않은 비공개 기록 상태입니다."),
    PUBLIC_RESULT_STATE_NOT_ALLOWED(HttpStatus.CONFLICT, "S010", "허용되지 않은 공개 결과 상태입니다."),
    PUBLIC_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "S011", "공개된 상담 결과를 찾을 수 없습니다."),
    PUBLIC_RESULT_NO_CHANGES(HttpStatus.CONFLICT, "S012", "수정한 내역이 없습니다."),
    // 학생이 예약 일정 변경 화면을 연 뒤 다른 요청이 먼저 같은 예약의 일정을 바꿔, 요청의
    // expectedScheduleId(화면이 기억하던 예약 당시 일정)와 DB의 실제 현재 일정이 달라졌을 때 사용하는
    // 에러코드. 새 일정의 마감·정원 문제(S002)와는 원인이 다르므로 별도 코드로 구분한다.
    RESERVATION_SCHEDULE_CONFLICT(HttpStatus.CONFLICT, "S013", "예약 일정이 이미 변경되었습니다. 최신 예약 정보를 확인해 주세요."),
    // 활성 버전(STRESS/1) 문항이 11개가 아니거나 번호·선택지 구성이 확정값과 다를 때 사용하는 에러코드.
    // 불완전한 문항을 학생에게 그대로 내려주지 않기 위해 문항 조회·제출 양쪽에서 이 코드로 통일해 막는다.
    STRESS_TEST_NOT_AVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "S014", "현재 스트레스 검사를 이용할 수 없습니다."),

    // ── 마일리지 ──────────────────────────────────────────────────
    MILEAGE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "마일리지 항목을 찾을 수 없습니다."),
    MILEAGE_ALREADY_CLAIMED(HttpStatus.CONFLICT, "M002", "이미 실적을 신청한 항목입니다."),
    INSUFFICIENT_MILEAGE(HttpStatus.BAD_REQUEST, "M003", "장학금 지급 기준 점수에 미달합니다."),
    MILEAGE_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "M004", "마일리지 정책을 찾을 수 없습니다."),
    // 같은 활동유형+학년도+학기+버전 조합으로 이미 정책이 존재할 때 사용하는 에러코드(uq_mileage_policy_activity_period_version).
    MILEAGE_POLICY_DUPLICATE(HttpStatus.CONFLICT, "M005", "이미 동일한 조건의 마일리지 정책이 존재합니다."),
    MILEAGE_POLICY_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "M006", "적용 시작일은 종료일보다 빨라야 합니다."),
    MILEAGE_ACTIVITY_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "M007", "마일리지 활동 유형을 찾을 수 없습니다."),
    MILEAGE_POLICY_VALID_TO_CONFLICT(HttpStatus.BAD_REQUEST, "M008", "validTo와 clearValidTo를 동시에 지정할 수 없습니다."),

    // ── 취창업 ────────────────────────────────────────────────────
    JOB_POSTING_NOT_FOUND(HttpStatus.NOT_FOUND, "J001", "구인공고를 찾을 수 없습니다."),
    PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, "J002", "포트폴리오를 찾을 수 없습니다."),
    SURVEY_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "J003", "이미 제출한 만족도 조사입니다."),
    CANNOT_MODIFY_APPROVED_POSTING(HttpStatus.BAD_REQUEST, "J004", "이미 승인/게시된 공고는 수정할 수 없습니다."),
    JOB_POSTING_ALREADY_CLOSED(HttpStatus.BAD_REQUEST, "J005", "이미 마감된 채용공고입니다."),
    INVALID_REVIEW_STATUS(HttpStatus.BAD_REQUEST, "J006", "유효하지 않은 검수 상태값입니다."),
    INVALID_APPLICATION_PERIOD(HttpStatus.BAD_REQUEST, "J007", "신청 종료 일시는 시작 일시보다 빠를 수 없습니다."),
    COMPANY_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "J008", "존재하지 않는 기업 계정입니다."),
    JOB_POSTING_NOT_PUBLISHED(HttpStatus.BAD_REQUEST, "J009", "게시(승인)되지 않은 공고에는 지원할 수 없습니다."),
    APPLICATION_PERIOD_EXPIRED(HttpStatus.BAD_REQUEST, "J010", "접수 마감 기간이 경과하여 지원 또는 취소할 수 없습니다."),
    JOB_POSTING_ALREADY_APPLIED(HttpStatus.CONFLICT, "J011", "이미 해당 채용공고에 지원 완료된 상태입니다."),
    JOB_POSTING_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "J012", "채용 지원 이력을 찾을 수 없거나 이미 취소된 상태입니다."),
    DUPLICATE_COMPANY_ACCOUNT_NO(HttpStatus.CONFLICT, "J013", "이미 등록된 사업자등록번호입니다."),
    COVER_LETTER_NOT_FOUND(HttpStatus.NOT_FOUND, "J014", "자기소개서를 찾을 수 없습니다."),
    COVER_LETTER_ALREADY_EXISTS(HttpStatus.CONFLICT, "J015", "이미 작성된 자기소개서가 있습니다. 버전 관리 기능을 이용해주세요."),
    // 이력서/자기소개서/포트폴리오 버전 번호 채번이 동시 요청으로 유니크 제약(uq_career_document_student_type_version)에 걸렸을 때 사용하는 에러코드.
    DOCUMENT_VERSION_CONFLICT(HttpStatus.CONFLICT, "J016", "저장이 동시에 처리되어 실패했습니다. 다시 시도해주세요."),
    RESUME_NOT_FOUND(HttpStatus.NOT_FOUND, "J017", "이력서를 찾을 수 없습니다."),
    RESUME_ALREADY_EXISTS(HttpStatus.CONFLICT, "J018", "이미 작성된 이력서가 있습니다. 버전 관리 기능을 이용해주세요."),
    RESUME_NOT_LATEST_VERSION(HttpStatus.CONFLICT, "J019", "최신 이력서 버전만 수정할 수 있습니다."),
    // ── 알림 ──────────────────────────────────────────────────────
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없습니다."),

    // ── 공통 게시판(공지/FAQ) ─────────────────────────────────────
    // 카테고리(=common_code FAQ_CATEGORY 그룹의 code)가 존재하지 않을 때 사용하는 에러코드.
    BOARD_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "B001", "카테고리를 찾을 수 없습니다."),
    BOARD_CATEGORY_INACTIVE(HttpStatus.BAD_REQUEST, "B002", "비활성화된 카테고리입니다."),
    BOARD_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "B003", "게시글을 찾을 수 없습니다."),
    // URL의 boardType과 게시글이 실제로 속한 board_type이 다를 때 사용하는 에러코드(타 게시판 글 접근 차단).
    BOARD_POST_BOARD_MISMATCH(HttpStatus.NOT_FOUND, "B004", "해당 게시판의 게시글이 아닙니다."),
    BOARD_PIN_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "B005", "상단 고정을 사용하지 않는 게시판입니다."),
    BOARD_ATTACHMENT_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "B006", "첨부파일 개수가 허용치를 초과했습니다."),
    // removeFileIds로 지정한 storedFileId가 이 게시글의 첨부파일이 아닐 때 사용하는 에러코드.
    BOARD_ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "B007", "게시글에 첨부된 파일이 아닙니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
