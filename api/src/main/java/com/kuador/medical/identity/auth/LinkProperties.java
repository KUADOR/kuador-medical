package com.kuador.medical.identity.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Links sent by email (ADR-014): {@code https://<domain>/<path>#t=<token>}. The same link is an
 * App Link, a Universal Link and the web fallback; the token goes in the fragment so it never
 * reaches server or proxy logs.
 */
@ConfigurationProperties("kuador.links")
record LinkProperties(
        @DefaultValue("") String webBaseUrl,
        @DefaultValue("") String appLinkBaseUrl,
        @DefaultValue("/invitation") String invitationPath,
        @DefaultValue("/password-reset") String passwordResetPath,
        @DefaultValue("/email-verification") String emailVerificationPath) {

    String passwordResetLink(String token) {
        return link(passwordResetPath, token);
    }

    private String link(String path, String token) {
        String base = StringUtils.hasText(appLinkBaseUrl) ? appLinkBaseUrl : webBaseUrl;
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + path + "#t=" + token;
    }
}
