package com.gnagnoohc.scms.domain.counsel.event;

/**
 * 상담 제안 생성 트랜잭션 커밋 뒤 학생에게 인앱 알림을 보내기 위한 최소 이벤트다.
 * 점수, 결과 수준, 제안 내용, 학생 이름 같은 민감정보는 담지 않는다.
 */
public record CounselingProposalCreatedEvent(
        Integer proposalId,
        Integer studentId
) {
}
