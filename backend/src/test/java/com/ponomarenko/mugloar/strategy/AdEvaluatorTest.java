package com.ponomarenko.mugloar.strategy;

import java.util.List;

import com.ponomarenko.mugloar.TestFixtures;
import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.Encryption;
import com.ponomarenko.mugloar.domain.Probability;
import org.junit.jupiter.api.Test;

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

		assertThat(shadyAssessment.score()).isCloseTo(honestAssessment.score() * 0.6, within(0.001));
		assertThat(shadyAssessment.notes()).contains("May hurt reputation");
		assertThat(honestAssessment.notes()).isEmpty();
		assertThat(evaluator.mentionsRiskyKeyword("KIDNAP the mayor")).isTrue();
		assertThat(evaluator.mentionsRiskyKeyword(null)).isFalse();
	}

	@Test
	void notesMentionDecodingAndUnknownLabels() {
		Ad encrypted = new Ad("e", "task", 10, 1, Probability.GAMBLE, "Gamble", Encryption.BASE64);
		Ad weird = new Ad("w", "task", 10, 1, Probability.UNKNOWN, "Who knows", Encryption.NONE);

		assertThat(evaluator.assess(encrypted, state(3, 0)).notes()).contains("Decoded from base64");
		assertThat(evaluator.assess(weird, state(3, 0)).notes()).contains("Unknown probability label 'Who knows'");
		assertThat(evaluator.assess(weird, state(1, 0)).notes())
				.contains("Below 80% success threshold for 1 lives");
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
