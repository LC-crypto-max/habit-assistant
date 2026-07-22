package com.example.assistant.controller;

import com.example.assistant.dto.DemoSystemStatusResponse;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo-system")
public class DemoSystemStatusController {

    private final DataSource dataSource;
    private final Environment environment;

    public DemoSystemStatusController(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @GetMapping("/status")
    public DemoSystemStatusResponse status() {
        String profile = configuredProfile();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            boolean ready = connection.isValid(2);
            return new DemoSystemStatusResponse(
                    ready,
                    metadata.getDatabaseProductName(),
                    metadata.getDatabaseProductVersion(),
                    profile,
                    ready ? "本地数据库连接正常。" : "本地数据库连接尚未就绪。");
        } catch (SQLException exception) {
            return new DemoSystemStatusResponse(
                    false,
                    "Unknown",
                    null,
                    profile,
                    "无法验证本地数据库连接，请检查 YAML 配置和 MySQL 服务。");
        }
    }

    private String configuredProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length > 0) {
            return String.join(",", active);
        }
        return Arrays.stream(environment.getDefaultProfiles()).findFirst().orElse("default");
    }
}
