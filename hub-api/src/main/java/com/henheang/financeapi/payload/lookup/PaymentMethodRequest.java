package com.henheang.financeapi.payload.lookup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Create request; mirrors Luyra's createPaymentMethodSchema.
@Getter
@NoArgsConstructor
public class PaymentMethodRequest {

    @NotBlank(message = LookupValues.NAME_REQUIRED)
    @Size(max = LookupValues.NAME_MAX, message = LookupValues.NAME_TOO_LONG)
    private String name;

    @Size(min = 1, max = LookupValues.ICON_MAX, message = LookupValues.ICON_SIZE)
    private String icon;

    public void setName(String name) {
        this.name = LookupValues.trim(name);
    }

    public void setIcon(String icon) {
        this.icon = LookupValues.trim(icon);
    }
}
