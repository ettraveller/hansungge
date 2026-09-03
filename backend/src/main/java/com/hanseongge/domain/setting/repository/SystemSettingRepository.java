package com.hanseongge.domain.setting.repository;

import com.hanseongge.domain.setting.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}
