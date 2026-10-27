package com.ponomarenko.mugloar.client.dto;

public record GameStartResponse(String gameId, int lives, int gold, int level, int score, int highScore, int turn) {
}
