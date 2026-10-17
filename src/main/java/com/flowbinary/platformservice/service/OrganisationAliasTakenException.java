package com.flowbinary.platformservice.service;

/**
 * Thrown by {@link KeycloakUserService#createOrganisation} when the requested alias
 * is held by an organisation that is neither pending nor stale enough to reap — i.e.
 * a real, settled conflict rather than an in-flight or crashed registration attempt.
 * Callers that generate the alias themselves (e.g. from a clinic name) can catch this
 * specifically and retry with the next candidate slug.
 */
public class OrganisationAliasTakenException extends RuntimeException {

    private final String alias;

    public OrganisationAliasTakenException(String alias) {
        super("Clinic slug '" + alias + "' is already registered.");
        this.alias = alias;
    }

    public String getAlias() {
        return alias;
    }
}
