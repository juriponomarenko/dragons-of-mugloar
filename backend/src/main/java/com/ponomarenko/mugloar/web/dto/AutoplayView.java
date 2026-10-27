package com.ponomarenko.mugloar.web.dto;

import java.util.List;

public record AutoplayView(GameView state, List<TurnView> turns, boolean gameOver, String stoppedBecause) {
}
