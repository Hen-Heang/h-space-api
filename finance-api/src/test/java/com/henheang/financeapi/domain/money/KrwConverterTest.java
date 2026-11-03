package com.henheang.financeapi.domain.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.henheang.financeapi.domain.money.KrwConverter.ResolvedAmounts;
import com.henheang.securityapi.exception.BadRequestException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Golden cases for the one place transaction amounts are resolved (decision D-7). */
class KrwConverterTest {

    private static BigDecimal bd(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static ResolvedAmounts usd(String original, String rate) {
        return KrwConverter.resolve("USD", null, bd(original), bd(rate));
    }

    @ParameterizedTest(name = "${0} x {1} = ₩{2}")
    @CsvSource({
        // ordinary
        "12.34, 1388.5, 17134", // 17134.09
        "1, 1390, 1390",
        // exact .5 products round half up
        "0.50, 3, 2", // 1.5
        "2.50, 1, 3", // 2.5
        "1.15, 10, 12", // 11.5 (a double gives 11.499999999999998)
        "10.05, 1390, 13970", // 13969.5
        // Luyra's Math.round on the double product gives 216584 here (the
        // double is 216584.49999999997); exact HALF_UP gives 216585. This ₩1
        // difference is the accepted D-7 trade-off.
        "156.25, 1386.1408, 216585",
        // just below / above .5
        "1.15, 1390.5, 1599", // 1599.075
        "0.01, 1350.25, 14", // 13.5025
        "0.01, 1349.99, 13", // 13.4999
    })
    void convertsUsdToWholeWonHalfUp(String original, String rate, String expectedKrw) {
        ResolvedAmounts amounts = usd(original, rate);

        assertThat(amounts.currency()).isEqualTo("USD");
        assertThat(amounts.amountKrw()).isEqualByComparingTo(expectedKrw);
        assertThat(amounts.amountKrw().scale()).isZero();
        assertThat(amounts.amountUsd()).isEqualByComparingTo(original);
        assertThat(amounts.exchangeRate()).isEqualByComparingTo(rate);
    }

    @Test
    void ignoresAClientKrwFigureForUsd() {
        ResolvedAmounts amounts = KrwConverter.resolve("USD", bd("1"), bd("10"), bd("1390"));

        assertThat(amounts.amountKrw()).isEqualByComparingTo("13900");
    }

    @Test
    void keepsKrwAsSentAndDropsUsdFields() {
        ResolvedAmounts amounts = KrwConverter.resolve("KRW", bd("12000"), bd("9"), bd("1390"));

        assertThat(amounts.currency()).isEqualTo("KRW");
        assertThat(amounts.amountKrw()).isEqualByComparingTo("12000");
        assertThat(amounts.amountUsd()).isNull();
        assertThat(amounts.exchangeRate()).isNull();
    }

    @Test
    void acceptsWholeWonWrittenWithTrailingZerosOrAnExponent() {
        assertThat(KrwConverter.resolve("KRW", bd("12000.00"), null, null).amountKrw())
                .isEqualTo(bd("12000"));
        BigDecimal exponent = KrwConverter.resolve("KRW", bd("1E+3"), null, null).amountKrw();
        assertThat(exponent).isEqualTo(bd("1000"));
        assertThat(exponent.toString()).isEqualTo("1000");
    }

    @Test
    void normalisesUsdScaleWithoutChangingTheValue() {
        ResolvedAmounts amounts = usd("12.30", "1388.500000");

        assertThat(amounts.amountUsd().toPlainString()).isEqualTo("12.3");
        assertThat(amounts.exchangeRate().toPlainString()).isEqualTo("1388.5");
        assertThat(amounts.amountKrw()).isEqualByComparingTo("17079"); // 17078.55
    }

    @ParameterizedTest(name = "KRW {0} -> \"{1}\"")
    @CsvSource(
            nullValues = "null",
            value = {
                "null, Amount is required",
                "0, Amount must be greater than 0",
                "-5, Amount must be greater than 0",
                "1000.5, Amount must be a whole number of won",
                "1000000000000, Amount is too large",
            })
    void rejectsInvalidKrw(String amount, String message) {
        assertThatThrownBy(() -> KrwConverter.resolve("KRW", bd(amount), null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(message);
    }

    @ParameterizedTest(name = "USD {0} x {1} -> \"{2}\"")
    @CsvSource(
            nullValues = "null",
            value = {
                "null, 1390, Amount is required",
                "10, null, Exchange rate is required",
                "12.345, 1390, Amount can have at most 2 decimal places",
                "10, 1390.1234567, Exchange rate can have at most 6 decimal places",
                "10000000000, 1, Amount is too large",
                "1, 100000000, Exchange rate is too large",
                "0.01, 0.000001, Amount is too small to record in won",
                "9999999999.99, 99999999, Amount is too large",
                "0, 1390, Amount must be greater than 0",
                "10, -1, Exchange rate must be greater than 0",
            })
    void rejectsInvalidUsd(String original, String rate, String message) {
        assertThatThrownBy(() -> usd(original, rate))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(message);
    }

    @Test
    void rejectsAnUnknownCurrency() {
        assertThatThrownBy(() -> KrwConverter.resolve("EUR", bd("1"), null, null))
                .isInstanceOf(BadRequestException.class);
    }
}
