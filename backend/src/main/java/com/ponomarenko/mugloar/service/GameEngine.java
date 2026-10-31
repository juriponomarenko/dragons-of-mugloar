package com.ponomarenko.mugloar.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
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

	private static final int HTTP_NOT_FOUND = 404;

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
	 * Attempts the ad with this id (a manual click). Its text and family come from the last
	 * fetched board, looked up before the call so a board refresh meanwhile cannot lose them; an
	 * id that is not on the board is still forwarded, since the API has the final say.
	 */
	public TurnRecord solve(GameSession session, String adId, String reason, boolean automated) {
		return solve(session, adId, session.adOnBoard(adId).orElse(null), reason, automated);
	}

	/** Attempts a known ad (the autoplayer's pick), whatever the board shows by now. */
	public TurnRecord solve(GameSession session, Ad ad, String reason, boolean automated) {
		return solve(session, ad.adId(), ad, reason, automated);
	}

	private TurnRecord solve(GameSession session, String adId, Ad ad, String reason, boolean automated) {
		requireActive(session);
		SolveResponse response;
		try {
			response = guardGameOver(session, () -> client.solve(session.gameId(), adId));
		}
		catch (MugloarApiException ex) {
			if (ex.isRejectedRequest()) {
				throw new InvalidActionException("Ad '" + adId + "' cannot be solved: it has expired or does not exist");
			}
			noteIfUncertain(session, ex);
			throw ex;
		}
		GameState after = session.state().after(response);
		// A failed ad changes no gold; a solved one adds its reward, known when the ad is.
		OptionalInt goldDelta = !response.success() ? OptionalInt.of(0)
				: ad != null ? OptionalInt.of(ad.reward()) : OptionalInt.empty();
		TurnRecord record = session.record(new TurnRecord(after.turn(), ActionType.SOLVE, adId,
				ad != null ? ad.message() : adId, ad != null ? ad.family() : "", response.success(),
				response.message(), reason, automated, after, Map.of(), Instant.now()), goldDelta);
		log.debug("Game {} turn {}: solve {} -> {} (lives={}, gold={}, score={})", after.gameId(), after.turn(), adId,
				response.success() ? "success" : "failure", after.lives(), after.gold(), after.score());
		return record;
	}

	/**
	 * Buys an item, refusing up front when it is unknown or unaffordable (a failed purchase still
	 * costs a turn), or while an earlier action went unanswered: the gold is then not known for
	 * certain, and an unanswered purchase must not be repeated. Solving an ad settles both.
	 */
	public TurnRecord buy(GameSession session, String itemId, String reason, boolean automated) {
		requireActive(session);
		ShopItem item = fetchShop(session).stream()
				.filter(i -> i.id().equals(itemId))
				.findFirst()
				.orElseThrow(() -> new InvalidActionException("Unknown shop item '" + itemId + "'"));
		if (session.isStateUncertain()) {
			throw new InvalidActionException(session.pendingPurchase()
					.map(p -> "The purchase of '" + p.name() + "' got no answer and may have gone through")
					.orElse("The last action got no answer, so the gold is not known for certain")
					+ "; solve an ad first to refresh the game");
		}
		if (session.state().gold() < item.cost()) {
			throw new InvalidActionException("Not enough gold for '" + item.name() + "': costs " + item.cost()
					+ ", you have " + session.state().gold());
		}
		PurchaseResponse response;
		try {
			response = guardGameOver(session, () -> client.buy(session.gameId(), itemId));
		}
		catch (MugloarApiException ex) {
			if (ex.getStatus() == MugloarApiException.NO_RESPONSE) {
				log.warn("Game {}: no answer to the purchase of {}; it may have gone through", session.gameId(), itemId);
				session.markPurchaseUnanswered(item);
			}
			throw ex;
		}
		GameState after = session.state().after(response);
		String message = response.shoppingSuccess() ? "Bought " + item.name() : "Purchase of " + item.name() + " failed";
		OptionalInt goldDelta = OptionalInt.of(response.shoppingSuccess() ? -item.cost() : 0);
		TurnRecord record = session.record(new TurnRecord(after.turn(), ActionType.BUY, itemId, item.name(), "",
				response.shoppingSuccess(), message, reason, automated, after, Map.of(), Instant.now()), goldDelta);
		log.debug("Game {} turn {}: buy {} -> {} (gold={}, lives={}, level={})", after.gameId(), after.turn(), itemId,
				response.shoppingSuccess(), after.gold(), after.lives(), after.level());
		return record;
	}

	/**
	 * The API answers HTTP 410 once a game is over, and HTTP 404 once it has forgotten a game
	 * (it does so after a while of inactivity; a bad ad id is a 400, a bad item a 200). Should our
	 * bookkeeping disagree with the server, trust the server and close the session: the last
	 * score we saw stands.
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
			if (ex.getStatus() == HTTP_NOT_FOUND) {
				log.info("Game {} is no longer known to the API", session.gameId());
				session.markOver();
				throw new GameOverException(session.gameId(), "the game server no longer knows it");
			}
			throw ex;
		}
	}

	/**
	 * A solve that got no answer (timeout, connection reset) may still have been carried out; the
	 * game's gold and lives are unknown until the next answer, so the session says so.
	 */
	private static void noteIfUncertain(GameSession session, MugloarApiException ex) {
		if (ex.getStatus() == MugloarApiException.NO_RESPONSE) {
			log.warn("Game {}: no answer to an action; the game may have moved on without us", session.gameId());
			session.markStateUncertain();
		}
	}

	private static void requireActive(GameSession session) {
		if (session.isOver()) {
			throw new GameOverException(session.gameId());
		}
	}
}
