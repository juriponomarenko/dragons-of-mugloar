package com.ponomarenko.mugloar.service;

import java.util.ArrayList;
import java.util.List;

import com.ponomarenko.mugloar.TestFixtures;
import com.ponomarenko.mugloar.client.MugloarApiException;
import com.ponomarenko.mugloar.client.dto.AdDto;
import com.ponomarenko.mugloar.config.StrategyProperties;
import com.ponomarenko.mugloar.domain.AdDecoder;
import com.ponomarenko.mugloar.strategy.AdEvaluator;
import com.ponomarenko.mugloar.strategy.GameStrategy;
import com.ponomarenko.mugloar.strategy.ShoppingPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class AutoPlayerTest {

	private FakeMugloarApi api;

	private GameEngine engine;

	private AutoPlayer autoPlayer;

	private GameSession session;

	/** Milliseconds the player wanted to wait before each retry; nothing actually sleeps. */
	private final List<Long> pauses = new ArrayList<>();

	@BeforeEach
	void setUp() {
		api = new FakeMugloarApi();
		engine = new GameEngine(api, new AdDecoder());
		StrategyProperties props = TestFixtures.testStrategy();
		GameStrategy strategy = new GameStrategy(new AdEvaluator(props), new ShoppingPolicy(props));
		autoPlayer = new AutoPlayer(engine, strategy, props, pauses::add);
		session = engine.start();
	}

	@Test
	void playTurnSolvesTheSafeAdAndRecordsTheReason() {
		TurnRecord record = autoPlayer.playTurn(session);

		assertThat(record.action()).isEqualTo(ActionType.SOLVE);
		assertThat(record.targetId()).isEqualTo("easy");
		assertThat(record.automated()).isTrue();
		assertThat(record.reason()).contains("Best eligible ad");
		assertThat(record.description()).isEqualTo("Help a neighbour");
	}

	@Test
	void playTurnBuysUpgradesOnceGoldAllows() {
		api.gold = 150;
		session = engine.start();

		TurnRecord record = autoPlayer.playTurn(session);

		assertThat(record.action()).isEqualTo(ActionType.BUY);
		assertThat(record.targetId()).isEqualTo("cs");
		assertThat(session.purchases()).containsEntry("cs", 1);
	}

	@Test
	void playTurnOnFinishedGameIsRejected() {
		api.lives = 0;
		session = engine.start();

		assertThatExceptionOfType(GameOverException.class).isThrownBy(() -> autoPlayer.playTurn(session));
	}

	@Test
	void playToEndStopsAtTheTurnLimit() {
		AutoplayOutcome outcome = autoPlayer.playToEnd(session, 4);

		assertThat(outcome.turns()).hasSize(4);
		assertThat(outcome.gameOver()).isFalse();
		assertThat(outcome.stoppedBecause()).isEqualTo("Reached the turn limit of 4");
		assertThat(outcome.finalState().score()).isGreaterThan(0);
	}

	@Test
	void playToEndRunsUntilLivesAreGone() {
		// Only impossible ads and no gold: the fallback keeps trying and the dragon dies.
		api.board = () -> List.of(new AdDto("doom", "Fight the sun", "1000", 5, null, "Impossible"));

		AutoplayOutcome outcome = autoPlayer.playToEnd(session, 100);

		assertThat(outcome.gameOver()).isTrue();
		assertThat(outcome.finalState().lives()).isZero();
		assertThat(outcome.turns()).hasSize(3);
		assertThat(outcome.stoppedBecause()).isEqualTo("Game over");
	}

	@Test
	void expiredAdIsRetriedWithAFreshBoard() {
		api.solveFailures.push(new MugloarApiException(400, "Bad Request", "expired"));

		AutoplayOutcome outcome = autoPlayer.playToEnd(session, 2);

		assertThat(outcome.turns()).hasSize(2);
		assertThat(api.solvedAdIds).containsExactly("easy", "easy");
	}

	@Test
	void givesUpAfterRepeatedUpstreamErrors() {
		for (int i = 0; i < AutoPlayer.MAX_CONSECUTIVE_FAILURES; i++) {
			api.messageFailures.push(new MugloarApiException(502, "", "upstream down"));
		}

		AutoplayOutcome outcome = autoPlayer.playToEnd(session, 10);

		assertThat(outcome.turns()).isEmpty();
		assertThat(outcome.gameOver()).isFalse();
		assertThat(outcome.stoppedBecause()).startsWith("Stopped after repeated upstream errors");
	}

	@Test
	void recoversFromASingleUpstreamError() {
		api.messageFailures.push(new MugloarApiException(502, "", "blip"));

		AutoplayOutcome outcome = autoPlayer.playToEnd(session, 2);

		assertThat(outcome.turns()).hasSize(2);
		assertThat(pauses).containsExactly(250L);
	}

	@Test
	void rateLimitOnSolveIsWaitedOutRatherThanTreatedAsAnExpiredAd() {
		api.solveFailures.push(new MugloarApiException(MugloarApiException.TOO_MANY_REQUESTS, "", "slow down"));

		AutoplayOutcome outcome = autoPlayer.playToEnd(session, 2);

		assertThat(outcome.turns()).hasSize(2);
		assertThat(outcome.stoppedBecause()).isEqualTo("Reached the turn limit of 2");
		assertThat(pauses).containsExactly(15_000L);
	}

	@Test
	void rateLimitOnTheBoardIsWaitedOutTooAndTheWaitGrows() {
		api.messageFailures.push(new MugloarApiException(MugloarApiException.TOO_MANY_REQUESTS, "", "slow down"));
		api.messageFailures.push(new MugloarApiException(MugloarApiException.TOO_MANY_REQUESTS, "", "slow down"));

		AutoplayOutcome outcome = autoPlayer.playToEnd(session, 1);

		assertThat(outcome.turns()).hasSize(1);
		assertThat(pauses).containsExactly(15_000L, 30_000L);
	}

	@Test
	void rateLimitsDoNotCountAsFailuresButPatienceHasALimit() {
		for (int i = 0; i <= AutoPlayer.MAX_RATE_LIMIT_WAITS; i++) {
			api.messageFailures.push(new MugloarApiException(MugloarApiException.TOO_MANY_REQUESTS, "", "slow down"));
		}

		AutoplayOutcome outcome = autoPlayer.playToEnd(session, 5);

		assertThat(outcome.turns()).isEmpty();
		assertThat(outcome.gameOver()).isFalse();
		assertThat(outcome.stoppedBecause()).startsWith("Stopped: the API kept rate-limiting us");
		assertThat(pauses).hasSize(AutoPlayer.MAX_RATE_LIMIT_WAITS).last().isEqualTo(60_000L);
		assertThat(pauses.get(0)).isEqualTo(15_000L);
	}

	@Test
	void aLostPurchaseAnswerIsNotRetriedAsAnotherPurchase() {
		api.gold = 250;
		session = engine.start();
		api.loseNextBuyResponse = true;

		AutoplayOutcome outcome = autoPlayer.playToEnd(session, 1);

		// the game carried the purchase out once; the retry solved an ad instead of buying again
		assertThat(api.boughtItemIds).containsExactly("cs");
		assertThat(outcome.turns()).extracting(TurnRecord::action).containsExactly(ActionType.SOLVE);
		// the solve answer brought the real state back: 250 - 100 for the claws + 20 reward
		assertThat(session.state().gold()).isEqualTo(170);
		assertThat(session.isStateUncertain()).isFalse();
		// and the gold told that the purchase went through, so it is counted and not bought again
		assertThat(session.purchases()).containsEntry("cs", 1);
		assertThat(outcome.turns().get(0).purchasesAfter()).containsEntry("cs", 1);
	}

	@Test
	void aPurchaseThatNeverReachedTheGameIsNotCountedEither() {
		api.gold = 250;
		session = engine.start();
		api.dropNextBuyRequest = true;

		AutoplayOutcome outcome = autoPlayer.playToEnd(session, 2);

		// the retry solved an ad, whose answer showed the gold untouched: the purchase was never made
		assertThat(outcome.turns()).extracting(TurnRecord::action).containsExactly(ActionType.SOLVE, ActionType.BUY);
		assertThat(outcome.turns().get(0).purchasesAfter()).isEmpty();
		// so the ladder bought it on the next turn, once
		assertThat(api.boughtItemIds).containsExactly("cs");
		assertThat(session.purchases()).containsEntry("cs", 1);
	}

	@Test
	void stopsWhenTheBoardIsEmptyAndNothingCanBeBought() {
		api.board = List::of;

		AutoplayOutcome outcome = autoPlayer.playToEnd(session, 10);

		assertThat(outcome.turns()).isEmpty();
		assertThat(outcome.stoppedBecause()).isEqualTo("Stopped: No ads available and nothing affordable to buy");
	}

	@Test
	void aGameOverReportedByTheApiMidRunEndsTheRunCleanly() {
		api.solveFailures.push(new MugloarApiException(410, "{\"status\":\"Game Over\"}", "gone"));

		AutoplayOutcome outcome = autoPlayer.playToEnd(session, 5);

		assertThat(outcome.gameOver()).isTrue();
		assertThat(outcome.turns()).isEmpty();
		assertThat(outcome.stoppedBecause()).isEqualTo("Game over (reported by the API)");
		assertThat(session.isOver()).isTrue();
	}

	@Test
	void turnLimitIsCappedByConfiguration() {
		StrategyProperties capped = new StrategyProperties(2, 50, List.of("cs"), true, 0.8, 0.6, 0.5, 50, List.of(),
				1.0, 2, List.of(), 2);
		GameStrategy strategy = new GameStrategy(new AdEvaluator(capped), new ShoppingPolicy(capped));
		AutoPlayer cappedPlayer = new AutoPlayer(engine, strategy, capped);

		AutoplayOutcome outcome = cappedPlayer.playToEnd(session, 50);

		assertThat(outcome.turns()).hasSize(2);
		assertThat(outcome.stoppedBecause()).isEqualTo("Reached the turn limit of 2");
	}
}
