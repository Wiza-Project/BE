package com.gnagnoohc.scms.domain.competency.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.gnagnoohc.scms.domain.academic.entity.QStudentAcademicDetail.studentAcademicDetail;

// assessment_round.target_condition(JSONB) 해석기. 응시율·미응시자 조회·백분위 산출 배치가
// 각자 이 JSON을 파싱하면 세 곳의 대상자 판정이 서로 어긋날 수 있어 여기 한 곳으로 모았다.
//
// 회차 등록 화면이 실제로 저장하는 키는 grades(학년 배열)·majorCodeIds(학과 공통코드 ID
// 배열, AcademicRecordQueryRepository.majorCodeIdEq와 같은 기준)다. colleges(단과대)는
// 학적 데이터에 단과대 계층이 없어(학과 공통코드에 상위 그룹이 없음) 해석할 수 없으므로,
// 조건을 조용히 무시해 잘못된 집계를 내보내는 대신 예외로 실패시킨다.
//
// student_academic_detail은 모든 학생에게 있는 게 아니라 LEFT JOIN 대상이므로, 조건을 하나라도 걸면
// 학적 상세가 없는 학생은 검증 불가능하다는 의미로 자동 제외된다(의도된 동작).
@Component
public class TargetConditionInterpreter {

    private static final String KEY_GRADES = "grades";
    private static final String KEY_MAJOR_CODE_IDS = "majorCodeIds";
    private static final String KEY_COLLEGES = "colleges";

    // null이면 "전체 학생 대상"이라 걸 조건이 없다는 뜻으로 null을 반환한다(QueryDSL은 where절의 null을 무시).
    public BooleanExpression toPredicate(JsonNode targetCondition) {
        if (targetCondition == null || targetCondition.isNull()) {
            return null;
        }
        if (targetCondition.hasNonNull(KEY_COLLEGES)) {
            throw new BusinessException(ErrorCode.ASSESSMENT_TARGET_CONDITION_UNSUPPORTED);
        }

        BooleanExpression predicate = null;
        predicate = and(predicate, gradeIn(targetCondition.get(KEY_GRADES)));
        predicate = and(predicate, majorCodeIdIn(targetCondition.get(KEY_MAJOR_CODE_IDS)));
        return predicate;
    }

    // grade는 DB CHECK 제약(1~4)이 있어 범위 밖 값과 일치하는 학생은 존재할 수 없다. 조건이
    // 아예 없는 것과 범위 밖 값이 섞여 들어온 것을 다르게 취급해야 하므로, 하나라도 범위를
    // 벗어나면 조건을 빼지 않고 항상 불일치로 고정한다(AcademicRecordQueryRepository.gradeEq와 동일한 근거).
    private BooleanExpression gradeIn(JsonNode gradesNode) {
        if (gradesNode == null || gradesNode.isNull() || !gradesNode.isArray() || gradesNode.isEmpty()) {
            return null;
        }
        List<Short> grades = new ArrayList<>();
        for (JsonNode gradeNode : gradesNode) {
            int grade = gradeNode.asInt();
            if (grade < 1 || grade > 4) {
                return Expressions.FALSE;
            }
            grades.add((short) grade);
        }
        return studentAcademicDetail.grade.in(grades);
    }

    // 학과는 라벨이 아니라 공통코드 ID로 비교한다 — AcademicRecordQueryRepository.majorCodeIdEq와
    // 같은 기준. 라벨 문자열 비교는 학과명 표기가 조금만 달라도(공백·축약형 등) 조용히 매칭 실패한다.
    private BooleanExpression majorCodeIdIn(JsonNode majorCodeIdsNode) {
        if (majorCodeIdsNode == null || majorCodeIdsNode.isNull() || !majorCodeIdsNode.isArray() || majorCodeIdsNode.isEmpty()) {
            return null;
        }
        List<Integer> ids = new ArrayList<>();
        majorCodeIdsNode.forEach(node -> ids.add(node.asInt()));
        return studentAcademicDetail.majorCode.codeId.in(ids);
    }

    private BooleanExpression and(BooleanExpression base, BooleanExpression addition) {
        if (addition == null) {
            return base;
        }
        return base == null ? addition : base.and(addition);
    }
}
