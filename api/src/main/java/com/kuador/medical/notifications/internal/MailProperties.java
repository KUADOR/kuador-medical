package com.kuador.medical.notifications.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code kuador.mail}: sender address of outgoing emails ({@code KUADOR_MAIL_FROM}). */
@ConfigurationProperties("kuador.mail")
record MailProperties(@DefaultValue("") String from) {}
