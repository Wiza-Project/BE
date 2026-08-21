package com.gnagnoohc.scms.domain.competency.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentQuestionEditRequest;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentQuestionResponse;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentQuestionUploadResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentQuestion;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentResponseRepository;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class AssessmentQuestionService {

    // 엑셀 원본(핵심역량 및 하위역량.xlsx, B2:E92)의 고정 컬럼 위치. 하위역량(C)·문항번호(D)는 읽지 않고 무시한다 —
    // 문항번호는 하위역량 안에서 1~3으로 리셋돼 있어 그대로 쓸 수 없고, 하위역량은 스코프 밖(미개발)이기 때문.
    private static final int COMPETENCY_NAME_COLUMN = 1; // B: 상위역량
    private static final int QUESTION_TEXT_COLUMN = 4;   // E: 평가문항
    private static final int HEADER_ROW_INDEX = 0;

    private static final String[] LIKERT_LABELS = {"매우 그렇지 않다", "그렇지 않다", "보통이다", "그렇다", "매우 그렇다"};
    private static final int RECOMMENDED_MAX_PER_COMPETENCY = 15;

    private final AssessmentQuestionRepository assessmentQuestionRepository;
    private final AssessmentResponseRepository assessmentResponseRepository;
    private final CompetencyRepository competencyRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssessmentQuestionUploadResponse uploadQuestions(MultipartFile file, Integer staffId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "업로드된 파일이 비어 있습니다.");
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            return processWorkbook(workbook, staffId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "엑셀 파일을 읽을 수 없습니다. 형식을 확인해주세요.");
        }
    }

    private AssessmentQuestionUploadResponse processWorkbook(Workbook workbook, Integer staffId) {
        Sheet sheet = workbook.getSheetAt(0);
        DataFormatter formatter = new DataFormatter();
        JsonNode defaultResponseOptions = buildDefaultResponseOptions();

        // 같은 역량명을 매번 다시 조회하지 않도록 시트 내에서 캐시
        Map<String, Competency> competencyByName = new HashMap<>();
        Map<Integer, Integer> newCountByCompetencyId = new HashMap<>();
        List<AssessmentQuestionUploadResponse.RowFailure> failures = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;

        for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }

            String competencyName = formatter.formatCellValue(row.getCell(COMPETENCY_NAME_COLUMN)).trim();
            String questionText = formatter.formatCellValue(row.getCell(QUESTION_TEXT_COLUMN)).trim();
            if (competencyName.isEmpty() && questionText.isEmpty()) {
                continue; // 완전히 빈 행은 집계에서도 제외
            }

            totalRows++;
            int excelRowNo = rowIndex + 1;

            if (questionText.isEmpty()) {
                failures.add(new AssessmentQuestionUploadResponse.RowFailure(excelRowNo, "평가문항 내용이 비어 있습니다."));
                continue;
            }

            Competency competency = competencyByName.computeIfAbsent(competencyName, name ->
                    competencyRepository.findByCompetencyNameAndParentCompetencyIsNull(name).orElse(null));
            if (competency == null) {
                failures.add(new AssessmentQuestionUploadResponse.RowFailure(excelRowNo, "존재하지 않는 핵심역량입니다: " + competencyName));
                continue;
            }

            AssessmentQuestion question = AssessmentQuestion.createFromUpload(
                    competency, questionText, defaultResponseOptions, staffId);
            assessmentQuestionRepository.save(question);
            successCount++;
            newCountByCompetencyId.merge(competency.getCompetencyId(), 1, Integer::sum);
        }

        List<String> warnings = buildOverCapacityWarnings(competencyByName, newCountByCompetencyId);
        return new AssessmentQuestionUploadResponse(totalRows, successCount, failures.size(), failures, warnings);
    }

    // 역량당 문항 15건 초과 시 경고 — 이번 업로드분만이 아니라 기존 활성 문항까지 합친 실제 총량 기준.
    // countByCompetency_...는 같은 트랜잭션 안에서도 직전 save()가 flush된 뒤 값을 읽는다(JPA 기본 FlushMode.AUTO).
    private List<String> buildOverCapacityWarnings(Map<String, Competency> competencyByName,
                                                     Map<Integer, Integer> newCountByCompetencyId) {
        Map<Integer, String> nameByCompetencyId = new HashMap<>();
        for (Competency competency : competencyByName.values()) {
            if (competency != null) {
                nameByCompetencyId.put(competency.getCompetencyId(), competency.getCompetencyName());
            }
        }

        List<String> warnings = new ArrayList<>();
        for (Integer competencyId : newCountByCompetencyId.keySet()) {
            long totalCount = assessmentQuestionRepository.countByCompetency_CompetencyIdAndActiveTrue(competencyId);
            if (totalCount > RECOMMENDED_MAX_PER_COMPETENCY) {
                warnings.add(nameByCompetencyId.get(competencyId) + ": 문항 " + totalCount + "건 (권장 "
                        + RECOMMENDED_MAX_PER_COMPETENCY + "건 초과)");
            }
        }
        return warnings;
    }

    public AssessmentQuestionResponse getQuestion(Integer questionId) {
        AssessmentQuestion question = assessmentQuestionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_QUESTION_NOT_FOUND));
        return AssessmentQuestionResponse.from(question);
    }

    public List<AssessmentQuestionResponse> getQuestionsByCompetency(Integer competencyId) {
        return assessmentQuestionRepository.findByCompetency_CompetencyIdAndActiveTrueOrderByQuestionIdAsc(competencyId)
                .stream()
                .map(AssessmentQuestionResponse::from)
                .toList();
    }

    // 이미 응시(응답 저장)가 시작된 문항은 제자리 수정 대신 새 버전으로 대체하고 이전 버전은 비활성 처리한다.
    // 아직 응시 전인 문항은 제자리 수정을 허용한다.
    public AssessmentQuestionResponse editQuestion(Integer questionId, AssessmentQuestionEditRequest request, Integer staffId) {
        AssessmentQuestion question = assessmentQuestionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_QUESTION_NOT_FOUND));
        if (!question.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_QUESTION_NOT_EDITABLE);
        }

        JsonNode responseOptions = objectMapper.valueToTree(request.responseOptions());
        boolean alreadyStarted = assessmentResponseRepository.existsByQuestion_QuestionId(questionId);

        if (!alreadyStarted) {
            question.editInPlace(request.questionText(), responseOptions, request.reverse());
            return AssessmentQuestionResponse.from(question);
        }

        AssessmentQuestion newVersion = AssessmentQuestion.createNewVersion(
                question, request.questionText(), responseOptions, request.reverse(), staffId);
        assessmentQuestionRepository.save(newVersion);
        question.deactivate();
        return AssessmentQuestionResponse.from(newVersion);
    }

    private JsonNode buildDefaultResponseOptions() {
        ArrayNode options = objectMapper.createArrayNode();
        for (int i = 0; i < LIKERT_LABELS.length; i++) {
            ObjectNode option = objectMapper.createObjectNode();
            option.put("value", i + 1);
            option.put("label", LIKERT_LABELS[i]);
            options.add(option);
        }
        return options;
    }
}
