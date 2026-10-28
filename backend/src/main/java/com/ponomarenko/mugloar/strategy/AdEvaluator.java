package com.ponomarenko.mugloar.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.ponomarenko.mugloar.config.StrategyProperties;
import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.domain.Probability;

import org.springframework.stereotype.Component;

/**
 * Scores and ranks ads for a given game state.
 * <p>
 * The score of an ad is its expected gold ({@code successRate * reward}) minus the expected
 * cost of failing ({@code (1 - successRate) * lifeCost}), where the cost of a life grows as
 * lives run out. Ads whose text suggests a reputation hit (stealing, kidnapping, ...) are
 * penalised. Ads below the safety threshold for the current number of lives are flagged as
 * not eligible; they are still returned (ranked) so a human player can see them.
 */
@Component
public class AdEvaluator {

	private final StrategyProperties properties;

	/** Whole-word, case-insensitive match of the risky keywords; {@code null} when none are configured. */
	private final Pattern riskyKeywordPattern;

	public AdEvaluator(StrategyProperties properties) {
		this.properties = properties;
		this.riskyKeywordPattern = properties.riskyKeywords().isEmpty() ? null
				: Pattern.compile("\\b(?:" + properties.riskyKeywords().stream().map(Pattern::quote)
						.collect(Collectors.joining("|")) + ")\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	/** Evaluates all ads and returns them ordered best first. */
	public List<AdAssessment> evaluate(List<Ad> ads, GameState state) {
		List<AdAssessment> assessments = new ArrayList<>(ads.size());
		for (Ad ad : ads) {
			assessments.add(assess(ad, state));
		}
		assessments.sort(Comparator.comparingDouble(AdAssessment::score).reversed()
				.thenComparing(a -> a.ad().expiresIn()));
		return assessments;
	}

	/** The best eligible ad, if any. */
	public Optional<AdAssessment> pickBest(List<Ad> ads, GameState state) {
		return evaluate(ads, state).stream().filter(AdAssessment::eligible).findFirst();
	}

	AdAssessment assess(Ad ad, GameState state) {
		List<String> notes = new ArrayList<>();
		double successRate = ad.successRate();
		double expectedValue = successRate * ad.reward();
		double score = expectedValue - (1 - successRate) * lifeCost(state.lives());

		if (ad.probability() == Probability.UNKNOWN) {
			notes.add("Unknown probability label '" + ad.probabilityLabel() + "'");
		}
		if (ad.wasEncrypted()) {
			notes.add("Decoded from " + ad.encryption().name().toLowerCase(Locale.ROOT));
		}
		if (mentionsRiskyKeyword(ad.message())) {
			score *= properties.riskyKeywordPenalty();
			notes.add("May hurt reputation");
		}

		double threshold = properties.minSuccessRateFor(state.lives());
		boolean eligible = successRate >= threshold && ad.reward() > 0 && successRate > 0;
		if (!eligible) {
			notes.add(String.format(Locale.ROOT, "Below %.0f%% success threshold for %d %s", threshold * 100,
					state.lives(), state.lives() == 1 ? "life" : "lives"));
		}
		return new AdAssessment(ad, successRate, expectedValue, score, RiskLevel.of(successRate), eligible, notes);
	}

	/**
	 * Gold-equivalent cost of losing a life. A healing potion costs 50 gold, so that is the
	 * baseline; the fewer lives remain, the more each one is worth.
	 */
	double lifeCost(int lives) {
		int multiplier = switch (lives) {
			case 0, 1 -> 4;
			case 2 -> 2;
			default -> 1;
		};
		return (double) properties.lifeValue() * multiplier;
	}

	boolean mentionsRiskyKeyword(String message) {
		if (message == null || riskyKeywordPattern == null) {
			return false;
		}
		return riskyKeywordPattern.matcher(message).find();
	}
}
