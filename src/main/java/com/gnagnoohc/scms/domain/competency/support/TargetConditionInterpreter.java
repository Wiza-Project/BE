package com.gnagnoohc.scms.domain.competency.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.gnagnoohc.scms.domain.academic.entity.QStudentAcademicDetail.studentAcademicDetail;

// assessment_round.target_condition(JSONB) 해석기. 응시율·미응시자 조회·백분위 산출 배치가
// 각자 이 JSON을 파싱하면 세 곳의 대상자 판정이 서로 어긋날 수 있어 여기 한 곳으로 모았다.
//
// 인식 키는 grades(학년 배열)·majorCodeIds(학과 공통코드 ID 배열,
// AcademicRecordQueryRepository.majorCodeIdEq와 같은 기준) 둘뿐이다. 그 외 키는 조용히
// 무시하지 않고 예외로 실패시킨다 — 무시하면 그 조건이 안 걸린 채로 대상자가 전체 학생으로 넓어진다.
//
// 형태 판정(isValidShape/hasUnrecognizedKey)은 AssessmentRoundService(등록·수정)와 여기(저장된
// 값 해석) 양쪽에서 재사용한다 — 판정을 두 곳에 따로 두면 등록은 통과했는데 조회는 실패하는 식으로
// 어긋날 수 있어서, 호출부는 실패를 자기 상황의 에러코드로만 변환한다.
//
// student_academic_detail은 모든 학생에게 있는 게 아니라 LEFT JOIN 대상이므로, 조건을 하나라도 걸면
// 학적 상세가 없는 학생은 검증 불가능하다는 의미로 자동 제외된다(의도된 동작).
@Component
public class TargetConditionInterpreter {

    private static final String KEY_GRADES = "grades";
    private static final String KEY_MAJOR_CODE_IDS = "majorCodeIds";
    private static final Set<String> RECOGNIZED_KEYS = Set.of(KEY_GRADES, KEY_MAJOR_CODE_IDS);

    // null이면 "전체 학생 대상"이라 걸 조건이 없다는 뜻으로 null을 반환한다(QueryDSL은 where절의 null을 무시).
    public BooleanExpression toPredicate(JsonNode targetCondition) {
        if (targetCondition == null || targetCondition.isNull()) {
            return null;
        }
        if (hasUnrecognizedKey(targetCondition)) {
            throw new BusinessException(ErrorCode.ASSESSMENT_TARGET_CONDITION_UNSUPPORTED);
        }
        if (!isValidShape(targetCondition)) {
            throw new BusinessException(ErrorCode.ASSESSMENT_TARGET_CONDITION_INVALID_FORMAT);
        }

        BooleanExpression predicate = null;
        predicate = and(predicate, gradeIn(targetCondition.get(KEY_GRADES)));
        predicate = and(predicate, majorCodeIdIn(targetCondition.get(KEY_MAJOR_CODE_IDS)));
        return predicate;
    }

    // grades/majorCodeIds 둘 다 인식하는 키의 전부다. 여기 없는 키가 하나라도 있으면 해석기가
    // 그 조건을 그냥 못 본 척 넘기는 대신 실패시켜야 한다(위 클래스 주석 참고).
    public boolean hasUnrecognizedKey(JsonNode targetCondition) {
        if (targetCondition == null || targetCondition.isNull()) {
            return false;
        }
        Iterator<String> fieldNames = targetCondition.fieldNames();
        while (fieldNames.hasNext()) {
            if (!RECOGNIZED_KEYS.contains(fieldNames.next())) {
                return true;
            }
        }
        return false;
    }

    // grades/majorCodeIds가 존재한다면 배열이어야 하고 원소는 전부 int로 안전하게 변환 가능해야
    // 한다. asInt()는 true→1, 4000.7→4000처럼 정수가 아닌 값도 그럴듯한 숫자로 바꿔버리고,
    // isIntegralNumber()만으로는 4294971296(2^32+4000)처럼 long 범위 정수가 int로 캐스팅되며
    // 4000으로 오버플로우되는 것도 못 막는다 — 그래서 canConvertToInt()로 범위까지 확인한다.
    // FE(회차 등록 화면)는 선택이 없으면 키 자체를 안 보내고 targetCondition을 null로 보낸다
    // (buildTargetCondition: fGrades.length/fDepts.length 확인 후에만 키를 채움) — 빈 배열이
    // 실제로 올 일이 없어 조건 생략으로 처리해도 무해하다.
    public boolean isValidShape(JsonNode targetCondition) {
        if (targetCondition == null || targetCondition.isNull()) {
            return true;
        }
        // 최상위가 object가 아니면(배열·스칼라 등) fieldNames()/get()이 각각 빈 iterator/null을
        // 반환해 아래 검사를 전부 통과해버린다 — 등록 API는 DTO 타입(Map<String, Object>)이 막아
        // 이 경로로 못 들어오지만, 이 해석기 자체가 그 게이트에만 기대면 안 되므로 여기서도 막는다.
        if (!targetCondition.isObject()) {
            return false;
        }
        return isValidArrayOfIntegers(targetCondition.get(KEY_GRADES))
                && isValidArrayOfIntegers(targetCondition.get(KEY_MAJOR_CODE_IDS));
    }

    private boolean isValidArrayOfIntegers(JsonNode node) {
        if (node == null || node.isNull()) {
            return true;
        }
        if (!node.isArray()) {
            return false;
        }
        for (JsonNode element : node) {
            // canConvertToInt()만으로는 4000.7 같은 소수를 못 거른다(범위 안에 들어오면 통과) —
            // isIntegralNumber()로 소수를, canConvertToInt()로 int 범위 초과를 각각 막아야 한다.
            if (!element.isIntegralNumber() || !element.canConvertToInt()) {
                return false;
            }
        }
        return true;
    }

    // grade는 DB CHECK 제약(1~4)이 있어 범위 밖 값과 일치하는 학생은 존재할 수 없다. 조건이
    // 아예 없는 것과 범위 밖 값이 섞여 들어온 것을 다르게 취급해야 하므로, 하나라도 범위를
    // 벗어나면 조건을 빼지 않고 항상 불일치로 고정한다(AcademicRecordQueryRepository.gradeEq와 동일한 근거).
    // 배열 형태·원소 타입은 isValidShape가 이미 보장하므로 여기서는 다시 검사하지 않는다.
    private BooleanExpression gradeIn(JsonNode gradesNode) {
        if (gradesNode == null || gradesNode.isNull() || gradesNode.isEmpty()) {
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
        if (majorCodeIdsNode == null || majorCodeIdsNode.isNull() || majorCodeIdsNode.isEmpty()) {
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
