package com.ponomarenko.mugloar.domain;

import java.util.Arrays;
import java.util.Locale;

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

	/** How many leading words identify a family of ads ("steal super awesome diamond"). */
	static final int FAMILY_WORDS = 4;

	public double successRate() {
		return probability.successRate();
	}

	public boolean wasEncrypted() {
		return encryption != Encryption.NONE;
	}

	/** The ad's family: its first few words, which the game reuses across ads of one kind. */
	public String family() {
		return family(message);
	}

	public static String family(String message) {
		if (message == null) {
			return "";
		}
		String[] words = message.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]", " ").trim().split("\\s+");
		return String.join(" ", Arrays.copyOf(words, Math.min(FAMILY_WORDS, words.length)));
	}
}
