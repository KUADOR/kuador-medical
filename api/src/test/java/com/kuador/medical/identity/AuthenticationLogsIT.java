package com.kuador.medical.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.CapturingNotificationSender;
import com.kuador.medical.IdentityApi;
import com.kuador.medical.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.json.JsonMapper;

/**
 * Constitution V: the authentication flows never write emails, passwords or tokens to the logs,
 * not even when they fail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationLogsIT {

    private static final Pattern LINK_TOKEN = Pattern.compile("#t=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private CapturingNotificationSender mail;

    @Test
    void noEmailPasswordOrTokenReachesTheLogs(CapturedOutput output) {
        IdentityApi api = new IdentityApi(mvc, jsonMapper);
        String email = IdentityApi.uniqueEmail("logs");
        String password = IdentityApi.randomPassword();
        String wrongPassword = IdentityApi.randomPassword();
        api.signUp("Clinica Logs", email, password);
        List<String> secrets = new ArrayList<>(List.of(email, password, wrongPassword));

        for (int i = 0; i < 6; i++) {
            api.login(email, wrongPassword);
        }
        api.login(IdentityApi.uniqueEmail("unknown"), wrongPassword);
        String otherEmail = IdentityApi.uniqueEmail("logs-b");
        String otherPassword = IdentityApi.randomPassword();
        api.signUp("Clinica Logs B", otherEmail, otherPassword);
        secrets.addAll(List.of(otherEmail, otherPassword));
        Map<String, Object> session = api.session(otherEmail, otherPassword);
        String refreshToken = session.get("refreshToken").toString();
        String newRefreshToken = api.body(api.refresh(refreshToken)).get("refreshToken").toString();
        api.refresh(refreshToken);
        api.logout(newRefreshToken);
        api.requestPasswordReset(otherEmail);
        Matcher link = LINK_TOKEN.matcher(mail.awaitSentTo(otherEmail, 1).getLast().body());
        assertThat(link.find()).isTrue();
        String newPassword = IdentityApi.randomPassword();
        assertThat(api.confirmPasswordReset(link.group(1), newPassword)).hasStatus(HttpStatus.NO_CONTENT);
        api.confirmPasswordReset(link.group(1), newPassword);
        secrets.addAll(List.of(session.get("accessToken").toString(), refreshToken, newRefreshToken, link.group(1),
                newPassword));

        String logs = output.getAll();
        for (String secret : secrets) {
            assertThat(logs).doesNotContain(secret);
        }
        assertThat(logs).doesNotContainIgnoringCase("@example.test");
    }
}
