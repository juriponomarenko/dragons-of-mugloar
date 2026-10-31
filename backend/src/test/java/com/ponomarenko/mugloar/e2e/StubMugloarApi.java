package com.ponomarenko.mugloar.e2e;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.ponomarenko.mugloar.client.MugloarApiClient;
import com.ponomarenko.mugloar.client.MugloarApiException;
import com.ponomarenko.mugloar.client.dto.AdDto;
import com.ponomarenko.mugloar.client.dto.GameStartResponse;
import com.ponomarenko.mugloar.client.dto.PurchaseResponse;
import com.ponomarenko.mugloar.client.dto.ReputationResponse;
import com.ponomarenko.mugloar.client.dto.ShopItemDto;
import com.ponomarenko.mugloar.client.dto.SolveResponse;

/**
 * Stand-in for the game API behind the frontend's end-to-end tests. Deterministic, so a test can
 * assert exact numbers, and multi-game, so tests can run in parallel: every game starts with three
 * lives and no gold, the board never changes, and every ad succeeds except the impossible one and
 * the trap. The game ends when the lives run out, as the real one does.
 */
public class StubMugloarApi implements MugloarApiClient {

	static final List<AdDto> BOARD = List.of(
			new AdDto("escort", "Escort a merchant caravan to the market", "60", 5, null, "Sure thing"),
			new AdDto("water", "Help Old Nan carry her water buckets", "20", 5, null, "Sure thing"),
			new AdDto("kitten", "Rescue a kitten from the old oak", "45", 5, null, "Walk in the park"),
			new AdDto("diamond", "Steal super awesome diamond ring from the mayor", "400", 5, null, "Sure thing"),
			new AdDto("dragon", "Slay the dragon of the northern peaks", "500", 5, null, "Impossible"));

	static final Set<String> FAILING_ADS = Set.of("diamond", "dragon");

	static final List<ShopItemDto> SHOP = List.of(
			new ShopItemDto("hpot", "Healing potion", 50),
			new ShopItemDto("cs", "Claw Sharpening", 100),
			new ShopItemDto("gas", "Gasoline", 100));

	private static final class Game {

		int lives = 3;

		int gold;

		int level;

		int score;

		int turn;

	}

	private final AtomicInteger nextId = new AtomicInteger();

	private final Map<String, Game> games = new ConcurrentHashMap<>();

	@Override
	public GameStartResponse startGame() {
		String id = "e2e-" + nextId.incrementAndGet();
		Game game = new Game();
		games.put(id, game);
		return new GameStartResponse(id, game.lives, game.gold, game.level, game.score, 0, game.turn);
	}

	@Override
	public List<AdDto> getMessages(String gameId) {
		game(gameId);
		return BOARD;
	}

	@Override
	public SolveResponse solve(String gameId, String adId) {
		Game game = game(gameId);
		synchronized (game) {
			requireAlive(game);
			AdDto ad = BOARD.stream().filter(a -> a.adId().equals(adId)).findFirst()
					.orElseThrow(() -> new MugloarApiException(400, "", "No such ad"));
			game.turn++;
			boolean success = !FAILING_ADS.contains(adId);
			if (success) {
				int reward = Integer.parseInt(ad.reward());
				game.gold += reward;
				game.score += reward;
			}
			else {
				game.lives--;
			}
			return new SolveResponse(success, game.lives, game.gold, game.score, 0, game.turn,
					success ? "You successfully solved the mission!" : "You failed to solve the mission!");
		}
	}

	@Override
	public List<ShopItemDto> getShop(String gameId) {
		game(gameId);
		return SHOP;
	}

	@Override
	public PurchaseResponse buy(String gameId, String itemId) {
		Game game = game(gameId);
		synchronized (game) {
			requireAlive(game);
			game.turn++;
			ShopItemDto item = SHOP.stream().filter(i -> i.id().equals(itemId)).findFirst().orElse(null);
			boolean success = item != null && game.gold >= item.cost();
			if (success) {
				game.gold -= item.cost();
				if ("hpot".equals(itemId)) {
					game.lives++;
				}
				else {
					game.level++;
				}
			}
			return new PurchaseResponse(success, game.gold, game.lives, game.level, game.turn);
		}
	}

	@Override
	public ReputationResponse investigateReputation(String gameId) {
		game(gameId);
		return new ReputationResponse(1, 0, -1);
	}

	private Game game(String gameId) {
		Game game = games.get(gameId);
		if (game == null) {
			throw new MugloarApiException(404, "", "No such game");
		}
		return game;
	}

	private static void requireAlive(Game game) {
		if (game.lives <= 0) {
			throw new MugloarApiException(410, "", "Game Over");
		}
	}

}
