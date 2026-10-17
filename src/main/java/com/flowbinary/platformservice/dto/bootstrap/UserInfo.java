package com.flowbinary.platformservice.dto.bootstrap;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserInfo {
    private String            id;
    private String            name;
    private String            email;
    private String            avatar;
    private String            role;
    private List<String>      permissions;
    private Map<String, String> preferences;
}
