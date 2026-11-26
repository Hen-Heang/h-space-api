package com.henheang.financeapi.payload.transaction;

import java.util.UUID;

// Luyra's DescriptionSuggestion (types/finance.ts). type, categoryId and
// paymentMethodId come from the most recent use of the description.
public record DescriptionSuggestionResponse(
        String description, String type, UUID categoryId, UUID paymentMethodId, int uses) {}
