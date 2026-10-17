package com.flowbinary.platformservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PlanFeatureId implements Serializable {

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "feature_id")
    private UUID featureId;
}
