package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppSettingRepository extends JpaRepository<AppSetting, UUID> {
    @Query("SELECT a FROM AppSetting a WHERE a.definition.key = :key")
    Optional<AppSetting> findByKey(String key);

    @Query("SELECT a FROM AppSetting a JOIN FETCH a.definition")
    List<AppSetting> findAllWithDefinition();
}
