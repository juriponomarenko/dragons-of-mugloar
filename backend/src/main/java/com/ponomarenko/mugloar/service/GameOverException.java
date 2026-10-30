package com.ponomarenko.mugloar.service;

public class GameOverException extends RuntimeException {

	public GameOverException(String gameId) {
		super("Game '" + gameId + "' is over; start a new game to continue playing");
	}

	public GameOverException(String gameId, String reason) {
		super("Game '" + gameId + "' is over (" + reason + "); start a new game to continue playing");
	}
}
