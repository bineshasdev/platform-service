package com.flowbinary.platformservice.dto.bootstrap;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ThemeInfo {
    private String mode;
    private String primaryColor;
    private String layout;
}
