package com.ponomarenko.mugloar.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

import com.ponomarenko.mugloar.client.MugloarApiException;
import com.ponomarenko.mugloar.config.StrategyProperties;
import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.domain.ShopItem;
import com.ponomarenko.mugloar.strategy.Decision;
import com.ponomarenko.mugloar.strategy.GameStrategy;
import com.ponomarenko.mugloar.strategy.NoMoveAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Drives a game with the {@link GameStrategy}: one turn at a time or until the game ends.
 * Transient upstream problems are retried a few times before the run is abandoned, so a
 * single flaky request never ends a promising game. A rate limit (HTTP 429) is not an error
 * of ours: a strong dragon makes around ten requests a second and the API starts refusing
 * after roughly six hundred, so the player waits it out, longer each time, for a few minutes.
 */
@Component
public class AutoPlayer {

	private static final Logger log = LoggerFactory.getLogger(AutoPlayer.class);

	static final int MAX_CONSECUTIVE_FAILURES = 3;

	/** How many rate-limit waits in a row the player sits through before giving up. */
	static final int MAX_RATE_LIMIT_WAITS = 6;

	private static final long RETRY_PAUSE_MILLIS = 250L;

	private static final long RATE_LIMIT_STEP_MILLIS = 15_000L;

	private static final long RATE_LIMIT_MAX_PAUSE_MILLIS = 60_000L;

	private final GameEngine engine;

	private final GameStrategy strategy;

	private final StrategyProperties properties;

	/** Sleeps for the given number of milliseconds between retries. */
	private final LongConsumer pause;

	@Autowired
	public AutoPlayer(GameEngine engine, GameStrategy strategy, StrategyProperties properties) {
		this(engine, strategy, properties, AutoPlayer::sleep);
	}

	/** Lets tests observe the pauses instead of waiting them out. */
	AutoPlayer(GameEngine engine, GameStrategy strategy, StrategyProperties properties, LongConsumer pause) {
		this.engine = engine;
		this.strategy = strategy;
		this.properties = properties;
		this.pause = pause;
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
			case Decision.Solve solve -> engine.solve(session, solve.assessment().ad().adId(), solve.reason(), true);
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
		int rateLimitWaits = 0;
		String stoppedBecause = "Game over";

		while (turns.size() < limit) {
			if (session.isOver()) {
				break;
			}
			try {
				turns.add(playTurn(session));
				consecutiveFailures = 0;
				rateLimitWaits = 0;
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
				if (ex.isTooManyRequests()) {
					rateLimitWaits++;
					if (rateLimitWaits > MAX_RATE_LIMIT_WAITS) {
						stoppedBecause = "Stopped: the API kept rate-limiting us through " + MAX_RATE_LIMIT_WAITS + " waits";
						break;
					}
					long millis = rateLimitPause(rateLimitWaits);
					log.info("Game {}: rate limited by the API, waiting {} s (wait {}/{})", session.gameId(),
							millis / 1000, rateLimitWaits, MAX_RATE_LIMIT_WAITS);
					pause.accept(millis);
					continue;
				}
				consecutiveFailures++;
				log.warn("Game {}: upstream error '{}' (attempt {}/{})", session.gameId(), ex.getMessage(),
						consecutiveFailures, MAX_CONSECUTIVE_FAILURES);
				if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
					stoppedBecause = "Stopped after repeated upstream errors: " + ex.getMessage();
					break;
				}
				pause.accept(RETRY_PAUSE_MILLIS * consecutiveFailures);
			}
			catch (NoMoveAvailableException ex) {
				stoppedBecause = "Stopped: " + ex.getMessage();
				break;
			}
			catch (GameOverException ex) {
				// The API says the game is over although we still counted lives; the session is closed already.
				stoppedBecause = "Game over (reported by the API)";
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

	/** 15, 30, 45 seconds, then a minute at a time: the limit window is long and the block with it. */
	static long rateLimitPause(int waits) {
		return Math.min(RATE_LIMIT_MAX_PAUSE_MILLIS, RATE_LIMIT_STEP_MILLIS * waits);
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}
}
