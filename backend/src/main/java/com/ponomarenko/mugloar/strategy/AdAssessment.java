package com.ponomarenko.mugloar.strategy;

import java.util.List;

import com.ponomarenko.mugloar.domain.Ad;

/**
 * An ad together with the strategy's opinion of it.
 *
 * @param ad the decoded ad
 * @param successRate estimated chance of success (0..1)
 * @param expectedValue {@code successRate * reward}
 * @param score ranking score: expected value minus the expected cost of losing a life, with
 * penalties applied; higher is better
 * @param risk coarse risk bucket for display
 * @param eligible whether the ad passes the safety threshold for the current number of lives
 * @param notes short human readable reasons behind the assessment
 */
public record AdAssessment(Ad ad, double successRate, double expectedValue, double score, RiskLevel risk,
		boolean eligible, List<String> notes) {
}
