package com.kuador.medical.platform;

import com.kuador.medical.shared.security.PublicEndpoint;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * System status: application name, version and a live check of the database. A down database is
 * reported in the body (200), never as an error, so clients can show a meaningful message.
 */
@RestController
@RequestMapping("/system")
public class SystemInfoController {

    private static final Logger log = LoggerFactory.getLogger(SystemInfoController.class);
    private static final int DATABASE_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;
    private final String name;
    private final String version;

    @Autowired
    SystemInfoController(
            DataSource dataSource,
            ObjectProvider<BuildProperties> buildProperties,
            @Value("${spring.application.name}") String name) {
        this(dataSource, buildProperties.getIfAvailable(), name);
    }

    SystemInfoController(DataSource dataSource, @Nullable BuildProperties buildProperties, String name) {
        this.dataSource = dataSource;
        this.name = name;
        this.version = buildProperties != null && buildProperties.getVersion() != null
                ? buildProperties.getVersion()
                : "unknown";
    }

    @PublicEndpoint
    @GetMapping("/info")
    public SystemInfo systemInfo() {
        HealthStatus database = databaseStatus();
        return new SystemInfo(name, version, database, database);
    }

    private HealthStatus databaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DATABASE_TIMEOUT_SECONDS) ? HealthStatus.UP : HealthStatus.DOWN;
        } catch (SQLException e) {
            log.warn("Database check failed ({})", e.getClass().getSimpleName());
            return HealthStatus.DOWN;
        }
    }
}
