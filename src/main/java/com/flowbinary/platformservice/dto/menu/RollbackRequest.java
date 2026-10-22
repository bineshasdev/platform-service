package com.flowbinary.platformservice.dto.menu;

import jakarta.validation.constraints.Min;

public record RollbackRequest(@Min(0) int toVersion) {
}
