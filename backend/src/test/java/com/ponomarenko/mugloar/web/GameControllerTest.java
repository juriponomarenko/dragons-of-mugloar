package com.ponomarenko.mugloar.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.ponomarenko.mugloar.client.MugloarApiException;
import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.Encryption;
import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.domain.Probability;
import com.ponomarenko.mugloar.domain.Reputation;
import com.ponomarenko.mugloar.domain.ShopItem;
import com.ponomarenko.mugloar.service.ActionType;
import com.ponomarenko.mugloar.service.AutoplayOutcome;
import com.ponomarenko.mugloar.service.GameNotFoundException;
import com.ponomarenko.mugloar.service.GameOverException;
import com.ponomarenko.mugloar.service.GameService;
import com.ponomarenko.mugloar.service.GameSession;
import com.ponomarenko.mugloar.service.InvalidActionException;
import com.ponomarenko.mugloar.service.ShopOffer;
import com.ponomarenko.mugloar.service.TurnRecord;
import com.ponomarenko.mugloar.strategy.AdAssessment;
import com.ponomarenko.mugloar.strategy.NoMoveAvailableException;
import com.ponomarenko.mugloar.strategy.RiskLevel;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
class GameControllerTest {

	private static final GameState STATE = new GameState("g1", 3, 20, 0, 20, 0, 1);

	private static final GameSession.Snapshot SNAPSHOT = new GameSession.Snapshot(STATE, Map.of(), Map.of());

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GameService gameService;

	private static GameSession session() {
		return new GameSession(STATE);
	}

	private static TurnRecord turn(boolean automated) {
		return new TurnRecord(1, ActionType.SOLVE, "a1", "Help someone", "help someone|sure_thing", true, "Solved",
				"reason", automated, STATE, Map.of(), Instant.parse("2026-10-27T10:00:00Z"));
	}

	@Test
	void startGameReturnsCreatedGame() throws Exception {
		when(gameService.startGame()).thenReturn(session());

		mockMvc.perform(post("/api/games"))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.gameId").value("g1"))
				.andExpect(jsonPath("$.lives").value(3))
				.andExpect(jsonPath("$.gold").value(20))
				.andExpect(jsonPath("$.gameOver").value(false))
				.andExpect(jsonPath("$.purchases").isMap());
	}

	@Test
	void getGameReturnsState() throws Exception {
		when(gameService.getGame("g1")).thenReturn(session());

		mockMvc.perform(get("/api/games/g1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.score").value(20));
	}

	@Test
	void listGames() throws Exception {
		when(gameService.listGames()).thenReturn(List.of(session()));

		mockMvc.perform(get("/api/games"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void unknownGameIsA404Problem() throws Exception {
		when(gameService.getGame("missing")).thenThrow(new GameNotFoundException("missing"));

		mockMvc.perform(get("/api/games/missing"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Game not found"))
				.andExpect(jsonPath("$.detail").value("No game with id 'missing' is known to this server"));
	}

	@Test
	void malformedIdsAreRejectedBeforeReachingTheService() throws Exception {
		mockMvc.perform(get("/api/games/bad id!"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid request"))
				.andExpect(jsonPath("$.errors", hasItem("gameId: must be 1-64 characters of letters, digits, '-' or '_'")));

		mockMvc.perform(post("/api/games/g1/ads/{adId}/solve", "x".repeat(65)))
				.andExpect(status().isBadRequest());

		verify(gameService, never()).getGame(anyString());
		verify(gameService, never()).solve(anyString(), anyString());
	}

	@Test
	void adsAreReturnedWithAssessmentAndRecommendation() throws Exception {
		Ad good = new Ad("a1", "Help", 90, 5, Probability.PIECE_OF_CAKE, "Piece of cake", Encryption.NONE);
		Ad bad = new Ad("a2", "Doom", 900, 5, Probability.IMPOSSIBLE, "Impossible", Encryption.BASE64);
		when(gameService.getAds("g1")).thenReturn(List.of(
				new AdAssessment(good, 0.95, 85.5, 83.0, RiskLevel.SAFE, true, List.of()),
				new AdAssessment(bad, 0.0, 0.0, -50.0, RiskLevel.DANGEROUS, false, List.of("Decoded from base64"))));

		mockMvc.perform(get("/api/games/g1/ads"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].adId").value("a1"))
				.andExpect(jsonPath("$[0].recommended").value(true))
				.andExpect(jsonPath("$[0].eligible").value(true))
				.andExpect(jsonPath("$[0].risk").value("SAFE"))
				.andExpect(jsonPath("$[0].successRate").value(0.95))
				.andExpect(jsonPath("$[0].expectedValue").value(85.5))
				.andExpect(jsonPath("$[1].recommended").value(false))
				.andExpect(jsonPath("$[1].encryption").value("base64"))
				.andExpect(jsonPath("$[1].notes[0]").value("Decoded from base64"));
	}

	@Test
	void solveReturnsTurnWithState() throws Exception {
		when(gameService.solve("g1", "a1")).thenReturn(turn(false));
		when(gameService.getGame("g1")).thenReturn(session());

		mockMvc.perform(post("/api/games/g1/ads/a1/solve"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.action").value("SOLVE"))
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.automated").value(false))
				.andExpect(jsonPath("$.state.gameId").value("g1"));
	}

	@Test
	void shopReturnsOffers() throws Exception {
		when(gameService.getShop("g1")).thenReturn(List.of(
				new ShopOffer(new ShopItem("hpot", "Healing potion", 50), false, 0, false),
				new ShopOffer(new ShopItem("cs", "Claw Sharpening", 100), true, 2, true)));

		mockMvc.perform(get("/api/games/g1/shop"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[1].id").value("cs"))
				.andExpect(jsonPath("$[1].affordable").value(true))
				.andExpect(jsonPath("$[1].timesBought").value(2))
				.andExpect(jsonPath("$[1].suggested").value(true));
	}

	@Test
	void invalidActionIsA422Problem() throws Exception {
		when(gameService.buy("g1", "hpot")).thenThrow(new InvalidActionException("Not enough gold"));

		mockMvc.perform(post("/api/games/g1/shop/hpot/buy"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.title").value("Action not possible"))
				.andExpect(jsonPath("$.detail").value("Not enough gold"));
	}

	@Test
	void gameOverIsA409Problem() throws Exception {
		when(gameService.autoplayStep("g1")).thenThrow(new GameOverException("g1"));

		mockMvc.perform(post("/api/games/g1/autoplay/step"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Game over"));
	}

	@Test
	void upstreamFailuresAreGatewayProblems() throws Exception {
		when(gameService.getAds("g1")).thenThrow(new MugloarApiException(500, "boom", "upstream exploded"));
		when(gameService.getShop("g1"))
				.thenThrow(new MugloarApiException(MugloarApiException.NO_RESPONSE, "", "timeout"));

		mockMvc.perform(get("/api/games/g1/ads"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.upstreamStatus").value(500));
		mockMvc.perform(get("/api/games/g1/shop"))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void autoplayAcceptsOptionalBodyAndValidatesIt() throws Exception {
		when(gameService.autoplay(eq("g1"), eq(Integer.MAX_VALUE))).thenReturn(new AutoplayOutcome(SNAPSHOT, List.of(turn(true)), true, "Game over"));
		when(gameService.autoplay(eq("g1"), eq(10))).thenReturn(new AutoplayOutcome(SNAPSHOT, List.of(), false, "Reached the turn limit of 10"));
		when(gameService.getGame("g1")).thenReturn(session());

		mockMvc.perform(post("/api/games/g1/autoplay"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gameOver").value(true))
				.andExpect(jsonPath("$.turns", hasSize(1)))
				.andExpect(jsonPath("$.turns[0].automated").value(true));

		mockMvc.perform(post("/api/games/g1/autoplay").contentType(MediaType.APPLICATION_JSON).content("{\"maxTurns\":10}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stoppedBecause").value("Reached the turn limit of 10"));

		mockMvc.perform(post("/api/games/g1/autoplay").contentType(MediaType.APPLICATION_JSON).content("{\"maxTurns\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors", hasItem("maxTurns: must be greater than or equal to 1")));
	}

	@Test
	void noPossibleMoveIsA422Problem() throws Exception {
		when(gameService.autoplayStep("g1"))
				.thenThrow(new NoMoveAvailableException("No ads available and nothing affordable to buy"));

		mockMvc.perform(post("/api/games/g1/autoplay/step"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.title").value("Action not possible"))
				.andExpect(jsonPath("$.detail").value("No ads available and nothing affordable to buy"));
	}

	@Test
	void reputationAndHistoryAreExposed() throws Exception {
		when(gameService.investigate("g1")).thenReturn(new Reputation(1, 0, -1));
		when(gameService.getGame("g1")).thenReturn(session());
		when(gameService.history("g1")).thenReturn(List.of(turn(true)));

		mockMvc.perform(post("/api/games/g1/reputation"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.underworld").value(-1));
		mockMvc.perform(get("/api/games/g1/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].reason").value("reason"));
	}
}
