package com.ponomarenko.mugloar.strategy;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ponomarenko.mugloar.config.StrategyProperties;
import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.domain.ShopItem;

import org.springframework.stereotype.Component;

/**
 * Decides what, if anything, to buy before attempting the next ad.
 * <ol>
 * <li>Survival first: when lives are at or below the threshold and a healing potion is
 * affordable, buy one.</li>
 * <li>Otherwise invest surplus gold in upgrades (which raise the dragon's level and make ads
 * easier), in the configured order, always keeping a reserve for the next potion.</li>
 * </ol>
 */
@Component
public class ShoppingPolicy {

	private final StrategyProperties properties;

	public ShoppingPolicy(StrategyProperties properties) {
		this.properties = properties;
	}

	public Optional<ShopItem> choosePurchase(GameState state, List<ShopItem> shop, Collection<String> ownedItemIds) {
		Map<String, ShopItem> byId = shop.stream().collect(Collectors.toMap(ShopItem::id, Function.identity(), (a, b) -> a));

		ShopItem potion = byId.get(StrategyProperties.HEALING_POTION_ID);
		if (potion != null && state.lives() <= properties.potionLivesThreshold() && state.gold() >= potion.cost()) {
			return Optional.of(potion);
		}

		int spendable = state.gold() - properties.goldReserve();
		for (String itemId : properties.upgradeOrder()) {
			ShopItem item = byId.get(itemId);
			if (item == null || item.cost() > spendable) {
				continue;
			}
			if (!ownedItemIds.contains(itemId) || properties.rebuyUpgrades()) {
				return Optional.of(item);
			}
		}
		return Optional.empty();
	}

	/** Whether this item is the one the policy would buy right now; used to hint the UI. */
	public boolean isSuggested(ShopItem item, GameState state, List<ShopItem> shop, Collection<String> ownedItemIds) {
		return choosePurchase(state, shop, ownedItemIds).map(s -> s.id().equals(item.id())).orElse(false);
	}
}
