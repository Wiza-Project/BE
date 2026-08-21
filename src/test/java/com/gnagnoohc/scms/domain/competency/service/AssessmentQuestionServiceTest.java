package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentQuestionUploadResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentQuestion;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentQuestionServiceTest {

    @Mock
    AssessmentQuestionRepository assessmentQuestionRepository;

    @Mock
    CompetencyRepository competencyRepository;

    @InjectMocks
    AssessmentQuestionService assessmentQuestionService;

    private static MockMultipartFile buildExcel(String[][] rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            sheet.createRow(0); // 헤더 행(내용은 사용하지 않음)
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(1).setCellValue(rows[i][0]); // B: 상위역량
                row.createCell(4).setCellValue(rows[i][1]); // E: 평가문항
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new MockMultipartFile("file", "questions.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void uploadQuestions_mapsRowsAndSavesFixedLikertOptions() {
        Competency selfManagement = Competency.createTop("C100", "자기관리 역량", null, null, 100, 1);
        when(competencyRepository.findByCompetencyNameAndParentCompetencyIsNull("자기관리 역량"))
                .thenReturn(Optional.of(selfManagement));
        when(assessmentQuestionRepository.save(any(AssessmentQuestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(assessmentQuestionRepository.countByCompetency_CompetencyIdAndActiveTrue(any()))
                .thenReturn(2L);

        MockMultipartFile file = buildExcel(new String[][]{
                {"자기관리 역량", "나는 목표를 세우고 실천한다."},
                {"자기관리 역량", "나는 시간을 계획적으로 관리한다."}
        });

        AssessmentQuestionUploadResponse response = assessmentQuestionService.uploadQuestions(file, 1);

        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.successCount()).isEqualTo(2);
        assertThat(response.failureCount()).isZero();
        assertThat(response.warnings()).isEmpty();

        var captor = org.mockito.ArgumentCaptor.forClass(AssessmentQuestion.class);
        org.mockito.Mockito.verify(assessmentQuestionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        AssessmentQuestion saved = captor.getAllValues().get(0);
        assertThat(saved.isReverse()).isFalse();
        assertThat(saved.getResponseOptions().get(0).get("label").asText()).isEqualTo("매우 그렇지 않다");
        assertThat(saved.getResponseOptions().get(4).get("label").asText()).isEqualTo("매우 그렇다");
        assertThat(saved.getResponseOptions()).hasSize(5);
    }

    @Test
    void uploadQuestions_whenCompetencyNameUnmapped_recordsRowFailure() {
        when(competencyRepository.findByCompetencyNameAndParentCompetencyIsNull("존재하지않는역량"))
                .thenReturn(Optional.empty());

        MockMultipartFile file = buildExcel(new String[][]{
                {"존재하지않는역량", "평가문항 내용"}
        });

        AssessmentQuestionUploadResponse response = assessmentQuestionService.uploadQuestions(file, 1);

        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.successCount()).isZero();
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.failures().get(0).reason()).contains("존재하지 않는 핵심역량");
    }

    @Test
    void uploadQuestions_whenQuestionTextBlank_recordsRowFailure() {
        MockMultipartFile file = buildExcel(new String[][]{
                {"자기관리 역량", ""}
        });

        AssessmentQuestionUploadResponse response = assessmentQuestionService.uploadQuestions(file, 1);

        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.failures().get(0).reason()).contains("평가문항 내용이 비어 있습니다");
    }

    @Test
    void uploadQuestions_whenCompetencyExceedsRecommendedCount_addsWarning() {
        Competency selfManagement = Competency.createTop("C100", "자기관리 역량", null, null, 100, 1);
        when(competencyRepository.findByCompetencyNameAndParentCompetencyIsNull("자기관리 역량"))
                .thenReturn(Optional.of(selfManagement));
        when(assessmentQuestionRepository.save(any(AssessmentQuestion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(assessmentQuestionRepository.countByCompetency_CompetencyIdAndActiveTrue(any()))
                .thenReturn(16L);

        MockMultipartFile file = buildExcel(new String[][]{
                {"자기관리 역량", "문항"}
        });

        AssessmentQuestionUploadResponse response = assessmentQuestionService.uploadQuestions(file, 1);

        assertThat(response.warnings()).hasSize(1);
        assertThat(response.warnings().get(0)).contains("자기관리 역량").contains("16건");
    }

    @Test
    void uploadQuestions_whenFileEmpty_throwsFileUploadFailed() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        assertThatThrownBy(() -> assessmentQuestionService.uploadQuestions(emptyFile, 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_UPLOAD_FAILED);
    }

    @Test
    void uploadQuestions_whenNotAnExcelFile_throwsFileUploadFailed() {
        MockMultipartFile notExcel = new MockMultipartFile("file", "questions.txt",
                "text/plain", "이건 엑셀이 아닙니다".getBytes());

        assertThatThrownBy(() -> assessmentQuestionService.uploadQuestions(notExcel, 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_UPLOAD_FAILED);
    }
}
