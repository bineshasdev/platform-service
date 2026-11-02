package com.flowbinary.platformservice.authz.repository;

import com.flowbinary.platformservice.authz.model.TemplateProposal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateProposalRepository extends JpaRepository<TemplateProposal, UUID> {

    List<TemplateProposal> findByStatusOrderByProposedAtAsc(String status);

    List<TemplateProposal> findAllByOrderByProposedAtDesc();

    Optional<TemplateProposal> findByTemplateIdAndPrivilegeCodeAndStatus(UUID templateId, String privilegeCode, String status);
}
