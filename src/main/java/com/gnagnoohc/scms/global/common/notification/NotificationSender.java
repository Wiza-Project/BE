package com.gnagnoohc.scms.global.common.notification;

/**
 * 도메인 공통 알림 발송 인터페이스. program/counsel/mileage/career/competency 등 각 도메인은
 * 승인/반려/적립 등 처리 로직 마지막에 이 인터페이스를 주입받아 send()만 호출하면 된다 —
 * 도메인마다 알림 발송을 개별로 구현하지 않는 것이 이 모듈의 핵심 목적이다.
 *
 * 사용 예:
 * <pre>{@code
 * private final NotificationSender notificationSender;
 *
 * notificationSender.send(new NotificationRequest(
 *         application.getStudentId(),
 *         NotificationType.APPROVED,
 *         ModuleCode.PROGRAM,
 *         "프로그램 신청이 승인되었습니다",
 *         "'%s' 프로그램 참여 신청이 승인되었습니다.".formatted(program.getTitle())
 * ));
 * }</pre>
 *
 * send()는 REQUIRES_NEW로 별도 트랜잭션에서 실행되므로, 알림 발송을 본 업무 실패 사유로 삼고 싶지
 * 않다면 호출부에서 try/catch로 감싸도 호출자의 트랜잭션(이미 끝난 승인/반려 등)에는 영향이 없다.
 * 잘못된 값(필수 필드 누락, 존재하지 않는 recipientUserId 등)은 BusinessException으로 즉시 실패한다.
 *
 * MVP1 범위는 APP 채널(인앱 알림, DB 저장 즉시 SENT)만 지원한다. SMS/알림톡/메일은 Notification
 * 엔티티의 channel 필드만 열어두었을 뿐 실제 연동은 차기 확장 대상이다 — 그때는 어느 채널로 보낼지
 * 나타낼 신호가 NotificationRequest에도 추가돼야 한다(지금은 없음, APP 고정).
 */
public interface NotificationSender {
    void send(NotificationRequest request);
}
