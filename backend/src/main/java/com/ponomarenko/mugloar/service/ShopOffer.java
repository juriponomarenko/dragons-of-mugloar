package com.ponomarenko.mugloar.service;

import com.ponomarenko.mugloar.domain.ShopItem;

/**
 * A shop item enriched with information about the current game.
 *
 * @param item the item
 * @param affordable whether the player currently has enough gold
 * @param timesBought how many times it has been bought in this game
 * @param suggested whether the strategy would buy it right now
 */
public record ShopOffer(ShopItem item, boolean affordable, int timesBought, boolean suggested) {
}
