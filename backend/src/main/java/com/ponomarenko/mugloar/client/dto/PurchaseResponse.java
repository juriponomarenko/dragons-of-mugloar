package com.ponomarenko.mugloar.client.dto;

public record PurchaseResponse(boolean shoppingSuccess, int gold, int lives, int level, int turn) {
}
