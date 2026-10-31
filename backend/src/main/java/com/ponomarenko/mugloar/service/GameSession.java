package com.ponomarenko.mugloar.service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
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
 * interleave inside one game. Reads never take the lock: the state, purchases and failed
 * families are published together as one immutable {@link Snapshot} that is swapped atomically,
 * so a reader never sees the state of one turn next to the purchases of another, and the UI can
 * follow a game while the server is playing it.
 */
public class GameSession {

	/**
	 * What a reader needs about a game at one instant.
	 *
	 * @param state lives, gold, level, score and turn
	 * @param purchases how often each item id has been bought, in order of first purchase
	 * @param failedFamilies how often ads of each family (see {@link Ad#family()}) have failed
	 */
	public record Snapshot(GameState state, Map<String, Integer> purchases, Map<String, Integer> failedFamilies) {
	}

	private final ReentrantLock lock = new ReentrantLock();

	private final Instant createdAt = Instant.now();

	private final List<TurnRecord> history = new CopyOnWriteArrayList<>();

	private volatile Snapshot snapshot;

	private volatile List<ShopItem> shop;

	private volatile Map<String, Ad> lastBoard = Map.of();

	/**
	 * Set when a solve got no answer: the game may have carried it out, so gold and lives are not
	 * to be trusted until the next answer refreshes them. Nothing is bought meanwhile.
	 */
	private volatile boolean stateUncertain;

	/**
	 * A purchase that got no answer. The game may have carried it out, and the game API has no
	 * endpoint that would say: the next answer's gold tells (see {@link #record}), and until then
	 * nothing is bought, so the purchase is never repeated by mistake.
	 */
	private volatile ShopItem pendingPurchase;

	public GameSession(GameState initial) {
		this.snapshot = new Snapshot(initial, Map.of(), Map.of());
	}

	public String gameId() {
		return snapshot.state().gameId();
	}

	/** The state, purchases and failed families as they stand right now, consistent with each other. */
	public Snapshot snapshot() {
		return snapshot;
	}

	public GameState state() {
		return snapshot.state();
	}

	public Map<String, Integer> purchases() {
		return snapshot.purchases();
	}

	public Map<String, Integer> failedFamilies() {
		return snapshot.failedFamilies();
	}

	public Instant createdAt() {
		return createdAt;
	}

	public boolean isOver() {
		return state().isOver();
	}

	/** Whether an unanswered action leaves gold, lives or purchases in doubt; nothing is bought then. */
	public boolean isStateUncertain() {
		return stateUncertain || pendingPurchase != null;
	}

	void markStateUncertain() {
		stateUncertain = true;
	}

	void markPurchaseUnanswered(ShopItem item) {
		pendingPurchase = item;
	}

	/** The purchase whose answer was lost, until the next answer has settled whether it went through. */
	public Optional<ShopItem> pendingPurchase() {
		return Optional.ofNullable(pendingPurchase);
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

	/**
	 * Stores a turn whose answer carried the full state (a solve or a purchase) and returns it
	 * completed with the purchases as they stand afterwards.
	 *
	 * @param goldDelta how much this action alone changed the gold (a reward, minus a price, or
	 * nothing), when known. It settles an unanswered purchase: the gold the answer reports either
	 * is exactly what this action explains, so that purchase never happened, or it is not, so it
	 * did and is counted. When the delta is unknown the purchase is counted rather than repeated.
	 */
	TurnRecord record(TurnRecord turn, OptionalInt goldDelta) {
		return store(turn, goldDelta, true);
	}

	/**
	 * Stores a reputation investigation: a turn whose answer carries no state. The turn is counted,
	 * but an unanswered purchase stays pending and the state stays uncertain, since nothing in the
	 * answer vouches for the gold or the lives.
	 */
	TurnRecord recordInvestigation(TurnRecord turn) {
		return store(turn, OptionalInt.empty(), false);
	}

	private TurnRecord store(TurnRecord turn, OptionalInt goldDelta, boolean answerCarriesState) {
		lock.lock();
		try {
			Snapshot current = snapshot;
			Map<String, Integer> purchases = current.purchases();
			Map<String, Integer> failedFamilies = current.failedFamilies();
			if (answerCarriesState) {
				ShopItem pending = pendingPurchase;
				if (pending != null && wentThrough(current.state().gold(), turn.stateAfter().gold(), goldDelta)) {
					purchases = incremented(purchases, pending.id());
				}
				pendingPurchase = null;
			}
			if (turn.action() == ActionType.BUY && turn.success()) {
				purchases = incremented(purchases, turn.targetId());
			}
			if (turn.action() == ActionType.SOLVE && !turn.success() && !turn.family().isEmpty()) {
				failedFamilies = incremented(failedFamilies, turn.family());
			}
			TurnRecord stored = turn.withPurchases(purchases);
			history.add(stored);
			snapshot = new Snapshot(stored.stateAfter(), purchases, failedFamilies);
			if (answerCarriesState) {
				stateUncertain = false;
			}
			return stored;
		}
		finally {
			lock.unlock();
		}
	}

	private static boolean wentThrough(int goldBefore, int goldAfter, OptionalInt goldDelta) {
		return goldDelta.isEmpty() || goldAfter != goldBefore + goldDelta.getAsInt();
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
			Snapshot current = snapshot;
			GameState s = current.state();
			snapshot = new Snapshot(new GameState(s.gameId(), 0, s.gold(), s.level(), s.score(), s.highScore(), s.turn()),
					current.purchases(), current.failedFamilies());
		}
		finally {
			lock.unlock();
		}
	}

	public List<TurnRecord> history() {
		return List.copyOf(history);
	}

	/** Remembers the most recently fetched board so manual actions can be described by ad text and family. */
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
