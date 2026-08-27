package com.gnagnoohc.scms.domain.mileage.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MileagePolicyTest {

    @Test
    void isApplicableOn_includesValidFromAndValidTo() {
        MileagePolicy policy = policy(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(policy.isApplicableOn(LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(policy.isApplicableOn(LocalDate.of(2026, 6, 30))).isTrue();
        assertThat(policy.isApplicableOn(LocalDate.of(2026, 12, 31))).isTrue();
    }

    @Test
    void isApplicableOn_excludesDatesOutsideThePeriod() {
        MileagePolicy policy = policy(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(policy.isApplicableOn(LocalDate.of(2025, 12, 31))).isFalse();
        assertThat(policy.isApplicableOn(LocalDate.of(2027, 1, 1))).isFalse();
    }

    @Test
    void isApplicableOn_treatsNullValidToAsOpenEnded() {
        MileagePolicy policy = policy(LocalDate.of(2026, 1, 1), null);

        assertThat(policy.isApplicableOn(LocalDate.of(2099, 12, 31))).isTrue();
    }

    @Test
    void isApplicableOn_returnsFalseForNullDateOrStartDate() {
        MileagePolicy withoutStartDate = policy(null, LocalDate.of(2026, 12, 31));

        assertThat(withoutStartDate.isApplicableOn(null)).isFalse();
        assertThat(withoutStartDate.isApplicableOn(LocalDate.of(2026, 6, 30))).isFalse();
    }

    private MileagePolicy policy(LocalDate validFrom, LocalDate validTo) {
        MileagePolicy policy = new MileagePolicy();
        ReflectionTestUtils.setField(policy, "validFrom", validFrom);
        ReflectionTestUtils.setField(policy, "validTo", validTo);
        return policy;
    }
}
