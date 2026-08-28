package com.gnagnoohc.scms.domain.career.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * <p>
 * [1. 학생 벡터 로드]
 * student_profile.embedding_vector (사전 연산 완료된 벡터)
 * RDBMS로 지역과 고용형태는 필터링 가능하므로 벡터화 제외
 * │
 * ▼
 * [2. pgvector 코사인 연산 (NCS 매칭)]
 * student_profile <=> ncs_standard
 * 학생과 가장 유사한 상위 NCS 코드(Top-K) 추출
 * │
 * ▼
 * [3. RDBMS 조인 및 정형 조건 필터링]
 * - job_posting.ncs_code IN (:topNcsCodes)
 * - job_posting.region_code = job_preference.preferred_region_code
 * - job_posting.employment_type = job_preference.employment_type
 * 최종 맞춤 채용공고 목록 서빙 (student_job_relation)
 * <p>
 * ---------------------------------------------------------------------------
 * <p>
 * [학생 UI 선택]
 * │
 * ▼ (CommonCode code_id 전송)
 * [JobPreference] ───────> CommonCode (FK 저장: 화면 조회/폼 수정용)
 * │
 * │ (저장 트랜잭션 시 CommonCode.code 값으로 매핑)
 * ▼
 * [NcsStandard]   ───────> 8자리 코드로 embedding_vector 추출
 * │
 * ▼
 * [StudentProfile] ──────> embedding_vector 저장 (pgvector 유사도 매칭 전용)
 *
 *
 */
@Entity
@Getter
@Table(name = "student_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentProfile {

    @Id
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "student_grade", nullable = true, length = 255)
    private String studentGrade;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "embedding_vector", nullable = true, columnDefinition = "vector")
    private float[] embeddingVector;

    @Builder
    public StudentProfile(AppUser user, String studentGrade, float[] embeddingVector) {
        this.user = user;
        this.studentGrade = studentGrade;
        this.embeddingVector = embeddingVector;
    }

    /**
     * 학생 대표 임베딩 벡터 갱신 도메인 메서드
     */
    public void updateEmbeddingVector(float[] embeddingVector) {
        this.embeddingVector = embeddingVector;
    }

    /**
     * 복수 직무 희망 시 벡터 평균 연산 로직 (현재 단일 희망직무 연동으로 미사용 처리, 추후 3뎁스 확장)
     */
//    public void updateAverageEmbeddingVector(List<float[]> vectors) {
//        if (vectors == null || vectors.isEmpty()) {
//            this.embeddingVector = null;
//            return;
//        }
//
//        int dimension = vectors.get(0).length;
//        float[] sumVector = new float[dimension];
//
//        for (float[] vector : vectors) {
//            if (vector == null || vector.length != dimension) {
//                continue;
//            }
//            for (int i = 0; i < dimension; i++) {
//                sumVector[i] += vector[i];
//            }
//        }
//
//        for (int i = 0; i < dimension; i++) {
//            sumVector[i] /= vectors.size();
//        }
//
//        this.embeddingVector = sumVector;
//    }
}
