package com.ponomarenko.mugloar.domain;

import java.util.ArrayList;
import java.util.List;
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

	/** How many words of the text identify a family of ads once names and places are skipped. */
	static final int FAMILY_WORDS = 4;

	public double successRate() {
		return probability.successRate();
	}

	public boolean wasEncrypted() {
		return encryption != Encryption.NONE;
	}

	/** The ad's family, see {@link #family(String, Probability)}. */
	public String family() {
		return family(message, probability);
	}

	/**
	 * The family of an ad: the kind of task it is, regardless of who it is for. The game
	 * generates ads from templates ("Escort X to Y", "Steal super awesome diamond Z from W"),
	 * so the first few words identify the template once names and places, which are the
	 * capitalised words after the first, are skipped. The probability label is part of the key:
	 * "Escort ... to ..." labelled <i>Hmmm</i> and the same text labelled <i>Piece of cake</i>
	 * are different bets, and two failures of the first must not discredit the second.
	 */
	public static String family(String message, Probability probability) {
		if (message == null) {
			return "";
		}
		List<String> words = new ArrayList<>(FAMILY_WORDS);
		String[] tokens = message.trim().split("\\s+");
		for (int i = 0; i < tokens.length && words.size() < FAMILY_WORDS; i++) {
			String word = tokens[i].replaceAll("[^\\p{L}\\p{N}]", "");
			if (word.isEmpty() || (i > 0 && Character.isUpperCase(word.charAt(0)))) {
				continue;
			}
			words.add(word.toLowerCase(Locale.ROOT));
		}
		String label = probability == null ? "" : "|" + probability.name().toLowerCase(Locale.ROOT);
		return String.join(" ", words) + label;
	}
}
