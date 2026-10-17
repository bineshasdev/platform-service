package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.UserSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSettingRepository extends JpaRepository<UserSetting, UUID> {
    @Query("SELECT us FROM UserSetting us WHERE us.userId = :userId AND us.definition.key = :key")
    Optional<UserSetting> findByUserIdAndKey(UUID userId, String key);

    List<UserSetting> findByUserId(UUID userId);
}
