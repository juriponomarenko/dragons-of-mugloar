package com.ponomarenko.mugloar.service;

/**
 * The requested action is well-formed but cannot be performed in the current game state, e.g.
 * buying an item the player cannot afford or solving an ad that has expired.
 */
public class InvalidActionException extends RuntimeException {

	public InvalidActionException(String message) {
		super(message);
	}
}
