package com.kuador.medical.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.ContractSchema;
import com.kuador.medical.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/**
 * GET /api/v1/system/info against a real PostgreSQL and the OpenAPI contract.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SystemInfoIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void reportsNameVersionAndDatabaseStatusWithoutAuthentication() {
        var result = mvc.get().uri("/api/v1/system/info").exchange();

        assertThat(result).hasStatus(HttpStatus.OK).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        Map<String, Object> body = body(result.getResponse().getContentAsByteArray());
        assertThat(body)
                .containsEntry("name", "medical-kuador-api")
                .containsEntry("version", "0.1.0")
                .containsEntry("status", "UP")
                .containsEntry("database", "UP");
    }

    @Test
    void responseMatchesTheContract() {
        var result = mvc.get().uri("/api/v1/system/info").exchange();

        ContractSchema.identity().assertResponse(
                "/system/info", "GET", 200, "application/json", body(result.getResponse().getContentAsByteArray()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(byte[] content) {
        return jsonMapper.readValue(content, Map.class);
    }
}
