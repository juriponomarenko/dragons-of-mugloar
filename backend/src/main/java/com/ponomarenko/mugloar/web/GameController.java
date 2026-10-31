package com.ponomarenko.mugloar.web;

import java.util.List;

import com.ponomarenko.mugloar.service.AutoplayOutcome;
import com.ponomarenko.mugloar.service.GameService;
import com.ponomarenko.mugloar.service.GameSession;
import com.ponomarenko.mugloar.service.Investigation;
import com.ponomarenko.mugloar.service.TurnRecord;
import com.ponomarenko.mugloar.web.dto.AdView;
import com.ponomarenko.mugloar.web.dto.AutoplayRequest;
import com.ponomarenko.mugloar.web.dto.AutoplayView;
import com.ponomarenko.mugloar.web.dto.GameView;
import com.ponomarenko.mugloar.web.dto.InvestigationView;
import com.ponomarenko.mugloar.web.dto.ShopItemView;
import com.ponomarenko.mugloar.web.dto.TurnView;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API consumed by the frontend. Every identifier coming from the client is a
 * {@link ValidId}, checked before it is ever forwarded to the upstream game API.
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

	private final GameService gameService;

	public GameController(GameService gameService) {
		this.gameService = gameService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public GameView startGame() {
		return ViewMapper.game(gameService.startGame());
	}

	@GetMapping
	public List<GameView> listGames() {
		return gameService.listGames().stream().map(ViewMapper::game).toList();
	}

	@GetMapping("/{gameId}")
	public GameView getGame(@PathVariable @ValidId String gameId) {
		return ViewMapper.game(gameService.getGame(gameId));
	}

	@GetMapping("/{gameId}/ads")
	public List<AdView> getAds(@PathVariable @ValidId String gameId) {
		return ViewMapper.ads(gameService.getAds(gameId));
	}

	@PostMapping("/{gameId}/ads/{adId}/solve")
	public TurnView solve(@PathVariable @ValidId String gameId,
			@PathVariable @ValidId String adId) {
		TurnRecord record = gameService.solve(gameId, adId);
		return ViewMapper.turn(record, gameService.getGame(gameId));
	}

	@GetMapping("/{gameId}/shop")
	public List<ShopItemView> getShop(@PathVariable @ValidId String gameId) {
		return gameService.getShop(gameId).stream().map(ViewMapper::shopItem).toList();
	}

	@PostMapping("/{gameId}/shop/{itemId}/buy")
	public TurnView buy(@PathVariable @ValidId String gameId,
			@PathVariable @ValidId String itemId) {
		TurnRecord record = gameService.buy(gameId, itemId);
		return ViewMapper.turn(record, gameService.getGame(gameId));
	}

	/** Costs a turn in the game, so the answer carries the recorded turn besides the reputation. */
	@PostMapping("/{gameId}/reputation")
	public InvestigationView investigate(@PathVariable @ValidId String gameId) {
		Investigation investigation = gameService.investigate(gameId);
		return new InvestigationView(investigation.reputation(),
				ViewMapper.turn(investigation.turn(), gameService.getGame(gameId)));
	}

	@PostMapping("/{gameId}/autoplay/step")
	public TurnView autoplayStep(@PathVariable @ValidId String gameId) {
		TurnRecord record = gameService.autoplayStep(gameId);
		return ViewMapper.turn(record, gameService.getGame(gameId));
	}

	@PostMapping("/{gameId}/autoplay")
	public AutoplayView autoplay(@PathVariable @ValidId String gameId,
			@RequestBody(required = false) @Valid AutoplayRequest request) {
		// No bound of its own unless the body gives one: the strategy's max-turns cap always applies.
		int maxTurns = request != null ? request.maxTurnsOr(Integer.MAX_VALUE) : Integer.MAX_VALUE;
		AutoplayOutcome outcome = gameService.autoplay(gameId, maxTurns);
		return ViewMapper.autoplay(outcome, gameService.getGame(gameId));
	}

	@GetMapping("/{gameId}/history")
	public List<TurnView> history(@PathVariable @ValidId String gameId) {
		GameSession session = gameService.getGame(gameId);
		return gameService.history(gameId).stream().map(t -> ViewMapper.turn(t, session)).toList();
	}
}
