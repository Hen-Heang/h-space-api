package com.henheang.financeapi.service.impl;

import com.henheang.financeapi.domain.FinanceCategory;
import com.henheang.financeapi.domain.LookupUsage;
import com.henheang.financeapi.payload.lookup.CategoryPatchRequest;
import com.henheang.financeapi.payload.lookup.CategoryRequest;
import com.henheang.financeapi.payload.lookup.CategoryResponse;
import com.henheang.financeapi.repository.FinanceCategoryRepository;
import com.henheang.financeapi.repository.LookupUsageRepository;
import com.henheang.financeapi.service.FinanceCategoryService;
import com.henheang.securityapi.exception.BadRequestException;
import com.henheang.securityapi.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Categories, ported from Luyra's finance-lookup-service. Ownership replaces
// the RLS the source never had: every read and write goes through a
// user-scoped finder, and another user's id answers 404 so ids don't leak.
@Service
@RequiredArgsConstructor
public class FinanceCategoryServiceImpl implements FinanceCategoryService {

    static final String NAME_CONSTRAINT = "uq_finance_categories_user_name";
    private static final String NOUN = "category";

    private final FinanceCategoryRepository categoryRepository;
    private final LookupUsageRepository usageRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID userId) {
        return categoryRepository.findAllByUserIdOrderByNameAscIdAsc(userId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public CategoryResponse create(UUID userId, CategoryRequest request) {
        rejectDuplicateName(userId, request.getName(), null);

        FinanceCategory category = new FinanceCategory();
        category.setUserId(userId);
        category.setName(request.getName());
        category.setIcon(request.getIcon());
        category.setColor(request.getColor());
        category.setType(request.getType());
        category.setSpendingClass(request.getSpendingClass());
        return CategoryResponse.from(save(category));
    }

    @Override
    @Transactional
    public CategoryResponse update(UUID userId, UUID id, CategoryPatchRequest request) {
        if (request.isEmpty()) {
            throw new BadRequestException("At least one field must be provided");
        }
        FinanceCategory category = findOwned(userId, id);

        if (request.getName() != null) {
            String name = LookupRules.required(request.getName(), "name");
            rejectDuplicateName(userId, name, id);
            category.setName(name);
        }
        if (request.getIcon() != null) {
            category.setIcon(request.getIcon().orElse(null));
        }
        if (request.getColor() != null) {
            category.setColor(request.getColor().orElse(null));
        }
        if (request.getType() != null) {
            category.setType(LookupRules.required(request.getType(), "type"));
        }
        if (request.getSpendingClass() != null) {
            category.setSpendingClass(request.getSpendingClass().orElse(null));
        }
        return CategoryResponse.from(save(category));
    }

    // A category still used by transactions or recurring rules answers 409
    // with the counts, as Luyra does (docs/finance/API-CONTRACT.md #4).
    // Budgets and templates join the count when their tables land.
    @Override
    @Transactional
    public void delete(UUID userId, UUID id) {
        FinanceCategory category = findOwned(userId, id);
        LookupUsage usage = usageRepository.countCategoryUsage(userId, id);
        if (usage.total() > 0) {
            throw LookupRules.inUse(category.getName(), usage);
        }
        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (LookupRules.isForeignKeyViolation(ex)) {
                throw LookupRules.inUse(category.getName());
            }
            throw ex;
        }
    }

    private FinanceCategory findOwned(UUID userId, UUID id) {
        return categoryRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    }

    private void rejectDuplicateName(UUID userId, String name, UUID excludeId) {
        categoryRepository.findByUserIdAndNameIgnoringCase(userId, name).stream()
                .filter(existing -> !existing.getId().equals(excludeId))
                .findFirst()
                .ifPresent(
                        existing -> {
                            throw LookupRules.duplicateName(NOUN, existing.getName());
                        });
    }

    private FinanceCategory save(FinanceCategory category) {
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            if (LookupRules.isViolationOf(ex, NAME_CONSTRAINT)) {
                throw LookupRules.duplicateName(NOUN, category.getName());
            }
            throw ex;
        }
    }
}
