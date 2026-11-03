package com.henheang.financeapi.service.impl;

import com.henheang.financeapi.domain.FinancePaymentMethod;
import com.henheang.financeapi.payload.lookup.PaymentMethodPatchRequest;
import com.henheang.financeapi.payload.lookup.PaymentMethodRequest;
import com.henheang.financeapi.payload.lookup.PaymentMethodResponse;
import com.henheang.financeapi.repository.FinancePaymentMethodRepository;
import com.henheang.financeapi.service.FinancePaymentMethodService;
import com.henheang.securityapi.exception.BadRequestException;
import com.henheang.securityapi.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Payment methods, ported from Luyra's finance-lookup-service. Same ownership
// rules as FinanceCategoryServiceImpl.
@Service
@RequiredArgsConstructor
public class FinancePaymentMethodServiceImpl implements FinancePaymentMethodService {

    static final String NAME_CONSTRAINT = "uq_finance_payment_methods_user_name";
    private static final String NOUN = "payment method";

    private final FinancePaymentMethodRepository paymentMethodRepository;

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> list(UUID userId) {
        return paymentMethodRepository.findAllByUserIdOrderByNameAscIdAsc(userId).stream()
                .map(PaymentMethodResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public PaymentMethodResponse create(UUID userId, PaymentMethodRequest request) {
        rejectDuplicateName(userId, request.getName(), null);

        FinancePaymentMethod paymentMethod = new FinancePaymentMethod();
        paymentMethod.setUserId(userId);
        paymentMethod.setName(request.getName());
        paymentMethod.setIcon(request.getIcon());
        return PaymentMethodResponse.from(save(paymentMethod));
    }

    @Override
    @Transactional
    public PaymentMethodResponse update(UUID userId, UUID id, PaymentMethodPatchRequest request) {
        if (request.isEmpty()) {
            throw new BadRequestException("At least one field must be provided");
        }
        FinancePaymentMethod paymentMethod = findOwned(userId, id);

        if (request.getName() != null) {
            String name = LookupRules.required(request.getName(), "name");
            rejectDuplicateName(userId, name, id);
            paymentMethod.setName(name);
        }
        if (request.getIcon() != null) {
            paymentMethod.setIcon(request.getIcon().orElse(null));
        }
        return PaymentMethodResponse.from(save(paymentMethod));
    }

    // Phase 1a: nothing references a payment method yet; see
    // FinanceCategoryServiceImpl.delete for what V2 must add.
    @Override
    @Transactional
    public void delete(UUID userId, UUID id) {
        paymentMethodRepository.delete(findOwned(userId, id));
    }

    private FinancePaymentMethod findOwned(UUID userId, UUID id) {
        return paymentMethodRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment method", "id", id));
    }

    private void rejectDuplicateName(UUID userId, String name, UUID excludeId) {
        paymentMethodRepository.findByUserIdAndNameIgnoringCase(userId, name).stream()
                .filter(existing -> !existing.getId().equals(excludeId))
                .findFirst()
                .ifPresent(
                        existing -> {
                            throw LookupRules.duplicateName(NOUN, existing.getName());
                        });
    }

    private FinancePaymentMethod save(FinancePaymentMethod paymentMethod) {
        try {
            return paymentMethodRepository.saveAndFlush(paymentMethod);
        } catch (DataIntegrityViolationException ex) {
            if (LookupRules.isViolationOf(ex, NAME_CONSTRAINT)) {
                throw LookupRules.duplicateName(NOUN, paymentMethod.getName());
            }
            throw ex;
        }
    }
}
