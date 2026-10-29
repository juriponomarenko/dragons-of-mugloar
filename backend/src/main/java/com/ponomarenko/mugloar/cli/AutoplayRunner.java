package com.ponomarenko.mugloar.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.ponomarenko.mugloar.client.MugloarApiException;
import com.ponomarenko.mugloar.service.AutoplayOutcome;
import com.ponomarenko.mugloar.service.GameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Command-line mode: plays one or more complete games with the strategy and prints a
 * summary. Activated with the {@code autoplay} profile, e.g.
 * {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=autoplay}. The process exit code
 * is non-zero when any game finished below the target score, which makes it usable as a
 * regression check. A well-upgraded dragon never dies, so each game is capped at
 * {@code mugloar.autoplay.max-turns} actions; the server keeps the score either way.
 */
@Component
@Profile("autoplay")
public class AutoplayRunner implements ApplicationRunner, ExitCodeGenerator {

	private static final Logger log = LoggerFactory.getLogger(AutoplayRunner.class);

	private static final int START_ATTEMPTS = 4;

	private static final int RATE_LIMIT_PAUSE_SECONDS = 30;

	private final GameService gameService;

	private final int games;

	private final int targetScore;

	private final int maxTurns;

	private int exitCode;

	public AutoplayRunner(GameService gameService, @Value("${mugloar.autoplay.games:1}") int games,
			@Value("${mugloar.autoplay.target-score:1000}") int targetScore,
			@Value("${mugloar.autoplay.max-turns:1000}") int maxTurns) {
		this.gameService = gameService;
		this.games = games;
		this.targetScore = targetScore;
		this.maxTurns = maxTurns;
	}

	@Override
	public void run(ApplicationArguments args) {
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
				return gameService.playNewGame(maxTurns);
			}
			catch (MugloarApiException ex) {
				if (!ex.isTooManyRequests() || attempt == START_ATTEMPTS) {
					System.out.printf(Locale.ROOT, "Game %d/%d  could not be started: %s%n", number, games,
							ex.getMessage());
					return null;
				}
				log.info("Rate limited while starting game {}; waiting {} s before trying again", number,
						RATE_LIMIT_PAUSE_SECONDS);
				sleepSeconds(RATE_LIMIT_PAUSE_SECONDS);
			}
		}
		return null;
	}

	private static void sleepSeconds(int seconds) {
		try {
			Thread.sleep(seconds * 1000L);
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
