package com.ponomarenko.mugloar.strategy;

import java.util.List;
import java.util.Set;

import com.ponomarenko.mugloar.TestFixtures;
import com.ponomarenko.mugloar.domain.Probability;
import com.ponomarenko.mugloar.domain.ShopItem;
import org.junit.jupiter.api.Test;

import static com.ponomarenko.mugloar.TestFixtures.CLAW_SHARPENING;
import static com.ponomarenko.mugloar.TestFixtures.POTION;
import static com.ponomarenko.mugloar.TestFixtures.SHOP;
import static com.ponomarenko.mugloar.TestFixtures.ad;
import static com.ponomarenko.mugloar.TestFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class GameStrategyTest {

	private final GameStrategy strategy = new GameStrategy(new AdEvaluator(TestFixtures.defaultStrategy()),
			new ShoppingPolicy(TestFixtures.defaultStrategy()));

	@Test
	void shoppingComesBeforeSolving() {
		Decision decision = strategy.decide(state(2, 60), List.of(ad("easy", Probability.SURE_THING, 100)), SHOP,
				Set.of());

		assertThat(decision).isInstanceOfSatisfying(Decision.Buy.class, buy -> {
			assertThat(buy.item()).isEqualTo(POTION);
			assertThat(buy.reason()).contains("Lives are low");
		});
	}

	@Test
	void surplusGoldGoesIntoUpgrades() {
		Decision decision = strategy.decide(state(3, 150), List.of(ad("easy", Probability.SURE_THING, 100)), SHOP,
				Set.of());

		assertThat(decision).isInstanceOfSatisfying(Decision.Buy.class, buy -> {
			assertThat(buy.item()).isEqualTo(CLAW_SHARPENING);
			assertThat(buy.reason()).contains("level up");
		});
	}

	@Test
	void solvesTheBestEligibleAdWhenNothingToBuy() {
		Decision decision = strategy.decide(state(3, 10),
				List.of(ad("meh", Probability.SURE_THING, 5), ad("good", Probability.PIECE_OF_CAKE, 90),
						ad("deadly", Probability.IMPOSSIBLE, 999)),
				SHOP, Set.of());

		assertThat(decision).isInstanceOfSatisfying(Decision.Solve.class, solve -> {
			assertThat(solve.assessment().ad().adId()).isEqualTo("good");
			assertThat(solve.reason()).contains("Best eligible ad");
		});
	}

	@Test
	void buysPotionToRerollWhenNoAdIsSafeEnough() {
		Decision decision = strategy.decide(state(3, 50), List.of(ad("deadly", Probability.IMPOSSIBLE, 999)), SHOP,
				Set.of());

		assertThat(decision).isInstanceOfSatisfying(Decision.Buy.class, buy -> {
			assertThat(buy.item()).isEqualTo(POTION);
			assertThat(buy.reason()).contains("No ad is safe enough");
		});
	}

	@Test
	void takesLeastDangerousAdWhenNothingElseIsPossible() {
		Decision decision = strategy.decide(state(3, 10),
				List.of(ad("deadly", Probability.IMPOSSIBLE, 999), ad("risky", Probability.RISKY, 10),
						ad("fire", Probability.PLAYING_WITH_FIRE, 500)),
				SHOP, Set.of());

		assertThat(decision).isInstanceOfSatisfying(Decision.Solve.class, solve -> {
			assertThat(solve.assessment().ad().adId()).isEqualTo("risky");
			assertThat(solve.reason()).contains("least dangerous");
		});
	}

	@Test
	void failsLoudlyWhenThereAreNoAdsAndNoPotion() {
		List<ShopItem> noPotion = List.of(CLAW_SHARPENING);

		assertThatIllegalStateException()
				.isThrownBy(() -> strategy.decide(state(3, 10), List.of(), noPotion, Set.of()))
				.withMessage("No ads available");
	}
}
