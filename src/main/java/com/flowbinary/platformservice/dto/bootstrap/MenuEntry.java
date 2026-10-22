package com.flowbinary.platformservice.dto.bootstrap;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
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
    /** LINK | GROUP | DIVIDER | EXTERNAL. */
    private String       type;
    /** NAVIGATE | EXPAND | BOTH — what activating the item does. */
    private String       behavior;
    /** SELF | BLANK. */
    private String       target;
    /** Effective sort order among siblings (tenant override applied). */
    private Integer      order;
    /** Render a divider above this entry. Only set when true. */
    private Boolean      separatorBefore;
    /** Short label such as "New"; populated by temporary menus. */
    private String       badge;
    /** When a time-bound entry leaves the menu; populated by temporary menus. */
    private Instant      expiresAt;
    private List<MenuEntry> children;
}
