package com.ponomarenko.mugloar.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.ponomarenko.mugloar.client.MugloarApiClient;
import com.ponomarenko.mugloar.client.MugloarApiException;
import com.ponomarenko.mugloar.client.dto.PurchaseResponse;
import com.ponomarenko.mugloar.client.dto.SolveResponse;
import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.AdDecoder;
import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.domain.Reputation;
import com.ponomarenko.mugloar.domain.ShopItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * The primitive game operations: each one talks to the API, validates what it can up front,
 * updates the session and records the outcome. Callers of the actions (solve, buy) are
 * expected to hold the session lock; fetching ads or the shop needs no lock.
 */
@Component
public class GameEngine {

	private static final Logger log = LoggerFactory.getLogger(GameEngine.class);

	private static final int HTTP_GONE = 410;

	private final MugloarApiClient client;

	private final AdDecoder adDecoder;

	public GameEngine(MugloarApiClient client, AdDecoder adDecoder) {
		this.client = client;
		this.adDecoder = adDecoder;
	}

	public GameSession start() {
		GameState state = GameState.from(client.startGame());
		log.info("Started game {} (lives={}, gold={})", state.gameId(), state.lives(), state.gold());
		return new GameSession(state);
	}

	public List<Ad> fetchAds(GameSession session) {
		requireActive(session);
		List<Ad> ads = adDecoder.decodeAll(guardGameOver(session, () -> client.getMessages(session.gameId())));
		session.rememberBoard(ads);
		return ads;
	}

	public List<ShopItem> fetchShop(GameSession session) {
		return session.shop(() -> guardGameOver(session, () -> client.getShop(session.gameId())).stream()
				.map(ShopItem::from)
				.toList());
	}

	public Reputation investigate(GameSession session) {
		requireActive(session);
		return Reputation.from(guardGameOver(session, () -> client.investigateReputation(session.gameId())));
	}

	/**
	 * Attempts an ad. Its text and family come from the last fetched board; an id that is not on
	 * it (a stale manual click) is still forwarded, since the API has the final say on whether it
	 * is valid.
	 */
	public TurnRecord solve(GameSession session, String adId, String reason, boolean automated) {
		requireActive(session);
		SolveResponse response;
		try {
			response = guardGameOver(session, () -> client.solve(session.gameId(), adId));
		}
		catch (MugloarApiException ex) {
			if (ex.isRejectedRequest()) {
				throw new InvalidActionException("Ad '" + adId + "' cannot be solved: it has expired or does not exist");
			}
			throw ex;
		}
		GameState after = session.state().after(response);
		Optional<Ad> ad = session.adOnBoard(adId);
		TurnRecord record = session.record(new TurnRecord(after.turn(), ActionType.SOLVE, adId,
				ad.map(Ad::message).orElse(adId), ad.map(Ad::family).orElse(""), response.success(),
				response.message(), reason, automated, after, Map.of(), Instant.now()));
		log.debug("Game {} turn {}: solve {} -> {} (lives={}, gold={}, score={})", after.gameId(), after.turn(), adId,
				response.success() ? "success" : "failure", after.lives(), after.gold(), after.score());
		return record;
	}

	/** Buys an item, refusing up front when it is unknown or unaffordable (a failed purchase still costs a turn). */
	public TurnRecord buy(GameSession session, String itemId, String reason, boolean automated) {
		requireActive(session);
		ShopItem item = fetchShop(session).stream()
				.filter(i -> i.id().equals(itemId))
				.findFirst()
				.orElseThrow(() -> new InvalidActionException("Unknown shop item '" + itemId + "'"));
		if (session.state().gold() < item.cost()) {
			throw new InvalidActionException("Not enough gold for '" + item.name() + "': costs " + item.cost()
					+ ", you have " + session.state().gold());
		}
		PurchaseResponse response = guardGameOver(session, () -> client.buy(session.gameId(), itemId));
		GameState after = session.state().after(response);
		String message = response.shoppingSuccess() ? "Bought " + item.name() : "Purchase of " + item.name() + " failed";
		TurnRecord record = session.record(new TurnRecord(after.turn(), ActionType.BUY, itemId, item.name(), "",
				response.shoppingSuccess(), message, reason, automated, after, Map.of(), Instant.now()));
		log.debug("Game {} turn {}: buy {} -> {} (gold={}, lives={}, level={})", after.gameId(), after.turn(), itemId,
				response.shoppingSuccess(), after.gold(), after.lives(), after.level());
		return record;
	}

	/**
	 * The API answers HTTP 410 once a game is over. Should our bookkeeping disagree with the
	 * server (e.g. after a lost response), trust the server and close the session.
	 */
	private static <T> T guardGameOver(GameSession session, Supplier<T> call) {
		try {
			return call.get();
		}
		catch (MugloarApiException ex) {
			if (ex.getStatus() == HTTP_GONE) {
				log.info("Game {} reported as over by the API", session.gameId());
				session.markOver();
				throw new GameOverException(session.gameId());
			}
			throw ex;
		}
	}

	private static void requireActive(GameSession session) {
		if (session.isOver()) {
			throw new GameOverException(session.gameId());
		}
	}
}
