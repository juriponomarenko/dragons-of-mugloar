package com.ponomarenko.mugloar.strategy;

/**
 * The strategy has no move to make: the board is empty and nothing useful can be bought. The
 * autoplayer ends its run on it and the API reports it as an action that is not possible.
 */
public class NoMoveAvailableException extends RuntimeException {

	public NoMoveAvailableException(String message) {
		super(message);
	}
}
