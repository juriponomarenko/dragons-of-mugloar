package com.ponomarenko.mugloar.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.domain.ShopItem;

/**
 * Server-side memory of one game: the latest state, the action history and what has been
 * bought. All mutation happens under the session lock so that a manual click and an
 * autoplay request can never interleave inside one game.
 */
public class GameSession {

	private final ReentrantLock lock = new ReentrantLock();

	private final Instant createdAt = Instant.now();

	private final List<TurnRecord> history = new ArrayList<>();

	private final Map<String, Integer> purchases = new LinkedHashMap<>();

	private volatile GameState state;

	private volatile List<ShopItem> shop;

	private volatile Map<String, String> lastBoard = Map.of();

	public GameSession(GameState initial) {
		this.state = initial;
	}

	public String gameId() {
		return state.gameId();
	}

	public GameState state() {
		return state;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public boolean isOver() {
		return state.isOver();
	}

	/** Runs {@code work} while holding this session's lock. */
	public <T> T locked(Supplier<T> work) {
		lock.lock();
		try {
			return work.get();
		}
		finally {
			lock.unlock();
		}
	}

	void record(TurnRecord turn) {
		lock.lock();
		try {
			history.add(turn);
			state = turn.stateAfter();
			if (turn.action() == ActionType.BUY && turn.success()) {
				purchases.merge(turn.targetId(), 1, Integer::sum);
			}
		}
		finally {
			lock.unlock();
		}
	}

	/** Called when the upstream API reports the game as finished although we still counted lives. */
	void markOver() {
		lock.lock();
		try {
			GameState s = state;
			state = new GameState(s.gameId(), 0, s.gold(), s.level(), s.score(), s.highScore(), s.turn());
		}
		finally {
			lock.unlock();
		}
	}

	public List<TurnRecord> history() {
		lock.lock();
		try {
			return List.copyOf(history);
		}
		finally {
			lock.unlock();
		}
	}

	public Set<String> ownedItemIds() {
		lock.lock();
		try {
			return Set.copyOf(purchases.keySet());
		}
		finally {
			lock.unlock();
		}
	}

	public Map<String, Integer> purchases() {
		lock.lock();
		try {
			return Collections.unmodifiableMap(new LinkedHashMap<>(purchases));
		}
		finally {
			lock.unlock();
		}
	}

	/** Remembers the most recently fetched board so manual actions can be described by ad text. */
	void rememberBoard(List<Ad> ads) {
		lastBoard = ads.stream().collect(Collectors.toUnmodifiableMap(Ad::adId, Ad::message, (a, b) -> a));
	}

	/** The text of an ad from the last fetched board, or {@code null} if it is not on it. */
	public String describeAd(String adId) {
		return lastBoard.get(adId);
	}

	/** The shop listing does not change during a game, so it is fetched once per session. */
	List<ShopItem> shop(Supplier<List<ShopItem>> loader) {
		List<ShopItem> current = shop;
		if (current == null) {
			current = List.copyOf(loader.get());
			shop = current;
		}
		return current;
	}
}
