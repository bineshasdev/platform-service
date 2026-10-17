package com.flowbinary.platformservice.dto.bootstrap;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class LookupEntry {
    private String              code;
    private String              label;
    private String              parentCode;
    private Map<String, Object> metadata;
}
