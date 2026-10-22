package com.flowbinary.platformservice.dto.menu;

import jakarta.validation.constraints.Size;

public record PublishRequest(@Size(max = 500) String note) {
}
