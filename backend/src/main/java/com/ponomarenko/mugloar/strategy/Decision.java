package com.ponomarenko.mugloar.strategy;

import com.ponomarenko.mugloar.domain.ShopItem;

/**
 * What the strategy wants to do next: either buy an item or attempt an ad.
 */
public sealed interface Decision {

	String reason();

	record Buy(ShopItem item, String reason) implements Decision {
	}

	record Solve(AdAssessment assessment, String reason) implements Decision {
	}
}
