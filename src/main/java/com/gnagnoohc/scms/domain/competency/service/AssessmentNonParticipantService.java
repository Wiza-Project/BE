package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentNonParticipantNotifyResponse;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentNonParticipantResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentNonParticipantQueryRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import com.gnagnoohc.scms.global.common.notification.ModuleCode;
import com.gnagnoohc.scms.global.common.notification.NotificationRequest;
import com.gnagnoohc.scms.global.common.notification.NotificationSender;
import com.gnagnoohc.scms.global.common.notification.NotificationType;
import com.gnagnoohc.scms.global.common.util.DateTimeUtils;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssessmentNonParticipantService {

    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AssessmentRoundRepository assessmentRoundRepository;
    private final AssessmentNonParticipantQueryRepository assessmentNonParticipantQueryRepository;
    private final NotificationSender notificationSender;

    public PageResponse<AssessmentNonParticipantResponse> getNonParticipants(Integer roundId, Pageable pageable) {
        AssessmentRound round = assessmentRoundRepository.findById(roundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND));

        return PageResponse.from(
                assessmentNonParticipantQueryRepository.findNonParticipants(roundId, round.getTargetCondition(), pageable));
    }

    /**
     * 요청 바디의 userIds를 그대로 신뢰하지 않고, GET 목록 조회와 동일한 기준(TargetConditionInterpreter
     * + submittedAt IS NULL)으로 다시 구한 실제 미응시자 집합과 교집합만 발송 대상으로 삼는다 —
     * 그래야 화면에 보이던 명단과 실제 발송 대상이 항상 일치한다.
     */
    public AssessmentNonParticipantNotifyResponse notify(Integer roundId, List<Integer> requestedUserIds) {
        AssessmentRound round = assessmentRoundRepository.findById(roundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND));

        List<Integer> nonParticipantUserIds =
                assessmentNonParticipantQueryRepository.findNonParticipantUserIds(roundId, round.getTargetCondition());

        List<Integer> targetUserIds = resolveTargetUserIds(nonParticipantUserIds, requestedUserIds);

        String title = "%s 진단 마감이 임박했습니다".formatted(round.getAssessmentName());
        String content = "'%s' 진단이 %s에 마감됩니다. 아직 응시하지 않으셨다면 서둘러 참여해주세요."
                .formatted(round.getAssessmentName(), DEADLINE_FORMATTER.format(DateTimeUtils.toKstOffsetDateTime(round.getEndsAt())));

        List<Integer> sentUserIds = new ArrayList<>();
        int failedCount = 0;
        for (Integer userId : targetUserIds) {
            try {
                notificationSender.send(new NotificationRequest(
                        userId, NotificationType.DEADLINE_IMMINENT, ModuleCode.COMPETENCY, title, content));
                sentUserIds.add(userId);
            } catch (Exception e) {
                failedCount++;
                log.warn("미응시자 알림 발송 실패 (assessmentRoundId={}, userId={})", roundId, userId, e);
            }
        }
        return new AssessmentNonParticipantNotifyResponse(sentUserIds, failedCount);
    }

    private List<Integer> resolveTargetUserIds(List<Integer> nonParticipantUserIds, List<Integer> requestedUserIds) {
        if (requestedUserIds == null) {
            return nonParticipantUserIds;
        }
        // null 원소가 섞여 와도(예: [1, null, 3]) 어차피 실제 미응시자 집합엔 없는 값이라
        // 교집합 전에 걸러내기만 하면 된다 — Set.copyOf는 null 원소가 있으면 NPE를 던진다.
        Set<Integer> requested = requestedUserIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return nonParticipantUserIds.stream().filter(requested::contains).toList();
    }
}
