package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.MenuModule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuModuleRepository extends JpaRepository<MenuModule, UUID> {

    Optional<MenuModule> findByCode(String code);

    /** Serialises concurrent pushes/pulls/state changes of one module. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MenuModule m WHERE m.code = :code")
    Optional<MenuModule> findByCodeForUpdate(@Param("code") String code);

    boolean existsByCode(String code);

    /** The live module that owns a service's privilege namespace. */
    @Query("SELECT m FROM MenuModule m WHERE m.privilegeModuleCode = :service AND m.status <> com.flowbinary.platformservice.model.ModuleStatus.DEREGISTERED")
    List<MenuModule> findLiveByPrivilegeModuleCode(@Param("service") String service);

    @Query("SELECT COUNT(m) > 0 FROM MenuModule m WHERE m.ownerClientId = :clientId AND m.status <> com.flowbinary.platformservice.model.ModuleStatus.DEREGISTERED")
    boolean existsLiveClient(@Param("clientId") String clientId);

    /** Non-deregistered modules of an application, for prefix-overlap checks. */
    @Query("SELECT m FROM MenuModule m WHERE m.application.id = :applicationId AND m.status <> com.flowbinary.platformservice.model.ModuleStatus.DEREGISTERED")
    List<MenuModule> findLiveByApplication(@Param("applicationId") UUID applicationId);

    List<MenuModule> findAllByOrderByCodeAsc();
}
