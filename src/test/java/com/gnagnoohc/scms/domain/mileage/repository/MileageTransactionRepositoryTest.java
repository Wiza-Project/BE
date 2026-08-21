package com.gnagnoohc.scms.domain.mileage.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MileageTransactionRepositoryTest {

    @Autowired
    private MileageTransactionRepository mileageTransactionRepository;

    @Test
    void findRecentTransactions_returnsEmptyListWhenStudentHasNoTransactions() {
        var result = mileageTransactionRepository.findRecentTransactions(
                1,
                PageRequest.of(0, 5));

        assertThat(result).isEmpty();
    }
}
