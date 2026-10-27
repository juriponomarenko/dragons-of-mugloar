package com.ponomarenko.mugloar.domain;

/**
 * A decoded message-board ad (a task the dragon can attempt).
 *
 * @param adId identifier to pass to the solve endpoint (already decoded)
 * @param message human readable task description
 * @param reward gold granted on success
 * @param expiresIn number of turns the ad stays on the board
 * @param probability parsed probability label
 * @param probabilityLabel original (decoded) label text, kept for display
 * @param encryption how the ad was encoded on the wire
 */
public record Ad(String adId, String message, int reward, int expiresIn, Probability probability,
		String probabilityLabel, Encryption encryption) {

	public double successRate() {
		return probability.successRate();
	}

	public boolean wasEncrypted() {
		return encryption != Encryption.NONE;
	}
}
