package com.ponomarenko.mugloar.strategy;

import java.util.List;
import java.util.Map;

import com.ponomarenko.mugloar.TestFixtures;
import com.ponomarenko.mugloar.config.StrategyProperties;
import com.ponomarenko.mugloar.domain.ShopItem;
import org.junit.jupiter.api.Test;

import static com.ponomarenko.mugloar.TestFixtures.CLAW_HONING;
import static com.ponomarenko.mugloar.TestFixtures.CLAW_SHARPENING;
import static com.ponomarenko.mugloar.TestFixtures.GASOLINE;
import static com.ponomarenko.mugloar.TestFixtures.POTION;
import static com.ponomarenko.mugloar.TestFixtures.SHOP;
import static com.ponomarenko.mugloar.TestFixtures.state;
import static org.assertj.core.api.Assertions.assertThat;

class ShoppingPolicyTest {

	/** Default fixture: order cs (100), gas (100), ch (300); reserve 50; rebuy on. */
	private final ShoppingPolicy policy = new ShoppingPolicy(TestFixtures.defaultStrategy());

	@Test
	void buysPotionWhenLivesAreLowAndAffordable() {
		assertThat(policy.choosePurchase(state(2, 50), SHOP, Map.of())).contains(POTION);
		assertThat(policy.choosePurchase(state(1, 999), SHOP, Map.of())).contains(POTION);
	}

	@Test
	void doesNotBuyPotionWhenLivesAreFineOrGoldIsShort() {
		assertThat(policy.choosePurchase(state(3, 50), SHOP, Map.of())).isEmpty();
		assertThat(policy.choosePurchase(state(1, 49), SHOP, Map.of())).isEmpty();
	}

	@Test
	void buysTheFirstUpgradeOnceGoldExceedsTheReserve() {
		// 150 gold: 100 for the upgrade, 50 reserved for a potion
		assertThat(policy.choosePurchase(state(3, 150), SHOP, Map.of())).contains(CLAW_SHARPENING);
		assertThat(policy.choosePurchase(state(3, 149), SHOP, Map.of())).isEmpty();
		// plenty of gold still starts at the bottom of the ladder
		assertThat(policy.choosePurchase(state(3, 1000), SHOP, Map.of())).contains(CLAW_SHARPENING);
	}

	@Test
	void climbsTheLadderInsteadOfRebuyingTheFirstRung() {
		assertThat(policy.choosePurchase(state(3, 500), SHOP, Map.of("cs", 1))).contains(GASOLINE);
		assertThat(policy.choosePurchase(state(3, 500), SHOP, Map.of("cs", 1, "gas", 1))).contains(CLAW_HONING);
	}

	@Test
	void skipsAnUnaffordableRungInsteadOfSavingForIt() {
		// cs and gas bought, next is ch at 300: 349 gold leaves only 299 above the reserve, so cs again
		assertThat(policy.choosePurchase(state(3, 349), SHOP, Map.of("cs", 1, "gas", 1))).contains(CLAW_SHARPENING);
		assertThat(policy.choosePurchase(state(3, 350), SHOP, Map.of("cs", 1, "gas", 1))).contains(CLAW_HONING);
	}

	@Test
	void withRebuyEnabledTheLadderStartsOverAfterARound() {
		assertThat(policy.choosePurchase(state(3, 500), SHOP, Map.of("cs", 1, "gas", 1, "ch", 1)))
				.contains(CLAW_SHARPENING);
		assertThat(policy.choosePurchase(state(3, 500), SHOP, Map.of("cs", 2, "gas", 1, "ch", 1)))
				.contains(GASOLINE);
	}

	@Test
	void withRebuyDisabledEachUpgradeIsBoughtOnce() {
		StrategyProperties props = new StrategyProperties(2, 50, List.of("cs", "gas"), false, 0.8, 0.6, 0.5, 50,
				List.of(), 1.0, 100, List.of(), 2);
		ShoppingPolicy onceOnly = new ShoppingPolicy(props);

		assertThat(onceOnly.choosePurchase(state(3, 500), SHOP, Map.of("cs", 1))).contains(GASOLINE);
		assertThat(onceOnly.choosePurchase(state(3, 500), SHOP, Map.of("cs", 1, "gas", 1))).isEmpty();
		// an unaffordable remaining rung is not replaced by rebuying a cheaper one
		StrategyProperties withHoning = new StrategyProperties(2, 50, List.of("cs", "gas", "ch"), false, 0.8, 0.6, 0.5,
				50, List.of(), 1.0, 100, List.of(), 2);
		assertThat(new ShoppingPolicy(withHoning).choosePurchase(state(3, 349), SHOP, Map.of("cs", 1, "gas", 1)))
				.isEmpty();
	}

	@Test
	void skipsItemsTheShopDoesNotOffer() {
		List<ShopItem> noClaws = List.of(POTION, GASOLINE);

		assertThat(policy.choosePurchase(state(3, 150), noClaws, Map.of())).contains(GASOLINE);
		assertThat(policy.choosePurchase(state(3, 150), List.of(POTION), Map.of())).isEmpty();
	}

	@Test
	void potionTakesPriorityOverUpgrades() {
		assertThat(policy.choosePurchase(state(2, 1000), SHOP, Map.of())).contains(POTION);
	}

	@Test
	void suggestedFlagMatchesTheChoice() {
		assertThat(policy.isSuggested(CLAW_SHARPENING, state(3, 150), SHOP, Map.of())).isTrue();
		assertThat(policy.isSuggested(GASOLINE, state(3, 150), SHOP, Map.of())).isFalse();
		assertThat(policy.isSuggested(POTION, state(3, 150), SHOP, Map.of())).isFalse();
	}
}
