package com.henheang.financeapi.service;

import com.henheang.financeapi.payload.lookup.CategoryPatchRequest;
import com.henheang.financeapi.payload.lookup.CategoryRequest;
import com.henheang.financeapi.payload.lookup.CategoryResponse;
import java.util.List;
import java.util.UUID;

// userId is always the caller's id from the JWT principal.
public interface FinanceCategoryService {

    List<CategoryResponse> list(UUID userId);

    CategoryResponse create(UUID userId, CategoryRequest request);

    CategoryResponse update(UUID userId, UUID id, CategoryPatchRequest request);

    void delete(UUID userId, UUID id);
}
