package com.ponomarenko.mugloar.domain;

import com.ponomarenko.mugloar.client.dto.ShopItemDto;

public record ShopItem(String id, String name, int cost) {

	public static ShopItem from(ShopItemDto dto) {
		return new ShopItem(dto.id(), dto.name(), dto.cost());
	}
}
