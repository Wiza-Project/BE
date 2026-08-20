package com.gnagnoohc.scms.domain.program.repository;

import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface ExtracurricularProgramRepository extends JpaRepository<ExtracurricularProgram, Integer> {

    // ── 여기부터 "등록(Create)" 기능 ──────────────────────────────────────────────
    //
    // ExtracurricularProgram 엔티티는 protected 기본 생성자만 있고 빌더/setter가 없어서
    // 애플리케이션 코드에서 인스턴스를 만들어 save()로 저장할 방법이 없다(생성자 안에서만 값을 채울 수 있는데,
    // 그 생성자조차 외부에서 못 부르게 protected로 막혀 있다).
    // 그래서 엔티티 객체를 아예 생성하지 않고, DB에 직접 SQL을 보내는 native INSERT로 우회한다.
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
    // 이 메서드는 몇 개의 row가 바뀌었는지가 아니라 "새로 만들어진 program_id 값"이 필요하기 때문에
    // @Modifying을 붙이지 않는다 — @Modifying은 반환 타입을 영향받은 row 수(int)로 고정시켜버려서,
    // PostgreSQL의 RETURNING 절로 돌아오는 값을 받을 수 없게 된다.
    Integer insertProgram(// 첨부파일 그룹 id. nullable이라 그대로 null이 들어올 수도 있다.
                           @Param("fileGroupId") Integer fileGroupId,
                           // 운영 단위 코드 id. DB에 없는 값이 들어오면 FK 제약 위반 예외가 발생한다(아래 resolveForeignKeyViolation 참고).
                           @Param("operatingUnitCodeId") Integer operatingUnitCodeId,
                           // 프로그램 유형(분류) 코드 id. 마찬가지로 FK 참조.
                           @Param("programTypeCodeId") Integer programTypeCodeId,
                           // 핵심역량 id. 마찬가지로 FK 참조.
                           @Param("competencyId") Integer competencyId,
                           // 마일리지 정책 id. nullable이라 정책 없이 등록되는 프로그램도 있을 수 있다.
                           @Param("mileagePolicyId") Integer mileagePolicyId,
                           // 이 프로그램을 등록하는 담당자(로그인한 사용자)의 id. 요청 바디가 아니라
                           // 서비스 계층에서 인증 정보로부터 직접 뽑아 넘겨준다(클라이언트가 위조 불가).
                           @Param("managerUserId") Integer managerUserId,
                           // 프로그램명.
                           @Param("programName") String programName,
                           // 프로그램 설명.
                           @Param("description") String description,
                           // 모집 시작 시각.
                           @Param("recruitmentStartsAt") Instant recruitmentStartsAt,
                           // 모집 종료 시각.
                           @Param("recruitmentEndsAt") Instant recruitmentEndsAt,
                           // 운영 시작 시각.
                           @Param("operationStartsAt") Instant operationStartsAt,
                           // 운영 종료 시각.
                           @Param("operationEndsAt") Instant operationEndsAt,
                           // 정원.
                           @Param("capacity") Integer capacity,
                           // 이수 기준 출석률(%). 서비스에서 null이면 기본값(80)으로 이미 채워서 넘어온다.
                           @Param("completionRate") BigDecimal completionRate,
                           // 등록 시 프로그램 상태. 서비스 계층이 항상 "DRAFT" 문자열을 고정으로 넘긴다(클라이언트가 정할 수 없음).
                           @Param("programStatus") String programStatus,
                           // created_at/updated_at 둘 다 이 값을 받는다.
                           // JPA auditing(@CreatedDate/@LastModifiedDate)은 엔티티 생명주기 이벤트에서만
                           // 동작하는데 native SQL은 그 경로를 타지 않으므로 서비스가 만든 시각을 직접 넣어야 한다.
                           @Param("now") Instant now);

    // ── 여기부터 "수정(Update)" 기능 ──────────────────────────────────────────────
    //
    // insertProgram과 마찬가지 이유(엔티티에 setter/빌더가 없음)로,
    // findById로 엔티티를 읽어서 값을 바꾸고 save()하는 방식(JPA dirty checking)을 쓸 수 없다.
    // 그래서 수정도 등록처럼 native UPDATE 쿼리로 직접 DB row를 갱신한다.
    //
    // WHERE 절에 "program_status = 'DRAFT'" 조건을 함께 걸어둔 이유:
    //   서비스 계층(ProgramService.update)에서 "지금 이 프로그램이 DRAFT 상태인지"를 먼저 확인하고,
    //   그 다음에 이 UPDATE 쿼리를 실행한다. 그런데 그 "확인"과 "실제 UPDATE 실행" 사이의 아주 짧은 시간에
    //   다른 요청이 먼저 끼어들어 상태를 바꿔버릴 수도 있다(이런 상황을 경쟁 조건, race condition이라고 부른다).
    //   그래서 UPDATE 문 자체에도 조건을 걸어두면, 그 사이에 상태가 바뀌었을 경우 이 UPDATE는
    //   0개의 row에만 영향을 주고 아무것도 바꾸지 않는다. 서비스 계층은 영향받은 row 수(0인지 아닌지)를 보고
    //   "역시 수정할 수 없는 상태였구나"를 알아챌 수 있다.
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE extracurricular_program
        SET file_group_id = :fileGroupId,
            operating_unit_code_id = :operatingUnitCodeId,
            program_type_code_id = :programTypeCodeId,
            competency_id = :competencyId,
            mileage_policy_id = :mileagePolicyId,
            program_name = :programName,
            description = :description,
            recruitment_starts_at = :recruitmentStartsAt,
            recruitment_ends_at = :recruitmentEndsAt,
            operation_starts_at = :operationStartsAt,
            operation_ends_at = :operationEndsAt,
            capacity = :capacity,
            completion_rate = :completionRate,
            updated_by = :updatedBy,
            updated_at = :now
        WHERE program_id = :programId
          AND program_status = 'DRAFT'
        """, nativeQuery = true)
    // @Modifying을 붙였다 — UPDATE/DELETE 쿼리는 새로 생성된 값을 돌려주는 게 아니라
    // "몇 개의 row가 실제로 바뀌었는지"만 알면 되므로, 반환 타입을 int(영향받은 row 수)로 받는다.
    // clearAutomatically = true는, 같은 트랜잭션 안에서 findById로 미리 읽어와 영속성 컨텍스트에
    // 남아있는 예전 program 엔티티 캐시를, native UPDATE 실행 이후 깨끗하게 비워서
    // 혹시 이후 코드가 그 프로그램을 다시 조회할 때 낡은(stale) 값을 보지 않도록 방지한다.
    int updateProgram(@Param("programId") Integer programId,
                       @Param("fileGroupId") Integer fileGroupId,
                       @Param("operatingUnitCodeId") Integer operatingUnitCodeId,
                       @Param("programTypeCodeId") Integer programTypeCodeId,
                       @Param("competencyId") Integer competencyId,
                       @Param("mileagePolicyId") Integer mileagePolicyId,
                       @Param("programName") String programName,
                       @Param("description") String description,
                       @Param("recruitmentStartsAt") Instant recruitmentStartsAt,
                       @Param("recruitmentEndsAt") Instant recruitmentEndsAt,
                       @Param("operationStartsAt") Instant operationStartsAt,
                       @Param("operationEndsAt") Instant operationEndsAt,
                       @Param("capacity") Integer capacity,
                       @Param("completionRate") BigDecimal completionRate,
                       // 마지막으로 수정한 사람의 user_id. 클라이언트가 보낸 값이 아니라
                       // 서비스 계층에서 "지금 로그인한 사용자"의 id를 그대로 전달한다.
                       @Param("updatedBy") Integer updatedBy,
                       // 이번 수정이 반영된 시각. updated_at 컬럼도 native 쿼리라 auditing을 안 타므로 직접 넣어야 한다.
                       @Param("now") Instant now);
}
