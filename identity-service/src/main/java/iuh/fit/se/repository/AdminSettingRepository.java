package iuh.fit.se.repository;

import iuh.fit.se.domain.entity.AdminSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminSettingRepository extends JpaRepository<AdminSetting, UUID> {
    Optional<AdminSetting> findByScope(String scope);
}
