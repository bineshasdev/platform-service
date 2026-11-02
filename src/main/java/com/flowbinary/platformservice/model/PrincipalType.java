package com.flowbinary.platformservice.model;

/**
 * What kind of actor a Keycloak user is. These are the only realm roles cliniqAI creates
 * (design §5): they say who someone is, never what they may do. Tenant authorization comes
 * from memberships and roles in the authorization model.
 */
public enum PrincipalType {
    /** cliniqAI staff. */
    PLATFORM_OPERATOR("platform_operator"),
    /** Anyone who works for a tenant: doctor, nurse, lab technician, pharmacist, tenant admin. */
    TENANT_MEMBER("tenant_member"),
    /** A patient. */
    PATIENT("patient");

    private final String roleName;

    PrincipalType(String roleName) {
        this.roleName = roleName;
    }

    /** The exact name of the Keycloak realm role. */
    public String roleName() {
        return roleName;
    }
}
