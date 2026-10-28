package com.ponomarenko.mugloar.strategy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.ponomarenko.mugloar.config.StrategyProperties;
import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.domain.ShopItem;

import org.springframework.stereotype.Component;

/**
 * Decides what, if anything, to buy before attempting the next ad.
 * <ol>
 * <li>Survival first: when lives are at or below the threshold and a healing potion is
 * affordable, buy one.</li>
 * <li>Otherwise climb the upgrade ladder: rungs are the configured upgrade order, and the next
 * rung is the one bought the fewest times so far (ties go to the earlier entry), so every
 * upgrade is bought once before any is bought twice. The first rung in that order that the
 * gold above the potion reserve covers is bought; an unaffordable rung is skipped for now, not
 * saved for, because a cheap level-up today beats an expensive one in ten turns. With
 * {@code rebuyUpgrades} off the ladder ends after one round.</li>
 * </ol>
 */
@Component
public class ShoppingPolicy {

	private final StrategyProperties properties;

	public ShoppingPolicy(StrategyProperties properties) {
		this.properties = properties;
	}

	/**
	 * @param purchases how many times each item id has been bought in this game
	 */
	public Optional<ShopItem> choosePurchase(GameState state, List<ShopItem> shop, Map<String, Integer> purchases) {
		Map<String, ShopItem> byId = shop.stream().collect(Collectors.toMap(ShopItem::id, Function.identity(), (a, b) -> a));

		ShopItem potion = byId.get(StrategyProperties.HEALING_POTION_ID);
		if (potion != null && state.lives() <= properties.potionLivesThreshold() && state.gold() >= potion.cost()) {
			return Optional.of(potion);
		}

		int spendable = state.gold() - properties.goldReserve();
		return ladder(byId, purchases).stream().filter(item -> item.cost() <= spendable).findFirst();
	}

	/** The upgrades still to buy, next rung first; empty when the ladder is finished. */
	List<ShopItem> ladder(Map<String, ShopItem> shopById, Map<String, Integer> purchases) {
		List<String> order = properties.upgradeOrder();
		return IntStream.range(0, order.size())
				.boxed()
				.filter(i -> shopById.containsKey(order.get(i)))
				.filter(i -> properties.rebuyUpgrades() || purchases.getOrDefault(order.get(i), 0) == 0)
				.sorted(Comparator.<Integer>comparingInt(i -> purchases.getOrDefault(order.get(i), 0)).thenComparing(i -> i))
				.map(i -> shopById.get(order.get(i)))
				.toList();
	}

	/** Whether this item is the one the policy would buy right now; used to hint the UI. */
	public boolean isSuggested(ShopItem item, GameState state, List<ShopItem> shop, Map<String, Integer> purchases) {
		return choosePurchase(state, shop, purchases).map(s -> s.id().equals(item.id())).orElse(false);
	}
}
