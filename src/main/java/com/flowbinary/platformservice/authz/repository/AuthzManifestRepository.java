package com.flowbinary.platformservice.authz.repository;

import com.flowbinary.platformservice.authz.model.AuthzManifestRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthzManifestRepository extends JpaRepository<AuthzManifestRecord, UUID> {

    Optional<AuthzManifestRecord> findFirstByServiceAndOutcomeOrderByReceivedAtDesc(String service, String outcome);

    List<AuthzManifestRecord> findByServiceOrderByReceivedAtDesc(String service);
}
