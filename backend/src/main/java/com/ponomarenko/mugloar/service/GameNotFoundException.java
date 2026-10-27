package com.ponomarenko.mugloar.service;

public class GameNotFoundException extends RuntimeException {

	public GameNotFoundException(String gameId) {
		super("No game with id '" + gameId + "' is known to this server");
	}
}
