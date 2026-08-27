package com.gnagnoohc.scms.domain.program.listener;

import com.gnagnoohc.scms.domain.program.event.ApplicationDecidedEvent;
import com.gnagnoohc.scms.global.common.notification.ModuleCode;
import com.gnagnoohc.scms.global.common.notification.NotificationSender;
import com.gnagnoohc.scms.global.common.notification.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApplicationDecidedNotificationEventListenerTest {

    @Mock
    private NotificationSender notificationSender;

    @InjectMocks
    private ApplicationDecidedNotificationEventListener listener;

    @Test
    void handle_whenApproved_sendsApprovedNotification() {
        listener.handle(new ApplicationDecidedEvent(5, 100, 1, "테스트 프로그램", "APPROVED", null));

        verify(notificationSender).send(argThat(request ->
                request.recipientUserId().equals(100)
                        && request.type() == NotificationType.APPROVED
                        && request.moduleCode() == ModuleCode.PROGRAM
                        && request.content().contains("테스트 프로그램")));
    }

    @Test
    void handle_whenRejected_sendsRejectedNotificationWithReason() {
        listener.handle(new ApplicationDecidedEvent(5, 100, 1, "테스트 프로그램", "REJECTED", "정원 외 사유"));

        verify(notificationSender).send(argThat(request ->
                request.recipientUserId().equals(100)
                        && request.type() == NotificationType.REJECTED
                        && request.moduleCode() == ModuleCode.PROGRAM
                        && request.content().contains("테스트 프로그램")
                        && request.content().contains("정원 외 사유")));
    }

    @Test
    void handle_whenNotificationSenderThrows_swallowsExceptionAndLogsWarning() {
        doThrow(new RuntimeException("발송 실패")).when(notificationSender).send(any());

        assertThatCode(() -> listener.handle(
                new ApplicationDecidedEvent(5, 100, 1, "테스트 프로그램", "APPROVED", null)))
                .doesNotThrowAnyException();
    }
}
