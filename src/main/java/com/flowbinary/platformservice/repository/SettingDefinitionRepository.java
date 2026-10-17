package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.SettingCategory;
import com.flowbinary.platformservice.model.SettingDefinition;
import com.flowbinary.platformservice.model.SettingScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettingDefinitionRepository extends JpaRepository<SettingDefinition, UUID> {
    Optional<SettingDefinition> findByKey(String key);
    List<SettingDefinition> findByCategory(SettingCategory category);
    List<SettingDefinition> findByScope(SettingScope scope);
    List<SettingDefinition> findByTenantOverridableTrue();
    List<SettingDefinition> findByUserOverridableTrue();
}
