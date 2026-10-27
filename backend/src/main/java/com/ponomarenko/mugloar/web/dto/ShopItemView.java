package com.ponomarenko.mugloar.web.dto;

public record ShopItemView(String id, String name, int cost, boolean affordable, int timesBought, boolean suggested) {
}
