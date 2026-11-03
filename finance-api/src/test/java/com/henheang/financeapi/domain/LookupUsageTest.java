package com.henheang.financeapi.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Luyra's describeUsage wording: only non-zero parts, singular/plural nouns. */
class LookupUsageTest {

    @Test
    void describesOnlyTheTablesThatHoldAReference() {
        assertThat(new LookupUsage(12, 1).describe())
                .isEqualTo("12 transactions, 1 recurring rule");
        assertThat(new LookupUsage(1, 0).describe()).isEqualTo("1 transaction");
        assertThat(new LookupUsage(0, 2).describe()).isEqualTo("2 recurring rules");
        assertThat(new LookupUsage(0, 0).describe()).isEmpty();
    }

    @Test
    void totalsEveryReference() {
        assertThat(new LookupUsage(3, 2).total()).isEqualTo(5);
        assertThat(new LookupUsage(0, 0).total()).isZero();
    }
}
