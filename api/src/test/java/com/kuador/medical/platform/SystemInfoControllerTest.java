package com.kuador.medical.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

/**
 * Database check of the system status: a down database is reported, never thrown.
 */
class SystemInfoControllerTest {

    private static final BuildProperties BUILD = new BuildProperties(properties());

    @Test
    void reportsUpWhenTheDatabaseAnswers() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        var info = new SystemInfoController(dataSource, BUILD, "medical-kuador-api").systemInfo();

        assertThat(info).isEqualTo(new SystemInfo("medical-kuador-api", "0.1.0", HealthStatus.UP, HealthStatus.UP));
    }

    @Test
    void reportsDownWhenTheDatabaseIsUnreachable() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        var info = new SystemInfoController(dataSource, BUILD, "medical-kuador-api").systemInfo();

        assertThat(info.database()).isEqualTo(HealthStatus.DOWN);
        assertThat(info.status()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void reportsDownWhenTheConnectionIsNotValid() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(false);

        var info = new SystemInfoController(dataSource, BUILD, "medical-kuador-api").systemInfo();

        assertThat(info.database()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void fallsBackToUnknownVersionWithoutBuildInfo() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("down"));

        var info = new SystemInfoController(dataSource, (BuildProperties) null, "medical-kuador-api").systemInfo();

        assertThat(info.version()).isEqualTo("unknown");
    }

    private static Properties properties() {
        var properties = new Properties();
        properties.setProperty("version", "0.1.0");
        return properties;
    }
}
