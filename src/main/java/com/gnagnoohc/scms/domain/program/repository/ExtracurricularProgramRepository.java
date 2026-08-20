package com.gnagnoohc.scms.domain.program.repository;

import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface ExtracurricularProgramRepository extends JpaRepository<ExtracurricularProgram, Integer> {

    // ExtracurricularProgram 엔티티는 protected 기본 생성자만 있고 빌더/setter가 없어서
    // 애플리케이션 코드에서 인스턴스를 만들어 save()로 저장할 방법이 없다.
    // 그래서 엔티티 객체를 아예 생성하지 않는 native INSERT로 우회한다.
    @Query(value = """
        INSERT INTO extracurricular_program (
            file_group_id, operating_unit_code_id, program_type_code_id, competency_id,
            mileage_policy_id, manager_user_id, program_name, description,
            recruitment_starts_at, recruitment_ends_at, operation_starts_at, operation_ends_at,
            capacity, completion_rate, program_status, created_at, updated_at
        ) VALUES (
            :fileGroupId, :operatingUnitCodeId, :programTypeCodeId, :competencyId,
            :mileagePolicyId, :managerUserId, :programName, :description,
            :recruitmentStartsAt, :recruitmentEndsAt, :operationStartsAt, :operationEndsAt,
            :capacity, :completionRate, :programStatus, :now, :now
        )
        RETURNING program_id
        """, nativeQuery = true)
    // @Modifying을 붙이지 않는다 — @Modifying은 반환 타입을 영향받은 row 수로 고정시켜서
    // PostgreSQL의 RETURNING 절로 돌아오는 생성된 program_id 값을 받을 수 없게 된다.
    Integer insertProgram(@Param("fileGroupId") Integer fileGroupId,
                           @Param("operatingUnitCodeId") Integer operatingUnitCodeId,
                           @Param("programTypeCodeId") Integer programTypeCodeId,
                           @Param("competencyId") Integer competencyId,
                           @Param("mileagePolicyId") Integer mileagePolicyId,
                           @Param("managerUserId") Integer managerUserId,
                           @Param("programName") String programName,
                           @Param("description") String description,
                           @Param("recruitmentStartsAt") Instant recruitmentStartsAt,
                           @Param("recruitmentEndsAt") Instant recruitmentEndsAt,
                           @Param("operationStartsAt") Instant operationStartsAt,
                           @Param("operationEndsAt") Instant operationEndsAt,
                           @Param("capacity") Integer capacity,
                           @Param("completionRate") BigDecimal completionRate,
                           @Param("programStatus") String programStatus,
                           // created_at/updated_at 둘 다 이 값을 받는다.
                           // JPA auditing(@CreatedDate/@LastModifiedDate)은 엔티티 생명주기 이벤트에서만
                           // 동작하는데 native SQL은 그 경로를 타지 않으므로 서비스가 만든 시각을 직접 넣어야 한다.
                           @Param("now") Instant now);
}
