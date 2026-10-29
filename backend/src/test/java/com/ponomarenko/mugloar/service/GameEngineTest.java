package com.ponomarenko.mugloar.service;

import java.util.List;

import com.ponomarenko.mugloar.client.MugloarApiException;
import com.ponomarenko.mugloar.client.dto.AdDto;
import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.AdDecoder;
import com.ponomarenko.mugloar.domain.Probability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameEngineTest {

	private FakeMugloarApi api;

	private GameEngine engine;

	private GameSession session;

	@BeforeEach
	void setUp() {
		api = new FakeMugloarApi();
		engine = new GameEngine(api, new AdDecoder());
		session = engine.start();
	}

	@Test
	void startCreatesSessionFromApiState() {
		assertThat(session.gameId()).isEqualTo(FakeMugloarApi.GAME_ID);
		assertThat(session.state().lives()).isEqualTo(3);
		assertThat(session.history()).isEmpty();
		assertThat(session.isOver()).isFalse();
	}

	@Test
	void fetchAdsDecodesTheBoard() {
		api.board = () -> List.of(new AdDto("aGVsbG8=", "aGVsbG8=", "7", 2, 1, "U3VyZSB0aGluZw=="));

		List<Ad> ads = engine.fetchAds(session);

		assertThat(ads).singleElement().satisfies(ad -> {
			assertThat(ad.adId()).isEqualTo("hello");
			assertThat(ad.probability()).isEqualTo(Probability.SURE_THING);
		});
	}

	@Test
	void shopIsFetchedOncePerSession() {
		engine.fetchShop(session);
		engine.fetchShop(session);

		assertThat(api.shopRequests).isEqualTo(1);
	}

	@Test
	void successfulSolveUpdatesStateAndHistory() {
		TurnRecord record = engine.solve(session, "easy", "Help a neighbour", "because", true);

		assertThat(record.action()).isEqualTo(ActionType.SOLVE);
		assertThat(record.success()).isTrue();
		assertThat(record.targetId()).isEqualTo("easy");
		assertThat(record.description()).isEqualTo("Help a neighbour");
		assertThat(record.reason()).isEqualTo("because");
		assertThat(record.automated()).isTrue();
		assertThat(record.stateAfter().gold()).isEqualTo(20);
		assertThat(record.stateAfter().score()).isEqualTo(20);
		assertThat(session.state()).isEqualTo(record.stateAfter());
		assertThat(session.history()).containsExactly(record);
	}

	@Test
	void manualSolveIsDescribedFromTheLastFetchedBoard() {
		engine.fetchAds(session);

		TurnRecord record = engine.solve(session, "easy", null, "", false);

		assertThat(record.description()).isEqualTo("Help a neighbour");
		assertThat(record.automated()).isFalse();
	}

	@Test
	void manualSolveFallsBackToTheAdIdWhenTheBoardIsUnknown() {
		TurnRecord record = engine.solve(session, "easy", null, "", false);

		assertThat(record.description()).isEqualTo("easy");
	}

	@Test
	void failedSolveCostsALifeAndIsRememberedByAdFamily() {
		TurnRecord record = engine.solve(session, "hard", "Steal super awesome diamond pan from Bob", "", false);

		assertThat(record.success()).isFalse();
		assertThat(record.stateAfter().lives()).isEqualTo(2);
		assertThat(session.failedFamilies()).containsEntry("steal super awesome diamond", 1);

		engine.solve(session, "hard", "Steal super awesome diamond cat from Alice", "", false);
		assertThat(session.failedFamilies()).containsEntry("steal super awesome diamond", 2);
	}

	@Test
	void solvingAnUnknownOrExpiredAdIsAnInvalidAction() {
		assertThatExceptionOfType(InvalidActionException.class)
				.isThrownBy(() -> engine.solve(session, "gone", null, "", false))
				.withMessageContaining("expired or does not exist");
		assertThat(session.history()).isEmpty();
	}

	@Test
	void rateLimitOnSolveKeepsItsStatusInsteadOfBecomingAnInvalidAction() {
		api.solveFailures.push(new MugloarApiException(MugloarApiException.TOO_MANY_REQUESTS, "", "slow down"));

		assertThatExceptionOfType(MugloarApiException.class)
				.isThrownBy(() -> engine.solve(session, "easy", null, "", false))
				.matches(MugloarApiException::isTooManyRequests);
		assertThat(session.history()).isEmpty();
	}

	@Test
	void upstreamServerErrorsPropagate() {
		api.solveFailures.push(new MugloarApiException(503, "", "down"));

		assertThatThrownBy(() -> engine.solve(session, "easy", null, "", false))
				.isInstanceOf(MugloarApiException.class);
	}

	@Test
	void buyRefusesUnknownItemsWithoutCallingTheApi() {
		assertThatExceptionOfType(InvalidActionException.class)
				.isThrownBy(() -> engine.buy(session, "sword", "", false))
				.withMessageContaining("Unknown shop item");
		assertThat(api.boughtItemIds).isEmpty();
		assertThat(api.turn).isZero();
	}

	@Test
	void buyRefusesUnaffordableItemsWithoutCallingTheApi() {
		assertThatExceptionOfType(InvalidActionException.class)
				.isThrownBy(() -> engine.buy(session, "hpot", "", false))
				.withMessageContaining("Not enough gold");
		assertThat(api.turn).isZero();
	}

	@Test
	void successfulPurchaseIsRecordedAndCounted() {
		api.gold = 120;
		session = engine.start();

		TurnRecord record = engine.buy(session, "cs", "levelling", true);

		assertThat(record.action()).isEqualTo(ActionType.BUY);
		assertThat(record.success()).isTrue();
		assertThat(record.description()).isEqualTo("Claw Sharpening");
		assertThat(record.message()).isEqualTo("Bought Claw Sharpening");
		assertThat(record.stateAfter().gold()).isEqualTo(20);
		assertThat(record.stateAfter().level()).isEqualTo(1);
		assertThat(session.ownedItemIds()).containsExactly("cs");
		assertThat(session.purchases()).containsEntry("cs", 1);
	}

	@Test
	void actionsOnAFinishedGameAreRejected() {
		engine.solve(session, "hard", null, "", false);
		engine.solve(session, "hard", null, "", false);
		engine.solve(session, "hard", null, "", false);
		assertThat(session.isOver()).isTrue();

		assertThatExceptionOfType(GameOverException.class).isThrownBy(() -> engine.fetchAds(session));
		assertThatExceptionOfType(GameOverException.class)
				.isThrownBy(() -> engine.solve(session, "easy", null, "", false));
		assertThatExceptionOfType(GameOverException.class).isThrownBy(() -> engine.buy(session, "hpot", "", false));
		assertThatExceptionOfType(GameOverException.class).isThrownBy(() -> engine.investigate(session));
	}

	@Test
	void upstreamGameOverClosesTheSession() {
		api.solveFailures.push(new MugloarApiException(410, "{\"status\":\"Game Over\"}", "gone"));

		assertThatExceptionOfType(GameOverException.class)
				.isThrownBy(() -> engine.solve(session, "easy", null, "", false));
		assertThat(session.isOver()).isTrue();
		assertThat(session.state().lives()).isZero();
		assertThatExceptionOfType(GameOverException.class).isThrownBy(() -> engine.fetchAds(session));
	}

	@Test
	void investigateReturnsReputation() {
		assertThat(engine.investigate(session).underworld()).isEqualTo(-1);
	}
}
