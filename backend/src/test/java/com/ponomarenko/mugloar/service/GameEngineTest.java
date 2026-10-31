package com.ponomarenko.mugloar.service;

import java.util.List;

import com.ponomarenko.mugloar.client.MugloarApiException;
import com.ponomarenko.mugloar.client.dto.AdDto;
import com.ponomarenko.mugloar.domain.Ad;
import com.ponomarenko.mugloar.domain.AdDecoder;
import com.ponomarenko.mugloar.domain.Encryption;
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
		engine.fetchAds(session); // the ad text and family come from the board
		TurnRecord record = engine.solve(session, "easy", "because", true);

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

		TurnRecord record = engine.solve(session, "easy", "", false);

		assertThat(record.description()).isEqualTo("Help a neighbour");
		assertThat(record.automated()).isFalse();
	}

	@Test
	void manualSolveFallsBackToTheAdIdWhenTheBoardIsUnknown() {
		TurnRecord record = engine.solve(session, "easy", "", false);

		assertThat(record.description()).isEqualTo("easy");
	}

	@Test
	void failedSolveCostsALifeAndIsRememberedByAdFamily() {
		api.board = () -> List.of(
				new AdDto("trap1", "Steal super awesome diamond pan from Bob", "300", 5, null, "Impossible"),
				new AdDto("trap2", "Steal super awesome diamond cat from Alice", "300", 5, null, "Impossible"));
		engine.fetchAds(session);

		TurnRecord record = engine.solve(session, "trap1", "", false);

		assertThat(record.success()).isFalse();
		assertThat(record.stateAfter().lives()).isEqualTo(2);
		assertThat(record.family()).isEqualTo("steal super awesome diamond|impossible");
		assertThat(session.failedFamilies()).containsEntry("steal super awesome diamond|impossible", 1);

		engine.solve(session, "trap2", "", false);
		assertThat(session.failedFamilies()).containsEntry("steal super awesome diamond|impossible", 2);
	}

	@Test
	void solvingAKnownAdKeepsItsTextAndFamilyWhateverTheBoardShows() {
		// the autoplayer passes the ad it chose; a board refresh in between must not lose it
		Ad chosen = new Ad("hard", "Steal super awesome diamond pan from Bob", 300, 5, Probability.IMPOSSIBLE,
				"Impossible", Encryption.NONE);

		TurnRecord record = engine.solve(session, chosen, "best eligible", true);

		assertThat(record.description()).isEqualTo("Steal super awesome diamond pan from Bob");
		assertThat(record.family()).isEqualTo("steal super awesome diamond|impossible");
		assertThat(session.failedFamilies()).containsEntry("steal super awesome diamond|impossible", 1);
	}

	@Test
	void aSnapshotPublishesStateAndPurchasesTogether() {
		api.gold = 150;
		session = engine.start();
		GameSession.Snapshot before = session.snapshot();

		engine.buy(session, "cs", "", false);

		GameSession.Snapshot after = session.snapshot();
		assertThat(before.state().turn()).isZero();
		assertThat(before.purchases()).isEmpty();
		assertThat(after.state().turn()).isEqualTo(1);
		assertThat(after.state().gold()).isEqualTo(50);
		assertThat(after.purchases()).containsEntry("cs", 1);
	}

	@Test
	void aGameTheApiNoLongerKnowsIsOver() {
		api.messageFailures.push(new MugloarApiException(404, "", "not found"));

		assertThatExceptionOfType(GameOverException.class).isThrownBy(() -> engine.fetchAds(session))
				.withMessageContaining("the game server no longer knows it");
		assertThat(session.isOver()).isTrue();
	}

	@Test
	void gameOverOnTheShopClosesTheSessionToo() {
		api.shopFailures.push(new MugloarApiException(410, "{\"status\":\"Game Over\"}", "gone"));

		assertThatExceptionOfType(GameOverException.class).isThrownBy(() -> engine.fetchShop(session));
		assertThat(session.isOver()).isTrue();
	}

	@Test
	void solvingAnUnknownOrExpiredAdIsAnInvalidAction() {
		assertThatExceptionOfType(InvalidActionException.class)
				.isThrownBy(() -> engine.solve(session, "gone", "", false))
				.withMessageContaining("expired or does not exist");
		assertThat(session.history()).isEmpty();
	}

	@Test
	void rateLimitOnSolveKeepsItsStatusInsteadOfBecomingAnInvalidAction() {
		api.solveFailures.push(new MugloarApiException(MugloarApiException.TOO_MANY_REQUESTS, "", "slow down"));

		assertThatExceptionOfType(MugloarApiException.class)
				.isThrownBy(() -> engine.solve(session, "easy", "", false))
				.matches(MugloarApiException::isTooManyRequests);
		assertThat(session.history()).isEmpty();
	}

	@Test
	void upstreamServerErrorsPropagate() {
		api.solveFailures.push(new MugloarApiException(503, "", "down"));

		assertThatThrownBy(() -> engine.solve(session, "easy", "", false))
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
		assertThat(session.purchases()).containsEntry("cs", 1);
	}

	@Test
	void actionsOnAFinishedGameAreRejected() {
		engine.solve(session, "hard", "", false);
		engine.solve(session, "hard", "", false);
		engine.solve(session, "hard", "", false);
		assertThat(session.isOver()).isTrue();

		assertThatExceptionOfType(GameOverException.class).isThrownBy(() -> engine.fetchAds(session));
		assertThatExceptionOfType(GameOverException.class)
				.isThrownBy(() -> engine.solve(session, "easy", "", false));
		assertThatExceptionOfType(GameOverException.class).isThrownBy(() -> engine.buy(session, "hpot", "", false));
		assertThatExceptionOfType(GameOverException.class).isThrownBy(() -> engine.investigate(session));
	}

	@Test
	void upstreamGameOverClosesTheSession() {
		api.solveFailures.push(new MugloarApiException(410, "{\"status\":\"Game Over\"}", "gone"));

		assertThatExceptionOfType(GameOverException.class)
				.isThrownBy(() -> engine.solve(session, "easy", "", false));
		assertThat(session.isOver()).isTrue();
		assertThat(session.state().lives()).isZero();
		assertThatExceptionOfType(GameOverException.class).isThrownBy(() -> engine.fetchAds(session));
	}

	@Test
	void investigationIsRecordedAsATurn() {
		Investigation investigation = engine.investigate(session);

		assertThat(investigation.reputation().underworld()).isEqualTo(-1);
		TurnRecord record = investigation.turn();
		assertThat(record.action()).isEqualTo(ActionType.INVESTIGATE);
		assertThat(record.turn()).isEqualTo(1);
		assertThat(record.message()).isEqualTo("People 1, state 0.5, underworld -1");
		assertThat(record.stateAfter().lives()).isEqualTo(3);
		assertThat(session.state().turn()).isEqualTo(1);
		assertThat(session.history()).containsExactly(record);
	}

	@Test
	void turnsAfterAnInvestigationLineUpWithTheApi() {
		engine.investigate(session);

		TurnRecord solved = engine.solve(session, "easy", "", false);

		assertThat(solved.turn()).isEqualTo(2);
		assertThat(session.history()).extracting(TurnRecord::turn).containsExactly(1, 2);
	}

	@Test
	void anInvestigationDoesNotVouchForGoldAndLives() {
		api.gold = 120;
		session = engine.start();
		api.loseNextBuyResponse = true;
		assertThatExceptionOfType(MugloarApiException.class).isThrownBy(() -> engine.buy(session, "cs", "", false));
		assertThat(session.isStateUncertain()).isTrue();

		engine.investigate(session);

		assertThat(session.isStateUncertain()).isTrue();
	}
}
