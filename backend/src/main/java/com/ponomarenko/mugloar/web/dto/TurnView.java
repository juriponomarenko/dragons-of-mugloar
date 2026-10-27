package com.ponomarenko.mugloar.web.dto;

import java.time.Instant;

public record TurnView(int turn, String action, String targetId, String description, boolean success, String message,
		String reason, boolean automated, GameView state, Instant at) {
}
