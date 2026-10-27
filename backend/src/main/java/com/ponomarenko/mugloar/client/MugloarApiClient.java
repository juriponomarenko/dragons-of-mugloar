package com.ponomarenko.mugloar.client;

import java.util.List;

import com.ponomarenko.mugloar.client.dto.AdDto;
import com.ponomarenko.mugloar.client.dto.GameStartResponse;
import com.ponomarenko.mugloar.client.dto.PurchaseResponse;
import com.ponomarenko.mugloar.client.dto.ReputationResponse;
import com.ponomarenko.mugloar.client.dto.ShopItemDto;
import com.ponomarenko.mugloar.client.dto.SolveResponse;

/**
 * Thin, typed facade over the Dragons of Mugloar HTTP API
 * (<a href="https://dragonsofmugloar.com/doc/">documentation</a>).
 * Every method throws {@link MugloarApiException} on a non-2xx response or a transport failure.
 */
public interface MugloarApiClient {

	GameStartResponse startGame();

	List<AdDto> getMessages(String gameId);

	SolveResponse solve(String gameId, String adId);

	List<ShopItemDto> getShop(String gameId);

	PurchaseResponse buy(String gameId, String itemId);

	ReputationResponse investigateReputation(String gameId);
}
