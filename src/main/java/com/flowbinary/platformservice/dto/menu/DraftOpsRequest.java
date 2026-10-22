package com.flowbinary.platformservice.dto.menu;

import com.flowbinary.platformservice.model.MenuOp;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DraftOpsRequest(
        /** The draft's rowVersion as the caller last saw it. Omit when creating the draft. */
        Long expectedRowVersion,
        @Size(max = 200) String title,
        @NotEmpty @Size(max = 500) List<MenuOp> ops) {
}
