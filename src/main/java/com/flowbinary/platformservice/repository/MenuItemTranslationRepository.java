package com.flowbinary.platformservice.repository;

import com.flowbinary.platformservice.model.MenuItemTranslation;
import com.flowbinary.platformservice.model.MenuItemTranslationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MenuItemTranslationRepository
        extends JpaRepository<MenuItemTranslation, MenuItemTranslationId> {
    List<MenuItemTranslation> findByIdMenuItemId(UUID menuItemId);
    List<MenuItemTranslation> findByIdLocale(String locale);
}
