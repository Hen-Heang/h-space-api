package com.henheang.financeapi.payload.lookup;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Create request; mirrors Luyra's createCategorySchema. icon, color and
// spendingClass default to null; type defaults to "expense" when absent, and
// an explicit null type is rejected (zod's .default() only fills undefined).
@Getter
@NoArgsConstructor
public class CategoryRequest {

    @NotBlank(message = LookupValues.NAME_REQUIRED)
    @Size(max = LookupValues.NAME_MAX, message = LookupValues.NAME_TOO_LONG)
    private String name;

    @Size(min = 1, max = LookupValues.ICON_MAX, message = LookupValues.ICON_SIZE)
    private String icon;

    @Pattern(regexp = LookupValues.HEX_COLOR, message = LookupValues.HEX_COLOR_MESSAGE)
    private String color;

    @JsonSetter(nulls = Nulls.FAIL)
    @Pattern(regexp = LookupValues.CATEGORY_TYPES, message = LookupValues.CATEGORY_TYPE_MESSAGE)
    private String type = LookupValues.DEFAULT_CATEGORY_TYPE;

    @Pattern(regexp = LookupValues.SPENDING_CLASSES, message = LookupValues.SPENDING_CLASS_MESSAGE)
    private String spendingClass;

    public void setName(String name) {
        this.name = LookupValues.trim(name);
    }

    public void setIcon(String icon) {
        this.icon = LookupValues.trim(icon);
    }

    public void setColor(String color) {
        this.color = LookupValues.trim(color);
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setSpendingClass(String spendingClass) {
        this.spendingClass = spendingClass;
    }
}
