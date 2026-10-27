package com.flowbinary.platformservice.dto.menu;

import com.flowbinary.platformservice.model.UserGroup;

public record GroupResponse(String code, String name, String description, boolean active, long memberCount) {

    public static GroupResponse of(UserGroup g, long memberCount) {
        return new GroupResponse(g.getCode(), g.getName(), g.getDescription(), Boolean.TRUE.equals(g.getIsActive()), memberCount);
    }
}
