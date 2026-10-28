package com.ponomarenko.mugloar;

import java.util.List;

import com.ponomarenko.mugloar.config.StrategyProperties;
import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.Encryption;
import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.domain.Probability;
import com.ponomarenko.mugloar.domain.ShopItem;

/** Shared builders for test data. */
public final class TestFixtures {

	public static final ShopItem POTION = new ShopItem("hpot", "Healing potion", 50);

	public static final ShopItem CLAW_SHARPENING = new ShopItem("cs", "Claw Sharpening", 100);

	public static final ShopItem GASOLINE = new ShopItem("gas", "Gasoline", 100);

	public static final ShopItem CLAW_HONING = new ShopItem("ch", "Claw Honing", 300);

	public static final List<ShopItem> SHOP = List.of(POTION, CLAW_SHARPENING, GASOLINE, CLAW_HONING);

	private TestFixtures() {
	}

	public static StrategyProperties defaultStrategy() {
		return new StrategyProperties(2, 50, List.of("cs", "gas", "ch"), true, 0.8, 0.6, 0.5, 50,
				List.of("steal", "kidnap"), 0.6, 5000, List.of("super awesome diamond"), 2);
	}

	public static GameState state(int lives, int gold) {
		return new GameState("game1", lives, gold, 0, 0, 0, 0);
	}

	public static Ad ad(String id, Probability probability, int reward) {
		return ad(id, "Help someone with " + id, probability, reward);
	}

	public static Ad ad(String id, String message, Probability probability, int reward) {
		return new Ad(id, message, reward, 5, probability, probability.label(), Encryption.NONE);
	}
}
