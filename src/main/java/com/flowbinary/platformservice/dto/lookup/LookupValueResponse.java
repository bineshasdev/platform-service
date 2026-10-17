package com.flowbinary.platformservice.dto.lookup;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LookupValueResponse {
    private String              code;
    private String              label;
    private String              parentCode;
    private Integer             sortOrder;
    private Map<String, Object> metadata;
}
