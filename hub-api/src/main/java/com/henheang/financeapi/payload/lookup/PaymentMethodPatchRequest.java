package com.henheang.financeapi.payload.lookup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Partial update; same three-state Optional semantics as CategoryPatchRequest.
// name can't be cleared.
@Getter
@NoArgsConstructor
public class PaymentMethodPatchRequest {

    private Optional<
                    @NotBlank(message = LookupValues.NAME_REQUIRED)
                    @Size(max = LookupValues.NAME_MAX, message = LookupValues.NAME_TOO_LONG) String>
            name;

    private Optional<
                    @Size(min = 1, max = LookupValues.ICON_MAX, message = LookupValues.ICON_SIZE)
                    String>
            icon;

    public void setName(Optional<String> name) {
        this.name = name == null ? null : name.map(LookupValues::trim);
    }

    public void setIcon(Optional<String> icon) {
        this.icon = icon == null ? null : icon.map(LookupValues::trim);
    }

    public boolean isEmpty() {
        return name == null && icon == null;
    }
}
