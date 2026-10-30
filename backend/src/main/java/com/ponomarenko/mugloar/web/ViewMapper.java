package com.ponomarenko.mugloar.web;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.ponomarenko.mugloar.domain.GameState;
import com.ponomarenko.mugloar.service.AutoplayOutcome;
import com.ponomarenko.mugloar.service.GameSession;
import com.ponomarenko.mugloar.service.ShopOffer;
import com.ponomarenko.mugloar.service.TurnRecord;
import com.ponomarenko.mugloar.strategy.AdAssessment;
import com.ponomarenko.mugloar.web.dto.AdView;
import com.ponomarenko.mugloar.web.dto.AutoplayView;
import com.ponomarenko.mugloar.web.dto.GameView;
import com.ponomarenko.mugloar.web.dto.ShopItemView;
import com.ponomarenko.mugloar.web.dto.TurnView;

/** Converts domain and service objects into the JSON shapes exposed by the API. */
final class ViewMapper {

	private ViewMapper() {
	}

	/** One snapshot, so the state and the purchases in a response belong to the same turn. */
	static GameView game(GameSession session) {
		GameSession.Snapshot now = session.snapshot();
		return game(now.state(), now.purchases(), session.createdAt());
	}

	static GameView game(GameState state, Map<String, Integer> purchases, Instant createdAt) {
		return new GameView(state.gameId(), state.lives(), state.gold(), state.level(), state.score(),
				state.highScore(), state.turn(), state.isOver(), purchases, createdAt);
	}

	/** The first eligible ad in a ranked list is the recommended one. */
	static List<AdView> ads(List<AdAssessment> ranked) {
		String recommendedId = ranked.stream()
				.filter(AdAssessment::eligible)
				.map(a -> a.ad().adId())
				.findFirst()
				.orElse(null);
		return ranked.stream().map(a -> ad(a, a.ad().adId().equals(recommendedId))).toList();
	}

	static AdView ad(AdAssessment a, boolean recommended) {
		return new AdView(a.ad().adId(), a.ad().message(), a.ad().reward(), a.ad().expiresIn(),
				a.ad().probabilityLabel(), round(a.successRate()), round(a.expectedValue()), round(a.score()),
				a.risk().name(), a.eligible(), recommended, a.ad().encryption().name().toLowerCase(Locale.ROOT),
				a.notes());
	}

	static ShopItemView shopItem(ShopOffer offer) {
		return new ShopItemView(offer.item().id(), offer.item().name(), offer.item().cost(), offer.affordable(),
				offer.timesBought(), offer.suggested());
	}

	static TurnView turn(TurnRecord record, GameSession session) {
		return new TurnView(record.turn(), record.action().name(), record.targetId(), record.description(),
				record.success(), record.message(), record.reason(), record.automated(),
				game(record.stateAfter(), record.purchasesAfter(), session.createdAt()), record.at());
	}

	static AutoplayView autoplay(AutoplayOutcome outcome, GameSession session) {
		return new AutoplayView(game(session), outcome.turns().stream().map(t -> turn(t, session)).toList(),
				outcome.gameOver(), outcome.stoppedBecause());
	}

	private static double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
