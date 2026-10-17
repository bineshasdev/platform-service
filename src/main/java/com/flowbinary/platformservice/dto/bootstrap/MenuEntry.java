package com.flowbinary.platformservice.dto.bootstrap;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuEntry {
    private String       id;
    private String       label;
    private String       icon;
    private String       path;
    /** Role names that can see this item. Null = visible to all. */
    private List<String> roles;
    /** Feature code required for this item to appear. Null = no feature gate. */
    private String       feature;
    private List<MenuEntry> children;
}
