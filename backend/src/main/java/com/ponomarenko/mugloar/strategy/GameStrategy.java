package com.ponomarenko.mugloar.strategy;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.ponomarenko.mugloar.config.StrategyProperties;
import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.domain.ShopItem;

import org.springframework.stereotype.Component;

/**
 * Combines {@link ShoppingPolicy} and {@link AdEvaluator} into a single decision per turn.
 */
@Component
public class GameStrategy {

	private final AdEvaluator adEvaluator;

	private final ShoppingPolicy shoppingPolicy;

	public GameStrategy(AdEvaluator adEvaluator, ShoppingPolicy shoppingPolicy) {
		this.adEvaluator = adEvaluator;
		this.shoppingPolicy = shoppingPolicy;
	}

	public Decision decide(GameState state, List<Ad> ads, List<ShopItem> shop, Collection<String> ownedItemIds) {
		Optional<ShopItem> purchase = shoppingPolicy.choosePurchase(state, shop, ownedItemIds);
		if (purchase.isPresent()) {
			ShopItem item = purchase.get();
			String reason = StrategyProperties.HEALING_POTION_ID.equals(item.id())
					? "Lives are low (" + state.lives() + "), buying a healing potion"
					: "Surplus gold, investing in '" + item.name() + "' to level up";
			return new Decision.Buy(item, reason);
		}

		List<AdAssessment> ranked = adEvaluator.evaluate(ads, state);
		Optional<AdAssessment> best = ranked.stream().filter(AdAssessment::eligible).findFirst();
		if (best.isPresent()) {
			AdAssessment a = best.get();
			return new Decision.Solve(a, String.format(Locale.ROOT,
					"Best eligible ad: %s, reward %d, expected value %.1f", a.ad().probabilityLabel(), a.ad().reward(),
					a.expectedValue()));
		}

		// Nothing safe enough on the board. A potion both adds a life and rolls the board over.
		Optional<ShopItem> potion = shop.stream()
				.filter(i -> StrategyProperties.HEALING_POTION_ID.equals(i.id()))
				.filter(i -> i.cost() <= state.gold())
				.findFirst();
		if (potion.isPresent()) {
			return new Decision.Buy(potion.get(), "No ad is safe enough; buying a potion to wait for a better board");
		}

		// Last resort: the least dangerous ad available.
		AdAssessment fallback = ranked.stream()
				.max(Comparator.comparingDouble(AdAssessment::successRate)
						.thenComparingDouble(AdAssessment::expectedValue))
				.orElseThrow(() -> new IllegalStateException("No ads available"));
		return new Decision.Solve(fallback, "No ad meets the safety threshold and no potion is affordable; "
				+ "taking the least dangerous one (" + fallback.ad().probabilityLabel() + ")");
	}
}
