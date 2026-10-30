package com.ponomarenko.mugloar.service;

import java.util.List;
import java.util.Map;

import com.ponomarenko.mugloar.TestFixtures;
import com.ponomarenko.mugloar.config.StrategyProperties;
import com.ponomarenko.mugloar.domain.AdDecoder;
import com.ponomarenko.mugloar.strategy.AdAssessment;
import com.ponomarenko.mugloar.strategy.AdEvaluator;
import com.ponomarenko.mugloar.strategy.GameStrategy;
import com.ponomarenko.mugloar.strategy.ShoppingPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class GameServiceTest {

	private FakeMugloarApi api;

	private GameService service;

	@BeforeEach
	void setUp() {
		api = new FakeMugloarApi();
		StrategyProperties props = TestFixtures.defaultStrategy();
		GameEngine engine = new GameEngine(api, new AdDecoder());
		AdEvaluator evaluator = new AdEvaluator(props);
		ShoppingPolicy shoppingPolicy = new ShoppingPolicy(props);
		AutoPlayer autoPlayer = new AutoPlayer(engine, new GameStrategy(evaluator, shoppingPolicy), props);
		service = new GameService(new GameSessionRegistry(), engine, autoPlayer, evaluator, shoppingPolicy);
	}

	@Test
	void startedGamesCanBeLookedUpAndListed() {
		GameSession session = service.startGame();

		assertThat(service.getGame(session.gameId())).isSameAs(session);
		assertThat(service.listGames()).containsExactly(session);
	}

	@Test
	void unknownGameIsReported() {
		assertThatExceptionOfType(GameNotFoundException.class).isThrownBy(() -> service.getGame("nope"))
				.withMessageContaining("nope");
	}

	@Test
	void adsAreRankedForTheCurrentState() {
		GameSession session = service.startGame();

		List<AdAssessment> ads = service.getAds(session.gameId());

		assertThat(ads).extracting(a -> a.ad().adId()).containsExactly("easy", "hard");
		assertThat(ads.get(0).eligible()).isTrue();
		assertThat(ads.get(1).eligible()).isFalse();
	}

	@Test
	void shopOffersReflectGoldAndPurchases() {
		api.gold = 150;
		GameSession session = service.startGame();

		List<ShopOffer> offers = service.getShop(session.gameId());

		assertThat(offers).extracting(o -> o.item().id()).containsExactly("hpot", "cs");
		assertThat(offers).allMatch(ShopOffer::affordable);
		assertThat(offers).filteredOn(ShopOffer::suggested).extracting(o -> o.item().id()).containsExactly("cs");

		service.buy(session.gameId(), "cs");

		List<ShopOffer> after = service.getShop(session.gameId());
		assertThat(after).filteredOn(o -> o.item().id().equals("cs")).singleElement()
				.satisfies(o -> {
					assertThat(o.timesBought()).isEqualTo(1);
					assertThat(o.affordable()).isFalse();
				});
	}

	@Test
	void manualActionsAreRecordedInHistory() {
		GameSession session = service.startGame();

		TurnRecord solve = service.solve(session.gameId(), "easy");

		assertThat(solve.automated()).isFalse();
		assertThat(service.history(session.gameId())).containsExactly(solve);
	}

	@Test
	void historyRemembersWhatHadBeenBoughtAfterEachTurn() {
		api.gold = 150;
		GameSession session = service.startGame();

		service.buy(session.gameId(), "cs");
		service.solve(session.gameId(), "easy");
		service.buy(session.gameId(), "hpot");

		List<TurnRecord> history = service.history(session.gameId());
		assertThat(history.get(0).purchasesAfter()).containsExactlyEntriesOf(Map.of("cs", 1));
		assertThat(history.get(1).purchasesAfter()).containsExactlyEntriesOf(Map.of("cs", 1));
		assertThat(history.get(2).purchasesAfter()).containsOnlyKeys("cs", "hpot");
		assertThat(session.purchases()).containsOnlyKeys("cs", "hpot");
	}

	@Test
	void autoplayStepAndRunUseTheStrategy() {
		GameSession session = service.startGame();

		TurnRecord step = service.autoplayStep(session.gameId());
		AutoplayOutcome outcome = service.autoplay(session.gameId(), 3);

		assertThat(step.automated()).isTrue();
		assertThat(outcome.turns()).hasSize(3);
		assertThat(service.history(session.gameId())).hasSize(4);
	}

	@Test
	void playNewGameStartsAndPlaysInOneGo() {
		AutoplayOutcome outcome = service.playNewGame(5);

		assertThat(outcome.turns()).hasSize(5);
		assertThat(service.listGames()).hasSize(1);
	}

	@Test
	void investigateDelegatesToTheApi() {
		GameSession session = service.startGame();

		assertThat(service.investigate(session.gameId()).people()).isEqualTo(1);
	}
}
