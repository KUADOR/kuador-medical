package com.kuador.medical.identity.account;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code kuador.links}: base URLs of the links sent by email (ADR-014). The app link domain opens
 * the app (App Links / Universal Links) or the web; when it is empty the web URL is used.
 */
@ConfigurationProperties("kuador.links")
public record LinkProperties(
        @DefaultValue("") String webBaseUrl,
        @DefaultValue("") String appLinkBaseUrl,
        @DefaultValue("/invitation") String invitationPath,
        @DefaultValue("/password-reset") String passwordResetPath,
        @DefaultValue("/email-verification") String emailVerificationPath) {

    @ConstructorBinding
    public LinkProperties {}

    /** Only the invitation path; the other paths keep their defaults. */
    public LinkProperties(String webBaseUrl, String appLinkBaseUrl, String invitationPath) {
        this(webBaseUrl, appLinkBaseUrl, invitationPath, "/password-reset", "/email-verification");
    }

    /** {@code <base><path>#t=<token>}: the token goes in the fragment, which never reaches a server log. */
    public String withToken(String path, String token) {
        String base = appLinkBaseUrl.isBlank() ? webBaseUrl : appLinkBaseUrl;
        return stripTrailingSlash(base) + path + "#t=" + token;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
