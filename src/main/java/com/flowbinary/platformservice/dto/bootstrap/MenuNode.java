package com.flowbinary.platformservice.dto.bootstrap;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MenuNode {
    private String       code;
    private String       label;
    private String       icon;
    private String       route;
    private String       type;
    private int          sortOrder;
    private String       requiredFeature;
    private List<MenuNode> children;
}
