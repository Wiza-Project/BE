package com.gnagnoohc.scms;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class ScmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScmsApplication.class, args);
    }

    @Bean
    public CommandLineRunner initTestData(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. 현재 Spring Security 설정에 맞는 비밀번호 "1" 해시값 생성
            String encodedPassword = passwordEncoder.encode("1");

            // 2. 기존 FK 및 유저 데이터 정리
            jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN (10, 11, 12)");
            jdbcTemplate.update("DELETE FROM app_user WHERE user_id IN (10, 11, 12) OR university_no IN ('11111111', '55555555', '99999999')");

            // 3. 테스트 유저 3명 생성 (학생: 11111111, 교직원: 55555555, 센터담당: 99999999)
            String insertUserSql = """
                INSERT INTO app_user (
                    user_id, created_at, updated_at, academic_status, account_status, created_by,
                    email, failed_login_count, last_login_at, locked_at, password_hash,
                    phone, preferred_contact, university_no, user_name, user_type, department_code_id
                ) VALUES 
                    (10, NOW(), NOW(), 'ENROLLED', 'ACTIVE', NULL, 'student1@univ.ac.kr', 0, NOW(), NULL, ?, '010-1111-1111', 'EMAIL', '11111111', '김학생', 'STUDENT', NULL),
                    (11, NOW(), NOW(), NULL, 'ACTIVE', NULL, 'staff1@univ.ac.kr', 0, NOW(), NULL, ?, '010-5555-5555', 'EMAIL', '55555555', '이교직', 'STAFF', NULL),
                    (12, NOW(), NOW(), NULL, 'ACTIVE', NULL, 'staff2@univ.ac.kr', 0, NOW(), NULL, ?, '010-9999-9999', 'EMAIL', '99999999', '박센터', 'STAFF', NULL)
            """;
            jdbcTemplate.update(insertUserSql, encodedPassword, encodedPassword, encodedPassword);

            // 4. 권한 매핑 생성
            String insertRoleSql = """
                INSERT INTO user_role (user_id, role_code, granted_at) VALUES 
                    (10, 'ROLE_STUDENT', NOW()),
                    (11, 'ROLE_STAFF', NOW()),
                    (11, 'ROLE_ADMIN', NOW()),
                    (12, 'ROLE_STAFF', NOW())
            """;
            jdbcTemplate.update(insertRoleSql);

            System.out.println("=======================================================");
            System.out.println(">>> [DB 초기화 성공] 테스트 계정 3건 생성 완료");
            System.out.println(">>> 학생: 11111111 (PW: 1)");
            System.out.println(">>> 교직원: 55555555 (PW: 1)");
            System.out.println(">>> 교직원/센터: 99999999 (PW: 1)");
            System.out.println("=======================================================");
        };
    }

}
