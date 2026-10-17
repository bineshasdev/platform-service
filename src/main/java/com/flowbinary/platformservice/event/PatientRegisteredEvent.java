package com.flowbinary.platformservice.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Emitted when a patient self-registers. Not tenant-scoped.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class PatientRegisteredEvent extends PlatformEvent {

    private UUID patientId;
    private UUID authUserId;
    private String email;
    private String firstName;
    private String lastName;

    @Override
    public String getEventType() {
        return "PATIENT_REGISTERED";
    }
}
