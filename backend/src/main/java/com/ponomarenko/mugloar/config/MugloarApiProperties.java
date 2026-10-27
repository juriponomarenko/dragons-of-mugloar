package com.ponomarenko.mugloar.config;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the Dragons of Mugloar game API. Timeouts are configured through Boot's
 * standard {@code spring.http.client.*} properties.
 *
 * @param baseUrl base URL of the API, including the version segment
 * @param userAgent value sent in the {@code User-Agent} header (the API sits behind a CDN that
 * dislikes anonymous clients)
 */
@Validated
@ConfigurationProperties(prefix = "mugloar.api")
public record MugloarApiProperties(
		@NotBlank String baseUrl,
		@NotBlank @DefaultValue("mugloar-backend/1.0") String userAgent) {
}
