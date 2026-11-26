package com.henheang.financeapi.domain.money;

import com.henheang.securityapi.exception.BadRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one place a transaction's amounts are resolved (docs/finance/SPRING-ARCHITECTURE.md §4,
 * decision D-7). Luyra's equivalent is finance-transaction-service.ts resolveAmounts.
 *
 * <ul>
 *   <li>KRW entry: amountKrw as sent, which must be a whole number of won; any USD fields sent are
 *       ignored (Luyra stores them as null).
 *   <li>USD entry: amountKrw = originalAmount x exchangeRate rounded HALF_UP to whole won; a KRW
 *       figure sent by the client is ignored, never trusted.
 * </ul>
 *
 * <p>Luyra rounds a binary-double product with Math.round, so it can be one won lower when the
 * exact product ends in .5 but the double lands just below (e.g. $156.25 x 1386.1408 = 216584.5:
 * here 216585, Luyra 216584). Exact HALF_UP is the accepted behaviour; stored rows are copied, not
 * recomputed, so migrated data keeps Luyra's values.
 */
public final class KrwConverter {

    public static final String KRW = "KRW";
    public static final String USD = "USD";

    /** Largest whole-won amount: NUMERIC(14,0), limited to 12 digits like Luyra's input. */
    public static final BigDecimal MAX_KRW = new BigDecimal("999999999999");

    public static final int USD_SCALE = 2;
    public static final int RATE_SCALE = 6;

    private KrwConverter() {}

    /** The amounts to store; amountUsd and exchangeRate are null for KRW. */
    public record ResolvedAmounts(
            String currency, BigDecimal amountKrw, BigDecimal amountUsd, BigDecimal exchangeRate) {}

    public static ResolvedAmounts resolve(
            String currency, BigDecimal amountKrw, BigDecimal originalAmount, BigDecimal rate) {
        if (USD.equals(currency)) {
            return resolveUsd(originalAmount, rate);
        }
        if (KRW.equals(currency)) {
            return new ResolvedAmounts(KRW, wholeWon(amountKrw), null, null);
        }
        throw new BadRequestException("Currency must be one of KRW, USD");
    }

    private static ResolvedAmounts resolveUsd(BigDecimal originalAmount, BigDecimal rate) {
        if (originalAmount == null) {
            throw new BadRequestException("Amount is required");
        }
        if (rate == null) {
            throw new BadRequestException("Exchange rate is required");
        }
        // NUMERIC(12,2) and NUMERIC(14,6): 10 and 8 integer digits.
        BigDecimal usd = positiveWithScale(originalAmount, USD_SCALE, 10, "Amount");
        BigDecimal exchangeRate = positiveWithScale(rate, RATE_SCALE, 8, "Exchange rate");

        BigDecimal krw = usd.multiply(exchangeRate).setScale(0, RoundingMode.HALF_UP);
        if (krw.signum() <= 0) {
            throw new BadRequestException("Amount is too small to record in won");
        }
        if (krw.compareTo(MAX_KRW) > 0) {
            throw new BadRequestException("Amount is too large");
        }
        return new ResolvedAmounts(USD, krw, usd, exchangeRate);
    }

    private static BigDecimal wholeWon(BigDecimal amountKrw) {
        if (amountKrw == null) {
            throw new BadRequestException("Amount is required");
        }
        if (amountKrw.signum() <= 0) {
            throw new BadRequestException("Amount must be greater than 0");
        }
        BigDecimal stripped = amountKrw.stripTrailingZeros();
        if (stripped.scale() > 0) {
            throw new BadRequestException("Amount must be a whole number of won");
        }
        if (stripped.compareTo(MAX_KRW) > 0) {
            throw new BadRequestException("Amount is too large");
        }
        return stripped.setScale(0, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal positiveWithScale(
            BigDecimal value, int maxScale, int maxIntegerDigits, String label) {
        if (value.signum() <= 0) {
            throw new BadRequestException(label + " must be greater than 0");
        }
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() > maxScale) {
            throw new BadRequestException(
                    label + " can have at most " + maxScale + " decimal places");
        }
        BigDecimal plain =
                stripped.setScale(Math.max(stripped.scale(), 0), RoundingMode.UNNECESSARY);
        if (plain.precision() - plain.scale() > maxIntegerDigits) {
            throw new BadRequestException(label + " is too large");
        }
        return plain;
    }
}
