package com.gnagnoohc.scms.global.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// YUN 로컬 전용
@Component
@Profile("local")
public class LocalDevDataSeeder implements CommandLineRunner {

    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public LocalDevDataSeeder(PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        String hash = passwordEncoder.encode("1");

        // 1. 기존 더미 데이터 정리
        jdbcTemplate.update("DELETE FROM user_role WHERE user_id IN (10, 11, 12)");
        jdbcTemplate.update("DELETE FROM app_user WHERE user_id IN (10, 11, 12) OR university_no IN ('11111111', '55555555', '99999999')");

        // 2. 학생 계정 (11111111 / PW: 1)
        jdbcTemplate.update(
                "INSERT INTO app_user (user_id, created_at, updated_at, academic_status, account_status, email, failed_login_count, last_login_at, password_hash, phone, preferred_contact, university_no, user_name, user_type, department_code_id) " +
                        "VALUES (10, NOW(), NOW(), 'ENROLLED', 'ACTIVE', 'student@univ.ac.kr', 0, NOW(), ?, '010-1111-1111', 'EMAIL', '11111111', '김학생', 'STUDENT', NULL)",
                hash
        );

        // 3. 교직원 1 (55555555 / PW: 1 - 학생역량센터 D100)
        jdbcTemplate.update(
                "INSERT INTO app_user (user_id, created_at, updated_at, account_status, email, failed_login_count, last_login_at, password_hash, phone, preferred_contact, university_no, user_name, user_type, department_code_id) " +
                        "VALUES (11, NOW(), NOW(), 'ACTIVE', 'staff1@univ.ac.kr', 0, NOW(), ?, '010-5555-5555', 'EMAIL', '55555555', '이교직', 'STAFF', " +
                        "(SELECT code_id FROM common_code WHERE code_group = 'DEPARTMENT' AND code = 'D100' LIMIT 1))",
                hash
        );

        // 4. 교직원 2 (99999999 / PW: 1 - 취창업지원과 D400)
        jdbcTemplate.update(
                "INSERT INTO app_user (user_id, created_at, updated_at, account_status, email, failed_login_count, last_login_at, password_hash, phone, preferred_contact, university_no, user_name, user_type, department_code_id) " +
                        "VALUES (12, NOW(), NOW(), 'ACTIVE', 'staff2@univ.ac.kr', 0, NOW(), ?, '010-9999-9999', 'EMAIL', '99999999', '박취창업', 'STAFF', " +
                        "(SELECT code_id FROM common_code WHERE code_group = 'DEPARTMENT' AND code = 'D400' LIMIT 1))",
                hash
        );

        // 5. 권한 매핑 주입
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_code, granted_at) VALUES (10, 'ROLE_STUDENT', NOW())");
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_code, granted_at) VALUES (11, 'ROLE_STAFF', NOW())");
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_code, granted_at) VALUES (11, 'ROLE_ADMIN', NOW())");
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_code, granted_at) VALUES (12, 'ROLE_STAFF', NOW())");

        System.out.println("====== [SCMS] 부서 매핑(D100/D400) 포함 테스트 계정 주입 완료 ======");
    }
}