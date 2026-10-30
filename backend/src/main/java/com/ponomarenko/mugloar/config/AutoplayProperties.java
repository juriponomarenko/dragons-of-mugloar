package com.ponomarenko.mugloar.config;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the command-line autoplay run (the {@code autoplay} profile). The number of
 * actions per game is capped by {@code mugloar.strategy.max-turns}, the same cap the UI uses.
 *
 * @param games how many complete games to play before exiting
 * @param targetScore the score every game has to reach for the process to exit with code 0
 */
@Validated
@ConfigurationProperties(prefix = "mugloar.autoplay")
public record AutoplayProperties(
		@Min(1) @DefaultValue("1") int games,
		@Min(0) @DefaultValue("1000") int targetScore) {
}
