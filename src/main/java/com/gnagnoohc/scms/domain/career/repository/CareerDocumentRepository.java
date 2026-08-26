package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.entity.CareerDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 취창업 문서(자기소개서/포트폴리오) 데이터 접근 계층 (Repository)
 */
public interface CareerDocumentRepository extends JpaRepository<CareerDocument, Integer> {

    /**
     * 학생 본인 소유 + 문서유형이 일치하는 문서 단건을 조회한다.
     * 소유권이 없거나 존재하지 않는 문서는 동일하게 빈 Optional을 반환한다 (소유권 비노출).
     */
    @Query("SELECT cd FROM CareerDocument cd JOIN FETCH cd.student " +
            "WHERE cd.careerDocumentId = :careerDocumentId AND cd.student.userId = :studentUserId AND cd.documentType = :documentType")
    Optional<CareerDocument> findOwnedDocument(@Param("careerDocumentId") Integer careerDocumentId,
                                                @Param("studentUserId") Integer studentUserId,
                                                @Param("documentType") String documentType);

    Page<CareerDocument> findByStudent_UserIdAndDocumentType(Integer studentUserId, String documentType, Pageable pageable);

    /** 학생·문서유형 기준 최신(가장 큰 버전 번호) 문서를 조회한다. 다음 버전 번호 채번에도 사용한다. */
    Optional<CareerDocument> findTopByStudent_UserIdAndDocumentTypeOrderByVersionNoDesc(Integer studentUserId, String documentType);
}
