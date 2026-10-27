package com.ponomarenko.mugloar.web.dto;

import java.time.Instant;
import java.util.Map;

public record GameView(String gameId, int lives, int gold, int level, int score, int highScore, int turn,
		boolean gameOver, Map<String, Integer> purchases, Instant createdAt) {
}
