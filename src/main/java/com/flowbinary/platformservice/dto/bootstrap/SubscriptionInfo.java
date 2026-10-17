package com.flowbinary.platformservice.dto.bootstrap;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionInfo {
    private String       plan;
    private Long         maxUsers;
    private Long         maxProjects;
    private List<String> features;
    private LocalDate    renewalDate;
}
