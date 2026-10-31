package com.ponomarenko.mugloar.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

import com.ponomarenko.mugloar.client.MugloarApiClient;
import com.ponomarenko.mugloar.client.MugloarApiException;
import com.ponomarenko.mugloar.client.dto.AdDto;
import com.ponomarenko.mugloar.client.dto.GameStartResponse;
import com.ponomarenko.mugloar.client.dto.PurchaseResponse;
import com.ponomarenko.mugloar.client.dto.ReputationResponse;
import com.ponomarenko.mugloar.client.dto.ShopItemDto;
import com.ponomarenko.mugloar.client.dto.SolveResponse;

/**
 * Deterministic in-memory stand-in for the game API. "Sure thing" ads always succeed,
 * "Impossible" ads always fail, every other label succeeds unless told otherwise. Failures can
 * be scripted to exercise error handling.
 */
class FakeMugloarApi implements MugloarApiClient {

	static final String GAME_ID = "fake-game";

	int lives = 3;

	int gold = 0;

	int level = 0;

	int score = 0;

	int turn = 0;

	Supplier<List<AdDto>> board = () -> List.of(
			new AdDto("easy", "Help a neighbour", "20", 5, null, "Sure thing"),
			new AdDto("hard", "Slay a dragon", "500", 5, null, "Impossible"));

	final List<ShopItemDto> shop = new ArrayList<>(List.of(
			new ShopItemDto("hpot", "Healing potion", 50),
			new ShopItemDto("cs", "Claw Sharpening", 100)));

	final Deque<RuntimeException> solveFailures = new ArrayDeque<>();

	final Deque<RuntimeException> messageFailures = new ArrayDeque<>();

	final Deque<RuntimeException> shopFailures = new ArrayDeque<>();

	final List<String> solvedAdIds = new ArrayList<>();

	final List<String> boughtItemIds = new ArrayList<>();

	int shopRequests;

	/** Carries out the next purchase but loses the answer, as a dropped connection would. */
	boolean loseNextBuyResponse;

	/** Never receives the next purchase, as a connection that failed before the request got through. */
	boolean dropNextBuyRequest;

	@Override
	public GameStartResponse startGame() {
		return new GameStartResponse(GAME_ID, lives, gold, level, score, 0, turn);
	}

	@Override
	public List<AdDto> getMessages(String gameId) {
		if (!messageFailures.isEmpty()) {
			throw messageFailures.pop();
		}
		return board.get();
	}

	@Override
	public SolveResponse solve(String gameId, String adId) {
		if (!solveFailures.isEmpty()) {
			throw solveFailures.pop();
		}
		AdDto ad = board.get().stream().filter(a -> a.adId().equals(adId)).findFirst()
				.orElseThrow(() -> new MugloarApiException(400, "Bad Request", "unknown ad"));
		solvedAdIds.add(adId);
		turn++;
		boolean success = !"Impossible".equals(ad.probability());
		if (success) {
			int reward = Integer.parseInt(ad.reward());
			gold += reward;
			score += reward;
		}
		else {
			lives--;
		}
		return new SolveResponse(success, lives, gold, score, 0, turn, success ? "Solved" : "Failed");
	}

	@Override
	public List<ShopItemDto> getShop(String gameId) {
		shopRequests++;
		if (!shopFailures.isEmpty()) {
			throw shopFailures.pop();
		}
		return shop;
	}

	@Override
	public PurchaseResponse buy(String gameId, String itemId) {
		if (dropNextBuyRequest) {
			dropNextBuyRequest = false;
			throw new MugloarApiException(MugloarApiException.NO_RESPONSE, "", "connect timed out");
		}
		turn++;
		ShopItemDto item = shop.stream().filter(i -> i.id().equals(itemId)).findFirst().orElse(null);
		boolean success = item != null && gold >= item.cost();
		if (success) {
			boughtItemIds.add(itemId);
			gold -= item.cost();
			if ("hpot".equals(itemId)) {
				lives++;
			}
			else {
				level++;
			}
		}
		if (loseNextBuyResponse) {
			loseNextBuyResponse = false;
			throw new MugloarApiException(MugloarApiException.NO_RESPONSE, "", "connection reset");
		}
		return new PurchaseResponse(success, gold, lives, level, turn);
	}

	@Override
	public ReputationResponse investigateReputation(String gameId) {
		return new ReputationResponse(1, 0, -1);
	}
}
