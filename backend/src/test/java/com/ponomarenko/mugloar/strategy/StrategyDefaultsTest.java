package com.ponomarenko.mugloar.strategy;

import java.util.List;
import java.util.Map;

import com.ponomarenko.mugloar.config.StrategyProperties;
import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.Probability;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static com.ponomarenko.mugloar.TestFixtures.CLAW_SHARPENING;
import static com.ponomarenko.mugloar.TestFixtures.POTION;
import static com.ponomarenko.mugloar.TestFixtures.SHOP;
import static com.ponomarenko.mugloar.TestFixtures.ad;
import static com.ponomarenko.mugloar.TestFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The strategy as it ships: the properties are bound by Spring Boot with nothing overridden,
 * so these tests fail when a default drifts away from what the README promises.
 */
class StrategyDefaultsTest {

	@EnableConfigurationProperties(StrategyProperties.class)
	static class Config {
	}

	private static StrategyProperties defaults;

	private static AdEvaluator evaluator;

	private static GameStrategy strategy;

	@BeforeAll
	static void bindTheShippingDefaults() {
		new ApplicationContextRunner().withUserConfiguration(Config.class)
				.run(context -> defaults = context.getBean(StrategyProperties.class));
		evaluator = new AdEvaluator(defaults);
		strategy = new GameStrategy(evaluator, new ShoppingPolicy(defaults));
	}

	@Test
	void theDefaultsAreTheOnesTheReadmeDescribes() {
		assertThat(defaults.potionLivesThreshold()).isEqualTo(3);
		assertThat(defaults.goldReserve()).isEqualTo(100);
		assertThat(defaults.lifeValue()).isEqualTo(100);
		assertThat(defaults.minSuccessRateCritical()).isEqualTo(0.8);
		assertThat(defaults.minSuccessRateLow()).isEqualTo(0.6);
		assertThat(defaults.minSuccessRateNormal()).isEqualTo(0.6);
		assertThat(defaults.maxTurns()).isEqualTo(1000);
		assertThat(defaults.rebuyUpgrades()).isTrue();
		assertThat(defaults.upgradeOrder()).hasSize(10).startsWith("cs").endsWith("wingpotmax");
		assertThat(defaults.riskyKeywords()).contains("steal", "kill");
		assertThat(defaults.riskyKeywordPenalty()).isEqualTo(0.6);
		assertThat(defaults.trapPhrases()).containsExactly("super awesome diamond");
		assertThat(defaults.distrustAfterFailures()).isEqualTo(2);
	}

	@Test
	void aGambleIsNeverTakenAtFullHealthButAHmmmIs() {
		Ad gamble = ad("gamble", Probability.GAMBLE, 100);
		Ad hmmm = ad("hmmm", Probability.HMMM, 100);
		Ad quiteLikely = ad("likely", Probability.QUITE_LIKELY, 100);

		assertThat(evaluator.evaluate(List.of(gamble), state(3, 500), SHOP, Map.of()).get(0).eligible()).isFalse();
		assertThat(evaluator.evaluate(List.of(hmmm), state(3, 500), SHOP, Map.of()).get(0).eligible()).isTrue();
		assertThat(evaluator.evaluate(List.of(hmmm), state(1, 500), SHOP, Map.of()).get(0).eligible()).isFalse();
		assertThat(evaluator.evaluate(List.of(quiteLikely), state(1, 500), SHOP, Map.of()).get(0).eligible()).isTrue();
	}

	@Test
	void theFirstFiftyGoldBuysAPotionAndUpgradesWaitForTheReserve() {
		List<Ad> ads = List.of(ad("easy", Probability.SURE_THING, 20));

		assertThat(strategy.decide(state(3, 50), ads, SHOP, Map.of(), Map.of()))
				.isInstanceOfSatisfying(Decision.Buy.class, buy -> assertThat(buy.item()).isEqualTo(POTION));
		// four lives and 150 gold: 50 above the reserve buys nothing, so the dragon earns instead
		assertThat(strategy.decide(state(4, 150), ads, SHOP, Map.of(), Map.of())).isInstanceOf(Decision.Solve.class);
		// 200 gold: the first rung of the ladder is affordable above the reserve
		assertThat(strategy.decide(state(4, 200), ads, SHOP, Map.of(), Map.of()))
				.isInstanceOfSatisfying(Decision.Buy.class, buy -> assertThat(buy.item()).isEqualTo(CLAW_SHARPENING));
	}

	@Test
	void theKnownTrapIsNeverTakenHoweverRichTheLabelLooks() {
		Ad trap = ad("trap", "Steal super awesome diamond pan from Kassiopeia Clemens", Probability.SURE_THING, 322);
		Ad honest = ad("honest", "Help Bob fix his fence", Probability.PIECE_OF_CAKE, 30);

		Decision decision = strategy.decide(state(4, 150), List.of(trap, honest), SHOP, Map.of(), Map.of());

		assertThat(decision).isInstanceOfSatisfying(Decision.Solve.class,
				solve -> assertThat(solve.assessment().ad().adId()).isEqualTo("honest"));
	}

	@Test
	void aLifeIsWorthAHundredGoldAndMoreAsTheyRunOut() {
		assertThat(evaluator.lifeCost(3)).isEqualTo(100);
		assertThat(evaluator.lifeCost(2)).isEqualTo(200);
		assertThat(evaluator.lifeCost(1)).isEqualTo(400);
	}

	@Test
	void aShadyAdNeedsAMuchBiggerRewardToWin() {
		Ad honest = ad("honest", "Help Bob fix his fence", Probability.WALK_IN_THE_PARK, 100);
		Ad shady = ad("shady", "Kill the guard of Bob", Probability.WALK_IN_THE_PARK, 150);

		List<AdAssessment> ranked = evaluator.evaluate(List.of(shady, honest), state(4, 500), SHOP, Map.of());

		assertThat(ranked.get(0).ad().adId()).isEqualTo("honest");
		assertThat(ranked.get(1).notes()).contains("May hurt reputation");
	}
}
