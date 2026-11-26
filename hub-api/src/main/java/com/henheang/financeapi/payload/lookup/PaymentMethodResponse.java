package com.henheang.financeapi.payload.lookup;

import com.henheang.financeapi.domain.FinancePaymentMethod;
import java.util.UUID;

// Matches Luyra's PaymentMethod type (types/finance.ts). user_id is never
// returned.
public record PaymentMethodResponse(UUID id, String name, String icon) {

    public static PaymentMethodResponse from(FinancePaymentMethod paymentMethod) {
        return new PaymentMethodResponse(
                paymentMethod.getId(), paymentMethod.getName(), paymentMethod.getIcon());
    }
}
