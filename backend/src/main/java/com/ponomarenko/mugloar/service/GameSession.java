package com.ponomarenko.mugloar.service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.domain.ShopItem;

/**
 * Server-side memory of one game: the latest state, the action history, what has been bought
 * and which ad families have failed.
 * <p>
 * Actions run under the session lock so that a manual click and an autoplay request can never
 * interleave inside one game. Reads never take the lock: every piece of state is an immutable
 * value that is swapped atomically, so the UI can follow a game while the server is playing it.
 */
public class GameSession {

	private final ReentrantLock lock = new ReentrantLock();

	private final Instant createdAt = Instant.now();

	private final List<TurnRecord> history = new CopyOnWriteArrayList<>();

	private volatile GameState state;

	/** How often each item id has been bought, in order of first purchase. */
	private volatile Map<String, Integer> purchases = Map.of();

	/** How often ads of each family (see {@link Ad#family()}) have failed in this game. */
	private volatile Map<String, Integer> failedFamilies = Map.of();

	private volatile List<ShopItem> shop;

	private volatile Map<String, Ad> lastBoard = Map.of();

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

	/** Stores a turn and returns it completed with the purchases as they stand afterwards. */
	TurnRecord record(TurnRecord turn) {
		lock.lock();
		try {
			if (turn.action() == ActionType.BUY && turn.success()) {
				purchases = incremented(purchases, turn.targetId());
			}
			if (turn.action() == ActionType.SOLVE && !turn.success() && !turn.family().isEmpty()) {
				failedFamilies = incremented(failedFamilies, turn.family());
			}
			TurnRecord stored = turn.withPurchases(purchases);
			history.add(stored);
			state = stored.stateAfter();
			return stored;
		}
		finally {
			lock.unlock();
		}
	}

	private static Map<String, Integer> incremented(Map<String, Integer> counts, String key) {
		Map<String, Integer> copy = new LinkedHashMap<>(counts);
		copy.merge(key, 1, Integer::sum);
		return Collections.unmodifiableMap(copy);
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
		return List.copyOf(history);
	}

	public Map<String, Integer> purchases() {
		return purchases;
	}

	public Map<String, Integer> failedFamilies() {
		return failedFamilies;
	}

	/** Remembers the most recently fetched board so actions can be described by ad text and family. */
	void rememberBoard(List<Ad> ads) {
		lastBoard = ads.stream().collect(Collectors.toUnmodifiableMap(Ad::adId, Function.identity(), (a, b) -> a));
	}

	/** The ad with this id on the last fetched board, if it is on it. */
	public Optional<Ad> adOnBoard(String adId) {
		return Optional.ofNullable(lastBoard.get(adId));
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
