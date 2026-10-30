package com.ponomarenko.mugloar.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;

import com.ponomarenko.mugloar.client.MugloarApiException;
import com.ponomarenko.mugloar.config.AutoplayProperties;
import com.ponomarenko.mugloar.service.AutoplayOutcome;
import com.ponomarenko.mugloar.service.GameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Command-line mode: plays one or more complete games with the strategy and prints a
 * summary. Activated with the {@code autoplay} profile, e.g.
 * {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=autoplay}. The exit code is
 * non-zero when any game finished below the target score (or could not be started), which
 * makes the run usable as a regression check; {@code main} applies it when the context closes.
 * A well-upgraded dragon can go on for hundreds of turns, so each game is capped at
 * {@code mugloar.strategy.max-turns} actions; the server keeps the score either way.
 */
@Component
@Profile("autoplay")
public class AutoplayRunner implements ApplicationRunner, ExitCodeGenerator {

	private static final Logger log = LoggerFactory.getLogger(AutoplayRunner.class);

	private static final int START_ATTEMPTS = 4;

	private static final long RATE_LIMIT_PAUSE_MILLIS = 30_000L;

	private final GameService gameService;

	private final AutoplayProperties properties;

	/** Sleeps for the given number of milliseconds while the API asks us to slow down. */
	private final LongConsumer pause;

	private int exitCode;

	@Autowired
	public AutoplayRunner(GameService gameService, AutoplayProperties properties) {
		this(gameService, properties, AutoplayRunner::sleep);
	}

	/** Lets tests observe the pauses instead of waiting them out. */
	AutoplayRunner(GameService gameService, AutoplayProperties properties, LongConsumer pause) {
		this.gameService = gameService;
		this.properties = properties;
		this.pause = pause;
	}

	@Override
	public void run(ApplicationArguments args) {
		int games = properties.games();
		int targetScore = properties.targetScore();
		List<AutoplayOutcome> outcomes = new ArrayList<>();
		for (int i = 1; i <= games; i++) {
			AutoplayOutcome outcome = playWithPatience(i);
			if (outcome == null) {
				continue;
			}
			outcomes.add(outcome);
			System.out.printf(Locale.ROOT, "Game %d/%d  id=%s  score=%d  turns=%d  level=%d  gold=%d  %s%n", i, games,
					outcome.finalState().gameId(), outcome.finalState().score(), outcome.turns().size(),
					outcome.finalState().level(), outcome.finalState().gold(), outcome.stoppedBecause());
		}
		long reachedTarget = outcomes.stream().filter(o -> o.finalState().score() >= targetScore).count();
		int min = outcomes.stream().mapToInt(o -> o.finalState().score()).min().orElse(0);
		int max = outcomes.stream().mapToInt(o -> o.finalState().score()).max().orElse(0);
		double avg = outcomes.stream().mapToInt(o -> o.finalState().score()).average().orElse(0);
		System.out.printf(Locale.ROOT, "%nPlayed %d game(s): min=%d  avg=%.0f  max=%d  reached %d+ in %d/%d%n", games,
				min, avg, max, targetScore, reachedTarget, games);
		exitCode = reachedTarget == games ? 0 : 1;
		if (exitCode != 0) {
			log.warn("{} game(s) finished below the target score of {}", games - reachedTarget, targetScore);
		}
	}

	/** Plays one game; when the API rate-limits the start, waits and tries again a few times. */
	private AutoplayOutcome playWithPatience(int number) {
		for (int attempt = 1; attempt <= START_ATTEMPTS; attempt++) {
			try {
				// No bound of its own: the strategy's max-turns cap applies.
				return gameService.playNewGame(Integer.MAX_VALUE);
			}
			catch (MugloarApiException ex) {
				if (!ex.isTooManyRequests() || attempt == START_ATTEMPTS) {
					System.out.printf(Locale.ROOT, "Game %d/%d  could not be started: %s%n", number, properties.games(),
							ex.getMessage());
					return null;
				}
				log.info("Rate limited while starting game {}; waiting {} s before trying again", number,
						RATE_LIMIT_PAUSE_MILLIS / 1000);
				pause.accept(RATE_LIMIT_PAUSE_MILLIS);
			}
		}
		return null;
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public int getExitCode() {
		return exitCode;
	}
}
