package com.ponomarenko.mugloar.service;

import java.util.ArrayList;
import java.util.List;

import com.ponomarenko.mugloar.client.MugloarApiException;
import com.ponomarenko.mugloar.config.StrategyProperties;
import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.domain.ShopItem;
import com.ponomarenko.mugloar.strategy.Decision;
import com.ponomarenko.mugloar.strategy.GameStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Drives a game with the {@link GameStrategy}: one turn at a time or until the game ends.
 * Transient upstream problems are retried a few times before the run is abandoned, so a
 * single flaky request never ends a promising game.
 */
@Component
public class AutoPlayer {

	private static final Logger log = LoggerFactory.getLogger(AutoPlayer.class);

	static final int MAX_CONSECUTIVE_FAILURES = 3;

	private static final int HTTP_TOO_MANY_REQUESTS = 429;

	private final GameEngine engine;

	private final GameStrategy strategy;

	private final StrategyProperties properties;

	public AutoPlayer(GameEngine engine, GameStrategy strategy, StrategyProperties properties) {
		this.engine = engine;
		this.strategy = strategy;
		this.properties = properties;
	}

	/** Lets the strategy choose and perform exactly one action. Caller holds the session lock. */
	public TurnRecord playTurn(GameSession session) {
		if (session.isOver()) {
			throw new GameOverException(session.gameId());
		}
		GameState state = session.state();
		List<Ad> ads = engine.fetchAds(session);
		List<ShopItem> shop = engine.fetchShop(session);
		Decision decision = strategy.decide(state, ads, shop, session.purchases(), session.failedFamilies());
		return switch (decision) {
			case Decision.Buy buy -> engine.buy(session, buy.item().id(), buy.reason(), true);
			case Decision.Solve solve -> engine.solve(session, solve.assessment().ad().adId(),
					solve.assessment().ad().message(), solve.reason(), true);
		};
	}

	/**
	 * Plays until the game is over or {@code maxTurns} actions have been taken in this run.
	 * Caller holds the session lock.
	 */
	public AutoplayOutcome playToEnd(GameSession session, int maxTurns) {
		int limit = Math.min(maxTurns, properties.maxTurns());
		List<TurnRecord> turns = new ArrayList<>();
		int consecutiveFailures = 0;
		String stoppedBecause = "Game over";

		while (turns.size() < limit) {
			if (session.isOver()) {
				break;
			}
			try {
				turns.add(playTurn(session));
				consecutiveFailures = 0;
			}
			catch (InvalidActionException ex) {
				// Typically the chosen ad expired between listing and solving: fetch a fresh board and go on.
				consecutiveFailures++;
				log.info("Game {}: {} (attempt {}/{})", session.gameId(), ex.getMessage(), consecutiveFailures,
						MAX_CONSECUTIVE_FAILURES);
				if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
					stoppedBecause = "Stopped after repeated rejected actions: " + ex.getMessage();
					break;
				}
			}
			catch (MugloarApiException ex) {
				consecutiveFailures++;
				log.warn("Game {}: upstream error '{}' (attempt {}/{})", session.gameId(), ex.getMessage(),
						consecutiveFailures, MAX_CONSECUTIVE_FAILURES);
				if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
					stoppedBecause = "Stopped after repeated upstream errors: " + ex.getMessage();
					break;
				}
				pauseBeforeRetry(consecutiveFailures, ex.getStatus());
			}
			catch (IllegalStateException ex) {
				stoppedBecause = "Stopped: " + ex.getMessage();
				break;
			}
		}
		if (!session.isOver() && turns.size() >= limit) {
			stoppedBecause = "Reached the turn limit of " + limit;
		}
		GameState finalState = session.state();
		log.info("Game {} autoplay finished: score={}, turns={}, lives={}, reason='{}'", session.gameId(),
				finalState.score(), turns.size(), finalState.lives(), stoppedBecause);
		return new AutoplayOutcome(finalState, turns, session.isOver(), stoppedBecause);
	}

	/**
	 * Rate limiting (HTTP 429) needs tens of seconds, not milliseconds, before the next try:
	 * the three attempts together wait a full minute.
	 */
	private static void pauseBeforeRetry(int attempt, int status) {
		long millis = status == HTTP_TOO_MANY_REQUESTS ? 10_000L * attempt : 250L * attempt;
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}
}
