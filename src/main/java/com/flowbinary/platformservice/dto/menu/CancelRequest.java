package com.flowbinary.platformservice.dto.menu;

import jakarta.validation.constraints.Size;

public record CancelRequest(@Size(max = 500) String reason) {
}
