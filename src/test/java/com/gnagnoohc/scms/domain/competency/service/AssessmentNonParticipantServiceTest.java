package com.gnagnoohc.scms.domain.competency.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentNonParticipantNotifyResponse;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentNonParticipantResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentNonParticipantQueryRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.common.notification.NotificationSender;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentNonParticipantServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    AssessmentRoundRepository assessmentRoundRepository;

    @Mock
    AssessmentNonParticipantQueryRepository assessmentNonParticipantQueryRepository;

    @Mock
    NotificationSender notificationSender;

    @InjectMocks
    AssessmentNonParticipantService assessmentNonParticipantService;

    private AssessmentRound buildRound(JsonNode targetCondition) {
        Instant startsAt = Instant.now();
        return AssessmentRound.create("2026학년도 1학기 사전진단", 2026, "SPRING", "PRE",
                startsAt, startsAt.plusSeconds(3600), targetCondition, 1);
    }

    @Test
    void getNonParticipants_whenRoundNotFound_throwsAssessmentRoundNotFound() {
        when(assessmentRoundRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assessmentNonParticipantService.getNonParticipants(999, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND);
    }

    @Test
    void getNonParticipants_returnsPageFromQueryRepository() {
        JsonNode targetCondition = objectMapper.valueToTree(Map.of("grades", List.of(3)));
        AssessmentRound round = buildRound(targetCondition);
        Pageable pageable = PageRequest.of(0, 20);
        AssessmentNonParticipantResponse row = new AssessmentNonParticipantResponse(
                1, "202012345", "홍길동", "hong@example.com", "010-0000-0000", "컴퓨터공학과", 3);

        when(assessmentRoundRepository.findById(1)).thenReturn(Optional.of(round));
        when(assessmentNonParticipantQueryRepository.findNonParticipants(1, targetCondition, pageable))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        PageResponse<AssessmentNonParticipantResponse> response =
                assessmentNonParticipantService.getNonParticipants(1, pageable);

        assertThat(response.content()).containsExactly(row);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void notify_whenRoundNotFound_throwsAssessmentRoundNotFound() {
        when(assessmentRoundRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assessmentNonParticipantService.notify(999, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND);
        verify(notificationSender, never()).send(any());
    }

    @Test
    void notify_withoutUserIds_sendsToEveryNonParticipant() {
        AssessmentRound round = buildRound(null);
        when(assessmentRoundRepository.findById(1)).thenReturn(Optional.of(round));
        when(assessmentNonParticipantQueryRepository.findNonParticipantUserIds(1, null))
                .thenReturn(List.of(100, 101, 102));

        AssessmentNonParticipantNotifyResponse response = assessmentNonParticipantService.notify(1, null);

        assertThat(response.sentUserIds()).containsExactly(100, 101, 102);
        assertThat(response.failedCount()).isZero();
        verify(notificationSender).send(argThat(r -> r.recipientUserId().equals(100)
                && r.content().contains(round.getAssessmentName())));
        verify(notificationSender).send(argThat(r -> r.recipientUserId().equals(101)));
        verify(notificationSender).send(argThat(r -> r.recipientUserId().equals(102)));
    }

    @Test
    void notify_withUserIds_sendsOnlyIntersectionWithActualNonParticipants() {
        AssessmentRound round = buildRound(null);
        when(assessmentRoundRepository.findById(1)).thenReturn(Optional.of(round));
        when(assessmentNonParticipantQueryRepository.findNonParticipantUserIds(1, null))
                .thenReturn(List.of(100, 101, 102));

        // 103은 요청에는 있지만 실제 미응시자 집합엔 없다(이미 제출했거나 대상 조건 밖) — 발송돼선 안 된다.
        AssessmentNonParticipantNotifyResponse response = assessmentNonParticipantService.notify(1, List.of(101, 103));

        assertThat(response.sentUserIds()).containsExactly(101);
        assertThat(response.failedCount()).isZero();
        verify(notificationSender).send(argThat(r -> r.recipientUserId().equals(101)));
        verify(notificationSender, never()).send(argThat(r -> r.recipientUserId().equals(100)));
        verify(notificationSender, never()).send(argThat(r -> r.recipientUserId().equals(103)));
    }

    // Set.copyOf(List)는 원소에 null이 있으면 NPE를 던진다 — userIds에 null이 섞여 와도
    // (예: [101, null]) 500 대신 그 원소만 무시하고 나머지 교집합은 정상 처리돼야 한다.
    @Test
    void notify_withUserIdsContainingNull_ignoresNullAndSendsToRestOfIntersection() {
        AssessmentRound round = buildRound(null);
        when(assessmentRoundRepository.findById(1)).thenReturn(Optional.of(round));
        when(assessmentNonParticipantQueryRepository.findNonParticipantUserIds(1, null))
                .thenReturn(List.of(100, 101));

        AssessmentNonParticipantNotifyResponse response =
                assessmentNonParticipantService.notify(1, Arrays.asList(101, null));

        assertThat(response.sentUserIds()).containsExactly(101);
        assertThat(response.failedCount()).isZero();
        verify(notificationSender).send(argThat(r -> r.recipientUserId().equals(101)));
        verify(notificationSender, never()).send(argThat(r -> r.recipientUserId().equals(100)));
    }

    @Test
    void notify_whenOneRecipientFails_stillSendsToTheRestAndReportsFailedCount() {
        AssessmentRound round = buildRound(null);
        when(assessmentRoundRepository.findById(1)).thenReturn(Optional.of(round));
        when(assessmentNonParticipantQueryRepository.findNonParticipantUserIds(1, null))
                .thenReturn(List.of(100, 101));
        doThrow(new RuntimeException("발송 실패"))
                .when(notificationSender).send(argThat(r -> r.recipientUserId().equals(100)));

        AssessmentNonParticipantNotifyResponse response = assessmentNonParticipantService.notify(1, null);

        assertThat(response.sentUserIds()).containsExactly(101);
        assertThat(response.failedCount()).isEqualTo(1);
        verify(notificationSender).send(argThat(r -> r.recipientUserId().equals(101)));
    }
}
