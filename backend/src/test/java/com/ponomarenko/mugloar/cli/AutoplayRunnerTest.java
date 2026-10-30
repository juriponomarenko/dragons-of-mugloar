package com.ponomarenko.mugloar.cli;

import java.util.ArrayList;
import java.util.List;

import com.ponomarenko.mugloar.client.MugloarApiException;
import com.ponomarenko.mugloar.config.AutoplayProperties;
import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.service.AutoplayOutcome;
import com.ponomarenko.mugloar.service.GameService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoplayRunnerTest {

	private final GameService gameService = mock(GameService.class);

	/** Milliseconds the runner wanted to wait; nothing actually sleeps. */
	private final List<Long> pauses = new ArrayList<>();

	private static AutoplayOutcome finishedWith(int score) {
		return new AutoplayOutcome(new GameState("g", 0, 10, 5, score, score, 50), List.of(), true, "Game over");
	}

	private AutoplayRunner runner(int games) {
		return new AutoplayRunner(gameService, new AutoplayProperties(games, 1000), pauses::add);
	}

	@Test
	void exitCodeIsZeroOnlyWhenEveryGameReachesTheTarget() {
		when(gameService.playNewGame(anyInt())).thenReturn(finishedWith(1200), finishedWith(999));
		AutoplayRunner oneMiss = runner(2);
		oneMiss.run(null);
		assertThat(oneMiss.getExitCode()).isEqualTo(1);

		when(gameService.playNewGame(anyInt())).thenReturn(finishedWith(1200), finishedWith(1000));
		AutoplayRunner allHits = runner(2);
		allHits.run(null);
		assertThat(allHits.getExitCode()).isZero();
	}

	@Test
	void aGameThatCannotBeStartedCountsAsAMiss() {
		when(gameService.playNewGame(anyInt()))
				.thenThrow(new MugloarApiException(502, "", "upstream down"))
				.thenReturn(finishedWith(1500));

		AutoplayRunner runner = runner(2);
		runner.run(null);

		assertThat(runner.getExitCode()).isEqualTo(1);
		assertThat(pauses).isEmpty();
	}

	@Test
	void rateLimitOnStartIsWaitedOutAndRetried() {
		when(gameService.playNewGame(anyInt()))
				.thenThrow(new MugloarApiException(MugloarApiException.TOO_MANY_REQUESTS, "", "slow down"))
				.thenReturn(finishedWith(1500));

		AutoplayRunner runner = runner(1);
		runner.run(null);

		assertThat(runner.getExitCode()).isZero();
		assertThat(pauses).containsExactly(30_000L);
		verify(gameService, times(2)).playNewGame(anyInt());
	}

	@Test
	void givesUpStartingAGameAfterRepeatedRateLimits() {
		when(gameService.playNewGame(anyInt()))
				.thenThrow(new MugloarApiException(MugloarApiException.TOO_MANY_REQUESTS, "", "slow down"));

		AutoplayRunner runner = runner(1);
		runner.run(null);

		assertThat(runner.getExitCode()).isEqualTo(1);
		assertThat(pauses).hasSize(3);
		verify(gameService, times(4)).playNewGame(anyInt());
	}
}
