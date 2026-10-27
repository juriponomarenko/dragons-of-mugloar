package com.ponomarenko.mugloar.strategy;

import java.util.List;
import java.util.Set;

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

	private final ShoppingPolicy policy = new ShoppingPolicy(TestFixtures.defaultStrategy());

	@Test
	void buysPotionWhenLivesAreLowAndAffordable() {
		assertThat(policy.choosePurchase(state(2, 50), SHOP, Set.of())).contains(POTION);
		assertThat(policy.choosePurchase(state(1, 999), SHOP, Set.of())).contains(POTION);
	}

	@Test
	void doesNotBuyPotionWhenLivesAreFineOrGoldIsShort() {
		assertThat(policy.choosePurchase(state(3, 50), SHOP, Set.of())).isEmpty();
		assertThat(policy.choosePurchase(state(1, 49), SHOP, Set.of())).isEmpty();
	}

	@Test
	void buysUpgradesInOrderWhileKeepingAReserve() {
		// 150 gold: 100 for the upgrade, 50 reserved for a potion
		assertThat(policy.choosePurchase(state(3, 150), SHOP, Set.of())).contains(CLAW_SHARPENING);
		assertThat(policy.choosePurchase(state(3, 149), SHOP, Set.of())).isEmpty();
		// 350 gold covers the 300 item plus the reserve, but the cheaper upgrade comes first in the order
		assertThat(policy.choosePurchase(state(3, 350), SHOP, Set.of())).contains(CLAW_SHARPENING);
	}

	@Test
	void skipsItemsTheShopDoesNotOffer() {
		List<ShopItem> noClaws = List.of(POTION, GASOLINE);

		assertThat(policy.choosePurchase(state(3, 150), noClaws, Set.of())).contains(GASOLINE);
	}

	@Test
	void withRebuyDisabledEachUpgradeIsBoughtOnce() {
		StrategyProperties props = new StrategyProperties(2, 50, List.of("cs", "gas"), false, 0.8, 0.6, 0.5, 50,
				List.of(), 1.0, 100);
		ShoppingPolicy onceOnly = new ShoppingPolicy(props);

		assertThat(onceOnly.choosePurchase(state(3, 500), SHOP, Set.of("cs"))).contains(GASOLINE);
		assertThat(onceOnly.choosePurchase(state(3, 500), SHOP, Set.of("cs", "gas"))).isEmpty();

		StrategyProperties withHoning = new StrategyProperties(2, 50, List.of("cs", "gas", "ch"), false, 0.8, 0.6, 0.5,
				50, List.of(), 1.0, 100);
		assertThat(new ShoppingPolicy(withHoning).choosePurchase(state(3, 350), SHOP, Set.of("cs", "gas")))
				.contains(CLAW_HONING);
	}

	@Test
	void withRebuyEnabledCheapestUpgradeIsBoughtAgain() {
		assertThat(policy.choosePurchase(state(3, 500), SHOP, Set.of("cs", "gas", "ch"))).contains(CLAW_SHARPENING);
	}

	@Test
	void potionTakesPriorityOverUpgrades() {
		assertThat(policy.choosePurchase(state(2, 1000), SHOP, Set.of())).contains(POTION);
	}

	@Test
	void suggestedFlagMatchesTheChoice() {
		assertThat(policy.isSuggested(CLAW_SHARPENING, state(3, 150), SHOP, Set.of())).isTrue();
		assertThat(policy.isSuggested(GASOLINE, state(3, 150), SHOP, Set.of())).isFalse();
		assertThat(policy.isSuggested(POTION, state(3, 150), SHOP, Set.of())).isFalse();
	}
}
