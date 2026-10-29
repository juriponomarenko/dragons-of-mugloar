package com.ponomarenko.mugloar.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.ponomarenko.mugloar.config.StrategyProperties;
import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.domain.Probability;
import com.ponomarenko.mugloar.domain.ShopItem;

import org.springframework.stereotype.Component;

/**
 * Scores and ranks ads for a given game state.
 * <p>
 * The score of an ad is its expected gold ({@code successRate * reward}) minus the expected
 * cost of failing ({@code (1 - successRate) * lifeCost}), where the cost of a life grows as
 * lives run out. Ads whose text suggests a reputation hit (stealing, kidnapping, ...) have
 * their expected gold discounted before the life cost is subtracted. Ads that match a known
 * trap phrase, or whose family has already failed repeatedly in this game, get a 0% success
 * estimate whatever their label says. Ads below the safety threshold for the current number of lives are flagged as
 * not eligible; they are still returned (ranked) so a human player can see them.
 */
@Component
public class AdEvaluator {

	private final StrategyProperties properties;

	/** Whole-word, case-insensitive match of the risky keywords; {@code null} when none are configured. */
	private final Pattern riskyKeywordPattern;

	/** Case-insensitive substring match of the trap phrases; {@code null} when none are configured. */
	private final Pattern trapPattern;

	public AdEvaluator(StrategyProperties properties) {
		this.properties = properties;
		this.riskyKeywordPattern = properties.riskyKeywords().isEmpty() ? null
				: Pattern.compile("\\b(?:" + properties.riskyKeywords().stream().map(Pattern::quote)
						.collect(Collectors.joining("|")) + ")\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		this.trapPattern = properties.trapPhrases().isEmpty() ? null
				: Pattern.compile(properties.trapPhrases().stream().map(Pattern::quote).collect(Collectors.joining("|")),
						Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	/** Evaluates all ads with no shop or history: a potion is assumed affordable and nothing has failed yet. */
	public List<AdAssessment> evaluate(List<Ad> ads, GameState state) {
		return evaluate(ads, state, List.of(), Map.of());
	}

	/** Evaluates all ads knowing the shop but with no history yet. */
	public List<AdAssessment> evaluate(List<Ad> ads, GameState state, List<ShopItem> shop) {
		return evaluate(ads, state, shop, Map.of());
	}

	/**
	 * Evaluates all ads and returns them ordered best first. The shop tells whether a healing
	 * potion is affordable right now; when it is not, a lost life cannot be replaced, so the
	 * ads are judged as if one life fewer remained. {@code failedFamilies} counts the failures
	 * per ad family so far in this game.
	 */
	public List<AdAssessment> evaluate(List<Ad> ads, GameState state, List<ShopItem> shop,
			Map<String, Integer> failedFamilies) {
		boolean potionAffordable = shop.stream()
				.filter(item -> StrategyProperties.HEALING_POTION_ID.equals(item.id()))
				.findFirst()
				.map(potion -> potion.cost() <= state.gold())
				.orElse(true);
		List<AdAssessment> assessments = new ArrayList<>(ads.size());
		for (Ad ad : ads) {
			assessments.add(assess(ad, state, potionAffordable, failedFamilies));
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
		return assess(ad, state, true, Map.of());
	}

	AdAssessment assess(Ad ad, GameState state, boolean potionAffordable, Map<String, Integer> failedFamilies) {
		List<String> notes = new ArrayList<>();
		int effectiveLives = potionAffordable ? state.lives() : Math.max(1, state.lives() - 1);
		double successRate = ad.successRate();
		int failures = failedFamilies.getOrDefault(ad.family(), 0);
		if (isTrap(ad.message())) {
			successRate = 0;
			notes.add("Known trap: ads like this fail whatever the label says");
		}
		else if (failures >= properties.distrustAfterFailures()) {
			successRate = 0;
			notes.add("Ads like this have failed " + failures + " times this game");
		}
		double expectedValue = successRate * ad.reward();
		// The reputation penalty discounts the reward side only. Applying it to the final score
		// would *raise* a negative score and make a shady ad outrank an honest one.
		boolean risky = mentionsRiskyKeyword(ad.message());
		double valued = risky ? expectedValue * properties.riskyKeywordPenalty() : expectedValue;
		double score = valued - (1 - successRate) * lifeCost(effectiveLives);

		if (ad.probability() == Probability.UNKNOWN) {
			notes.add("Unknown probability label '" + ad.probabilityLabel() + "'");
		}
		if (ad.wasEncrypted()) {
			notes.add("Decoded from " + ad.encryption().name().toLowerCase(Locale.ROOT));
		}
		if (risky) {
			notes.add("May hurt reputation");
		}

		double threshold = properties.minSuccessRateFor(effectiveLives);
		boolean eligible = successRate >= threshold && ad.reward() > 0 && successRate > 0;
		if (!eligible) {
			notes.add(String.format(Locale.ROOT, "Below %.0f%% success threshold for %d %s%s", threshold * 100,
					state.lives(), state.lives() == 1 ? "life" : "lives",
					potionAffordable ? "" : " (no gold for a potion)"));
		}
		return new AdAssessment(ad, successRate, expectedValue, score, RiskLevel.of(successRate), eligible, notes);
	}

	/**
	 * Gold-equivalent cost of losing a life: the configured {@code lifeValue} (a potion plus
	 * the turn spent buying it back) multiplied up as lives run out.
	 */
	double lifeCost(int lives) {
		int multiplier = switch (lives) {
			case 0, 1 -> 4;
			case 2 -> 2;
			default -> 1;
		};
		return (double) properties.lifeValue() * multiplier;
	}

	boolean isTrap(String message) {
		return message != null && trapPattern != null && trapPattern.matcher(message).find();
	}

	boolean mentionsRiskyKeyword(String message) {
		if (message == null || riskyKeywordPattern == null) {
			return false;
		}
		return riskyKeywordPattern.matcher(message).find();
	}
}
