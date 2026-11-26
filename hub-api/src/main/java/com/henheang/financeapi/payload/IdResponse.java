package com.henheang.financeapi.payload;

import java.util.UUID;

// Body of a successful delete: Luyra returns { id } (lib/api/finance.ts).
public record IdResponse(UUID id) {}
