package com.gnagnoohc.scms.domain.competency.support;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.querydsl.core.types.dsl.BooleanExpression;

import static com.gnagnoohc.scms.domain.user.entity.QAppUser.appUser;

/**
 * 핵심역량 진단 대상자 판정 기준 한 곳. 대상자 = user_type이 STUDENT이면서 학적상태가 '재학'인 학생이다.
 *
 * <p>집계(응시율·미응시자 명단·결과 통계)와 응시(시작·응답 저장·제출)가 각자 이 기준을 들고 있으면
 * 서로 어긋난다 — 실제로 응시 차단 없이 집계에서만 비재학생을 빼던 동안, 휴학·졸업 학생이 응시는
 * 할 수 있는데 그 기록은 분모에도 분자에도 안 잡히는 상태였다. 그래서 쿼리용 표현식과 단건 판정을
 * 같은 클래스에 둬서, 한쪽만 고치면 바로 눈에 띄게 한다.
 *
 * <p>academic_status가 실제로 쓰는 라벨은 재학/휴학/졸업/제적/자퇴 5개
 * (AcademicRecordQueryRepository.KNOWN_STATUSES와 동일). 이 중 '재학'만 대상자로 본다.
 */
public final class AssessmentTargetPolicy {

    private static final String STUDENT_USER_TYPE = "STUDENT";
    private static final String ENROLLED_ACADEMIC_STATUS = "재학";

    /**
     * 집계·목록 쿼리의 WHERE절에 그대로 넣는 대상자 조건. QAppUser 기본 인스턴스(appUser)를 참조하므로,
     * 쓰는 쪽 쿼리도 같은 별칭으로 app_user를 조인해야 한다(from(appUser) 또는 join(..., appUser)).
     */
    public static final BooleanExpression ENROLLED_STUDENT =
            appUser.userType.eq(STUDENT_USER_TYPE).and(appUser.academicStatus.eq(ENROLLED_ACADEMIC_STATUS));

    /**
     * 위 ENROLLED_STUDENT와 같은 판정을 엔티티 1건에 대해 메모리에서 수행한다.
     * academic_status는 nullable이라 값이 비어 있으면 대상자가 아니다(eq 비교가 NULL을 거르는 것과 동일).
     */
    public static boolean isEnrolledStudent(AppUser user) {
        return user != null
                && STUDENT_USER_TYPE.equals(user.getUserType())
                && ENROLLED_ACADEMIC_STATUS.equals(user.getAcademicStatus());
    }

    private AssessmentTargetPolicy() {
    }
}
