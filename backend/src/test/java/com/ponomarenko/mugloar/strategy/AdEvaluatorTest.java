package com.ponomarenko.mugloar.strategy;

import java.util.List;
import java.util.Map;

import com.ponomarenko.mugloar.TestFixtures;
import com.ponomarenko.mugloar.config.StrategyProperties;
import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.Encryption;
import com.ponomarenko.mugloar.domain.Probability;
import com.ponomarenko.mugloar.domain.ShopItem;
import org.junit.jupiter.api.Test;

import static com.ponomarenko.mugloar.TestFixtures.POTION;
import static com.ponomarenko.mugloar.TestFixtures.SHOP;
import static com.ponomarenko.mugloar.TestFixtures.ad;
import static com.ponomarenko.mugloar.TestFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AdEvaluatorTest {

	private final AdEvaluator evaluator = new AdEvaluator(TestFixtures.defaultStrategy());

	@Test
	void ranksAdsByScoreBestFirst() {
		List<Ad> ads = List.of(
				ad("cheap-safe", Probability.SURE_THING, 10),
				ad("rich-safe", Probability.PIECE_OF_CAKE, 100),
				ad("rich-risky", Probability.SUICIDE_MISSION, 500));

		List<AdAssessment> ranked = evaluator.evaluate(ads, state(3, 0));

		assertThat(ranked).extracting(a -> a.ad().adId()).containsExactly("rich-safe", "cheap-safe", "rich-risky");
	}

	@Test
	void expectedValueIsSuccessRateTimesReward() {
		AdAssessment assessment = evaluator.evaluate(List.of(ad("a", Probability.QUITE_LIKELY, 100)), state(3, 0))
				.getFirst();

		assertThat(assessment.successRate()).isEqualTo(0.8);
		assertThat(assessment.expectedValue()).isCloseTo(80.0, within(0.001));
		// 80 expected gold minus 20% chance of losing a life worth 50 gold
		assertThat(assessment.score()).isCloseTo(80.0 - 0.2 * 50, within(0.001));
		assertThat(assessment.risk()).isEqualTo(RiskLevel.SAFE);
	}

	@Test
	void losingALifeCostsMoreWhenFewerLivesRemain() {
		assertThat(evaluator.lifeCost(3)).isEqualTo(50.0);
		assertThat(evaluator.lifeCost(2)).isEqualTo(100.0);
		assertThat(evaluator.lifeCost(1)).isEqualTo(200.0);
	}

	@Test
	void eligibilityThresholdDependsOnLives() {
		Ad gamble = ad("gamble", Probability.GAMBLE, 100); // 50%
		Ad hmmm = ad("hmmm", Probability.HMMM, 100); // 60%
		Ad quiteLikely = ad("ql", Probability.QUITE_LIKELY, 100); // 80%

		assertThat(evaluator.assess(gamble, state(3, 0)).eligible()).isTrue();
		assertThat(evaluator.assess(gamble, state(2, 0)).eligible()).isFalse();
		assertThat(evaluator.assess(hmmm, state(2, 0)).eligible()).isTrue();
		assertThat(evaluator.assess(hmmm, state(1, 0)).eligible()).isFalse();
		assertThat(evaluator.assess(quiteLikely, state(1, 0)).eligible()).isTrue();
	}

	@Test
	void impossibleAndWorthlessAdsAreNeverEligible() {
		assertThat(evaluator.assess(ad("imp", Probability.IMPOSSIBLE, 1000), state(3, 0)).eligible()).isFalse();
		assertThat(evaluator.assess(ad("free", Probability.SURE_THING, 0), state(3, 0)).eligible()).isFalse();
	}

	@Test
	void riskyKeywordsArePenalisedAndExplained() {
		Ad honest = ad("honest", "Help Bob fix his fence", Probability.SURE_THING, 100);
		Ad shady = ad("shady", "Steal the turnips of Bob", Probability.SURE_THING, 100);

		AdAssessment honestAssessment = evaluator.assess(honest, state(3, 0));
		AdAssessment shadyAssessment = evaluator.assess(shady, state(3, 0));

		// Sure thing, 100 gold: expected value 100, no life cost; the penalty discounts the gold to 60
		assertThat(honestAssessment.score()).isCloseTo(100, within(0.001));
		assertThat(shadyAssessment.score()).isCloseTo(60, within(0.001));
		assertThat(shadyAssessment.expectedValue()).isCloseTo(100, within(0.001));
		assertThat(shadyAssessment.notes()).contains("May hurt reputation");
		assertThat(honestAssessment.notes()).isEmpty();
		assertThat(evaluator.mentionsRiskyKeyword("KIDNAP the mayor")).isTrue();
		assertThat(evaluator.mentionsRiskyKeyword(null)).isFalse();
	}

	@Test
	void riskyKeywordNeverImprovesANegativeScore() {
		// Gamble, 50 gold, 3 lives: expected value 25, failure cost 25 -> score 0 for the honest ad
		Ad honest = ad("honest", "Help Bob fix his fence", Probability.GAMBLE, 50);
		Ad shady = ad("shady", "Steal the turnips of Bob", Probability.GAMBLE, 50);

		double honestScore = evaluator.assess(honest, state(3, 0)).score();
		double shadyScore = evaluator.assess(shady, state(3, 0)).score();

		assertThat(honestScore).isCloseTo(0, within(0.001));
		assertThat(shadyScore).isLessThan(honestScore);
		// and with an already negative score the shady ad is still ranked lower
		Ad honestBad = ad("honest-bad", "Help Bob fix his fence", Probability.RISKY, 50);
		Ad shadyBad = ad("shady-bad", "Steal the turnips of Bob", Probability.RISKY, 50);
		assertThat(evaluator.assess(honestBad, state(3, 0)).score()).isNegative();
		assertThat(evaluator.assess(shadyBad, state(3, 0)).score())
				.isLessThan(evaluator.assess(honestBad, state(3, 0)).score());
	}

	@Test
	void riskyKeywordsMatchWholeWordsOnly() {
		assertThat(evaluator.mentionsRiskyKeyword("Help Bob improve their skills")).isFalse();
		assertThat(evaluator.mentionsRiskyKeyword("Kill the beast")).isFalse(); // 'kill' is not configured here
		assertThat(evaluator.mentionsRiskyKeyword("Please steal, quietly")).isTrue();
		AdEvaluator noKeywords = new AdEvaluator(new StrategyProperties(2, 50, List.of("cs"), true, 0.8, 0.6, 0.5,
				50, List.of(), 0.6, 100, List.of(), 2));
		assertThat(noKeywords.mentionsRiskyKeyword("steal everything")).isFalse();
	}

	@Test
	void notesMentionDecodingAndUnknownLabels() {
		Ad encrypted = new Ad("e", "task", 10, 1, Probability.GAMBLE, "Gamble", Encryption.BASE64);
		Ad weird = new Ad("w", "task", 10, 1, Probability.UNKNOWN, "Who knows", Encryption.NONE);

		assertThat(evaluator.assess(encrypted, state(3, 0)).notes()).contains("Decoded from base64");
		assertThat(evaluator.assess(weird, state(3, 0)).notes()).contains("Unknown probability label 'Who knows'");
		assertThat(evaluator.assess(weird, state(1, 0)).notes())
				.contains("Below 80% success threshold for 1 life");
		assertThat(evaluator.assess(weird, state(2, 0)).notes())
				.contains("Below 60% success threshold for 2 lives");
	}

	@Test
	void aLifeThatCannotBeReplacedCountsForOneMore() {
		Ad gamble = ad("gamble", Probability.GAMBLE, 100);

		// 3 lives: a gamble is eligible, unless the 50-gold potion is out of reach
		assertThat(evaluator.evaluate(List.of(gamble), state(3, 50), SHOP).get(0).eligible()).isTrue();
		AdAssessment broke = evaluator.evaluate(List.of(gamble), state(3, 49), SHOP).get(0);
		assertThat(broke.eligible()).isFalse();
		assertThat(broke.notes()).contains("Below 60% success threshold for 3 lives (no gold for a potion)");
		// a shop without a potion changes nothing
		List<ShopItem> noPotion = SHOP.stream().filter(i -> !i.equals(POTION)).toList();
		assertThat(evaluator.evaluate(List.of(gamble), state(3, 0), noPotion).get(0).eligible()).isTrue();
		// one life never becomes zero
		assertThat(evaluator.evaluate(List.of(ad("sure", Probability.QUITE_LIKELY, 100)), state(1, 0), SHOP).get(0)
				.eligible()).isTrue();
	}

	@Test
	void knownTrapsAreNeverEligibleWhateverTheLabelSays() {
		Ad trap = ad("trap", "Steal super awesome diamond pan from Kassiopeia Clemens", Probability.SURE_THING, 322);
		Ad honest = ad("honest", "Help Bob fix his fence", Probability.WALK_IN_THE_PARK, 30);

		List<AdAssessment> ranked = evaluator.evaluate(List.of(trap, honest), state(3, 0));

		assertThat(ranked.get(0).ad().adId()).isEqualTo("honest");
		AdAssessment trapAssessment = ranked.get(1);
		assertThat(trapAssessment.eligible()).isFalse();
		assertThat(trapAssessment.successRate()).isZero();
		assertThat(trapAssessment.risk()).isEqualTo(RiskLevel.DANGEROUS);
		assertThat(trapAssessment.notes()).contains("Known trap: ads like this fail whatever the label says");
	}

	@Test
	void anAdFamilyThatKeepsFailingIsDistrustedForTheRestOfTheGame() {
		Ad suspicious = ad("s", "Escort Babette Barnard to field in Oldcawl", Probability.SURE_THING, 100);

		assertThat(evaluator.evaluate(List.of(suspicious), state(3, 0), SHOP, Map.of("escort babette barnard to", 1))
				.get(0).eligible()).isTrue();
		AdAssessment distrusted = evaluator
				.evaluate(List.of(suspicious), state(3, 0), SHOP, Map.of("escort babette barnard to", 2)).get(0);
		assertThat(distrusted.eligible()).isFalse();
		assertThat(distrusted.successRate()).isZero();
		assertThat(distrusted.notes()).contains("Ads like this have failed 2 times this game");
	}

	@Test
	void adFamilyIsTheFirstFewWordsNormalised() {
		assertThat(Ad.family("Steal super awesome diamond pan from Kassiopeia Clemens"))
				.isEqualTo("steal super awesome diamond");
		assertThat(Ad.family("Steal super awesome diamond cat from Cuthberht Hudnall"))
				.isEqualTo("steal super awesome diamond");
		assertThat(Ad.family("  Help   Bob, fix his fence! ")).isEqualTo("help bob fix his");
		assertThat(Ad.family("Rescue")).isEqualTo("rescue");
		assertThat(Ad.family(null)).isEmpty();
	}

	@Test
	void pickBestReturnsHighestScoringEligibleAd() {
		List<Ad> ads = List.of(
				ad("tempting", Probability.SUICIDE_MISSION, 9999),
				ad("sensible", Probability.WALK_IN_THE_PARK, 50),
				ad("meh", Probability.SURE_THING, 5));

		assertThat(evaluator.pickBest(ads, state(3, 0))).get().extracting(a -> a.ad().adId()).isEqualTo("sensible");
		assertThat(evaluator.pickBest(List.of(ad("imp", Probability.IMPOSSIBLE, 10)), state(3, 0))).isEmpty();
	}

	@Test
	void tiesAreBrokenByUrgency() {
		Ad later = new Ad("later", "task", 10, 6, Probability.SURE_THING, "Sure thing", Encryption.NONE);
		Ad soon = new Ad("soon", "task", 10, 1, Probability.SURE_THING, "Sure thing", Encryption.NONE);

		List<AdAssessment> ranked = evaluator.evaluate(List.of(later, soon), state(3, 0));

		assertThat(ranked.getFirst().ad().adId()).isEqualTo("soon");
	}
}
