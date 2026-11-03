package com.henheang.financeapi.payload.lookup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Partial update; mirrors Luyra's updateCategorySchema. Each Optional field has
// three states (Jackson's Jdk8 module): absent -> null (unchanged), explicit
// JSON null -> Optional.empty() (clear), a value -> Optional.of(v). name and
// type can't be cleared - FinanceCategoryServiceImpl rejects an explicit null
// for them, and a request with no fields at all.
@Getter
@NoArgsConstructor
public class CategoryPatchRequest {

    private Optional<
                    @NotBlank(message = LookupValues.NAME_REQUIRED)
                    @Size(max = LookupValues.NAME_MAX, message = LookupValues.NAME_TOO_LONG) String>
            name;

    private Optional<
                    @Size(min = 1, max = LookupValues.ICON_MAX, message = LookupValues.ICON_SIZE)
                    String>
            icon;

    private Optional<
                    @Pattern(
                            regexp = LookupValues.HEX_COLOR,
                            message = LookupValues.HEX_COLOR_MESSAGE)
                    String>
            color;

    private Optional<
                    @Pattern(
                            regexp = LookupValues.CATEGORY_TYPES,
                            message = LookupValues.CATEGORY_TYPE_MESSAGE)
                    String>
            type;

    private Optional<
                    @Pattern(
                            regexp = LookupValues.SPENDING_CLASSES,
                            message = LookupValues.SPENDING_CLASS_MESSAGE)
                    String>
            spendingClass;

    public void setName(Optional<String> name) {
        this.name = name == null ? null : name.map(LookupValues::trim);
    }

    public void setIcon(Optional<String> icon) {
        this.icon = icon == null ? null : icon.map(LookupValues::trim);
    }

    public void setColor(Optional<String> color) {
        this.color = color == null ? null : color.map(LookupValues::trim);
    }

    public void setType(Optional<String> type) {
        this.type = type;
    }

    public void setSpendingClass(Optional<String> spendingClass) {
        this.spendingClass = spendingClass;
    }

    public boolean isEmpty() {
        return name == null
                && icon == null
                && color == null
                && type == null
                && spendingClass == null;
    }
}
