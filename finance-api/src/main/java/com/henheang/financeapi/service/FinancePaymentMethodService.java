package com.henheang.financeapi.service;

import com.henheang.financeapi.payload.lookup.PaymentMethodPatchRequest;
import com.henheang.financeapi.payload.lookup.PaymentMethodRequest;
import com.henheang.financeapi.payload.lookup.PaymentMethodResponse;
import java.util.List;
import java.util.UUID;

// userId is always the caller's id from the JWT principal.
public interface FinancePaymentMethodService {

    List<PaymentMethodResponse> list(UUID userId);

    PaymentMethodResponse create(UUID userId, PaymentMethodRequest request);

    PaymentMethodResponse update(UUID userId, UUID id, PaymentMethodPatchRequest request);

    void delete(UUID userId, UUID id);
}
