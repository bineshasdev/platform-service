package com.flowbinary.platformservice.dto.lookup;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LookupCategoryResponse {
    private String           code;
    private String           name;
    private List<LookupValueResponse> values;
}
