package com.ponomarenko.mugloar.service;

import java.util.List;
import java.util.Map;

import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.domain.ShopItem;
import com.ponomarenko.mugloar.strategy.AdAssessment;
import com.ponomarenko.mugloar.strategy.AdEvaluator;
import com.ponomarenko.mugloar.strategy.ShoppingPolicy;

import org.springframework.stereotype.Service;

/**
 * Application facade used by the REST layer and the CLI. Every action on an existing game runs
 * under that game's session lock; reads do not, so the UI can follow a game the server is playing.
 */
@Service
public class GameService {

	private final GameSessionRegistry registry;

	private final GameEngine engine;

	private final AutoPlayer autoPlayer;

	private final AdEvaluator adEvaluator;

	private final ShoppingPolicy shoppingPolicy;

	public GameService(GameSessionRegistry registry, GameEngine engine, AutoPlayer autoPlayer,
			AdEvaluator adEvaluator, ShoppingPolicy shoppingPolicy) {
		this.registry = registry;
		this.engine = engine;
		this.autoPlayer = autoPlayer;
		this.adEvaluator = adEvaluator;
		this.shoppingPolicy = shoppingPolicy;
	}

	public GameSession startGame() {
		return registry.register(engine.start());
	}

	public GameSession getGame(String gameId) {
		return registry.require(gameId);
	}

	public List<GameSession> listGames() {
		return registry.all();
	}

	/** Current ads, decoded and ranked by the strategy (best first). */
	public List<AdAssessment> getAds(String gameId) {
		GameSession session = registry.require(gameId);
		List<Ad> ads = engine.fetchAds(session);
		GameSession.Snapshot now = session.snapshot();
		return adEvaluator.evaluate(ads, now.state(), engine.fetchShop(session), now.failedFamilies());
	}

	public List<ShopOffer> getShop(String gameId) {
		GameSession session = registry.require(gameId);
		List<ShopItem> shop = engine.fetchShop(session);
		GameSession.Snapshot now = session.snapshot();
		GameState state = now.state();
		Map<String, Integer> purchases = now.purchases();
		return shop.stream()
				.map(item -> new ShopOffer(item, state.gold() >= item.cost(), purchases.getOrDefault(item.id(), 0),
						!state.isOver() && shoppingPolicy.isSuggested(item, state, shop, purchases)))
				.toList();
	}

	public TurnRecord solve(String gameId, String adId) {
		GameSession session = registry.require(gameId);
		return session.locked(() -> engine.solve(session, adId, "", false));
	}

	public TurnRecord buy(String gameId, String itemId) {
		GameSession session = registry.require(gameId);
		return session.locked(() -> engine.buy(session, itemId, "", false));
	}

	public Investigation investigate(String gameId) {
		GameSession session = registry.require(gameId);
		return session.locked(() -> engine.investigate(session));
	}

	public TurnRecord autoplayStep(String gameId) {
		GameSession session = registry.require(gameId);
		return session.locked(() -> autoPlayer.playTurn(session));
	}

	public AutoplayOutcome autoplay(String gameId, int maxTurns) {
		GameSession session = registry.require(gameId);
		return session.locked(() -> autoPlayer.playToEnd(session, maxTurns));
	}

	/** Starts a fresh game and plays it to the end; used by the CLI. */
	public AutoplayOutcome playNewGame(int maxTurns) {
		GameSession session = startGame();
		return session.locked(() -> autoPlayer.playToEnd(session, maxTurns));
	}

	public List<TurnRecord> history(String gameId) {
		return registry.require(gameId).history();
	}
}
