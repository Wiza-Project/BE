package com.gnagnoohc.scms.domain.career.repository;

import com.gnagnoohc.scms.domain.career.entity.CompanyAccount;
import org.springframework.data.jpa.repository.JpaRepository;

// TODO: JobPosting 채용공고 내 기업 넘버링 처리를 위해 기업계정 레파지토리 인터페이스 생성, 이후 내용 추가 필요
public interface CompanyAccountRepository extends JpaRepository<CompanyAccount, Integer> {
}