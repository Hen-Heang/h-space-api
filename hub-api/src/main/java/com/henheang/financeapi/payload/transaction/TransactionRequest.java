package com.henheang.financeapi.payload.transaction;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Create and update body - Luyra's createTransactionSchema, which its PATCH reuses, so an update
 * replaces every field (omitted nullable fields become null; decision D-15).
 *
 * <p>Amounts are BigDecimal, parsed exactly from the JSON number. Which ones are required depends
 * on currency (KRW: amountKrw; USD: originalAmount + exchangeRate) and is checked, with the
 * rounding, by KrwConverter.
 */
@Getter
@NoArgsConstructor
public class TransactionRequest {

    @NotNull(message = "Date is required")
    private LocalDate date;

    @NotNull(message = "Type is required")
    @Pattern(regexp = TransactionValues.TYPES, message = TransactionValues.TYPE_MESSAGE)
    private String type;

    private UUID categoryId;

    @NotBlank(message = "Description is required")
    @Size(
            max = TransactionValues.DESCRIPTION_MAX,
            message = "Description must be at most 200 characters")
    private String description;

    // Defaults to KRW when absent; an explicit null is rejected, like zod's
    // .default("KRW").
    @JsonSetter(nulls = Nulls.FAIL)
    @Pattern(regexp = TransactionValues.CURRENCIES, message = TransactionValues.CURRENCY_MESSAGE)
    private String currency = "KRW";

    @Positive(message = "Amount must be greater than 0")
    private BigDecimal amountKrw;

    @Positive(message = "Amount must be greater than 0")
    private BigDecimal originalAmount;

    @Positive(message = "Exchange rate must be greater than 0")
    private BigDecimal exchangeRate;

    private UUID paymentMethodId;

    @Size(max = TransactionValues.NOTE_MAX, message = "Note must be at most 500 characters")
    private String note;

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public void setDescription(String description) {
        this.description = TransactionValues.trim(description);
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setAmountKrw(BigDecimal amountKrw) {
        this.amountKrw = amountKrw;
    }

    public void setOriginalAmount(BigDecimal originalAmount) {
        this.originalAmount = originalAmount;
    }

    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public void setPaymentMethodId(UUID paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public void setNote(String note) {
        this.note = TransactionValues.trim(note);
    }
}
