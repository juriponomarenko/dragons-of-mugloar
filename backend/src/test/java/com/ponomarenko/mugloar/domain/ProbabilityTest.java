package com.ponomarenko.mugloar.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProbabilityTest {

	@ParameterizedTest
	@CsvSource({
			"Sure thing, SURE_THING",
			"Piece of cake, PIECE_OF_CAKE",
			"Walk in the park, WALK_IN_THE_PARK",
			"Quite likely, QUITE_LIKELY",
			"Hmmm...., HMMM",
			"Gamble, GAMBLE",
			"Risky, RISKY",
			"Rather detrimental, RATHER_DETRIMENTAL",
			"Playing with fire, PLAYING_WITH_FIRE",
			"Suicide mission, SUICIDE_MISSION",
			"Impossible, IMPOSSIBLE" })
	void parsesEveryKnownLabel(String label, Probability expected) {
		assertThat(Probability.fromLabel(label)).isEqualTo(expected);
	}

	@Test
	void matchingIgnoresCasePunctuationAndWhitespace() {
		assertThat(Probability.fromLabel("  piece OF cake! ")).isEqualTo(Probability.PIECE_OF_CAKE);
		assertThat(Probability.fromLabel("hmmm")).isEqualTo(Probability.HMMM);
		assertThat(Probability.fromLabel("HMMM........")).isEqualTo(Probability.HMMM);
	}

	@Test
	void unknownAndNullLabelsFallBackToUnknown() {
		assertThat(Probability.fromLabel("Totally new label")).isEqualTo(Probability.UNKNOWN);
		assertThat(Probability.fromLabel(null)).isEqualTo(Probability.UNKNOWN);
		assertThat(Probability.fromLabel("unknown")).isEqualTo(Probability.UNKNOWN);
		assertThat(Probability.isKnownLabel("unknown")).isFalse();
		assertThat(Probability.isKnownLabel("Gamble")).isTrue();
	}

	@Test
	void successRatesAreMonotonicFromSafeToImpossible() {
		Probability[] ordered = { Probability.SURE_THING, Probability.PIECE_OF_CAKE, Probability.WALK_IN_THE_PARK,
				Probability.QUITE_LIKELY, Probability.HMMM, Probability.GAMBLE, Probability.RISKY,
				Probability.RATHER_DETRIMENTAL, Probability.PLAYING_WITH_FIRE, Probability.SUICIDE_MISSION,
				Probability.IMPOSSIBLE };
		for (int i = 1; i < ordered.length; i++) {
			assertThat(ordered[i].successRate()).isLessThan(ordered[i - 1].successRate());
		}
		assertThat(Probability.SURE_THING.successRate()).isEqualTo(1.0);
		assertThat(Probability.IMPOSSIBLE.successRate()).isEqualTo(0.0);
	}
}
