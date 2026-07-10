package com.puduvandi.admin.repository;

import com.puduvandi.admin.entity.CommissionSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommissionSettingsRepository extends JpaRepository<CommissionSettings, Long> {
    Optional<CommissionSettings> findTopByActiveTrueOrderByIdDesc();
}
