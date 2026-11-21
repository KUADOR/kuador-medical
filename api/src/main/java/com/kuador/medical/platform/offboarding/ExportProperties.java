package com.kuador.medical.platform.offboarding;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code kuador.platform.exports} (MEDK-70): how long a ready export is kept, how long a download
 * link lasts, and the secret its signature derives from (by default the JWT secret; never logged).
 */
@ConfigurationProperties("kuador.platform.exports")
record ExportProperties(
        @DefaultValue("P7D") Duration retention,
        @DefaultValue("PT5M") Duration linkTtl,
        @DefaultValue("") String signingKey) {

    @Override
    public String toString() {
        return "ExportProperties[retention=" + retention + ", linkTtl=" + linkTtl + ", signingKey=redacted]";
    }
}
