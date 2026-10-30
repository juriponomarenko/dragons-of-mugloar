package com.ponomarenko.mugloar.client;

import java.util.List;

import com.ponomarenko.mugloar.client.dto.AdDto;
import com.ponomarenko.mugloar.client.dto.GameStartResponse;
import com.ponomarenko.mugloar.client.dto.PurchaseResponse;
import com.ponomarenko.mugloar.client.dto.ReputationResponse;
import com.ponomarenko.mugloar.client.dto.ShopItemDto;
import com.ponomarenko.mugloar.client.dto.SolveResponse;
import com.ponomarenko.mugloar.config.MugloarApiProperties;
import com.ponomarenko.mugloar.config.RestClientConfig;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(MugloarRestClient.class)
@Import(RestClientConfig.class)
@EnableConfigurationProperties(MugloarApiProperties.class)
class MugloarRestClientTest {

	private static final String BASE = "https://game.example/api/v2";

	@Autowired
	private MugloarRestClient client;

	@Autowired
	private MockRestServiceServer server;

	@Test
	void startGameParsesResponse() {
		server.expect(requestTo(BASE + "/game/start"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("User-Agent", "mugloar-backend/1.0"))
				.andRespond(withSuccess("""
						{"gameId":"JuMXKG6p","lives":3,"gold":0,"level":0,"score":0,"highScore":1234,"turn":0}
						""", MediaType.APPLICATION_JSON));

		GameStartResponse response = client.startGame();

		assertThat(response.gameId()).isEqualTo("JuMXKG6p");
		assertThat(response.lives()).isEqualTo(3);
		assertThat(response.highScore()).isEqualTo(1234);
		server.verify();
	}

	@Test
	void messagesAcceptNumericAndTextualRewardsAndUnknownFields() {
		server.expect(requestTo(BASE + "/g1/messages"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						[{"adId":"a1","message":"Help","reward":18,"expiresIn":7,"encrypted":null,"probability":"Piece of cake","extra":1},
						 {"adId":"YTI=","message":"SGVscA==","reward":"NzU=","expiresIn":3,"encrypted":1,"probability":"UXVpdGUgbGlrZWx5"}]
						""", MediaType.APPLICATION_JSON));

		List<AdDto> ads = client.getMessages("g1");

		assertThat(ads).hasSize(2);
		assertThat(ads.get(0).reward()).isEqualTo("18");
		assertThat(ads.get(0).encrypted()).isNull();
		assertThat(ads.get(1).reward()).isEqualTo("NzU=");
		assertThat(ads.get(1).encrypted()).isEqualTo(1);
	}

	@Test
	void solveParsesResponse() {
		server.expect(requestTo(BASE + "/g1/solve/a1"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("""
						{"success":true,"lives":3,"gold":18,"score":18,"highScore":0,"turn":1,"message":"You did it"}
						""", MediaType.APPLICATION_JSON));

		SolveResponse response = client.solve("g1", "a1");

		assertThat(response.success()).isTrue();
		assertThat(response.message()).isEqualTo("You did it");
	}

	@Test
	void shopAndPurchaseParseResponses() {
		server.expect(requestTo(BASE + "/g1/shop"))
				.andRespond(withSuccess("""
						[{"id":"hpot","name":"Healing potion","cost":50}]
						""", MediaType.APPLICATION_JSON));
		server.expect(requestTo(BASE + "/g1/shop/buy/hpot"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("""
						{"shoppingSuccess":true,"gold":0,"lives":4,"level":0,"turn":2}
						""", MediaType.APPLICATION_JSON));

		List<ShopItemDto> shop = client.getShop("g1");
		PurchaseResponse purchase = client.buy("g1", "hpot");

		assertThat(shop).singleElement().extracting(ShopItemDto::cost).isEqualTo(50);
		assertThat(purchase.shoppingSuccess()).isTrue();
		assertThat(purchase.lives()).isEqualTo(4);
	}

	@Test
	void purchaseAcceptsTextualSuccessFlag() {
		server.expect(requestTo(BASE + "/g1/shop/buy/cs"))
				.andRespond(withSuccess("""
						{"shoppingSuccess":"false","gold":10,"lives":3,"level":0,"turn":2}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.buy("g1", "cs").shoppingSuccess()).isFalse();
	}

	@Test
	void reputationParsesResponse() {
		server.expect(requestTo(BASE + "/g1/investigate/reputation"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess("""
						{"people":1.5,"state":-2,"underworld":0}
						""", MediaType.APPLICATION_JSON));

		ReputationResponse reputation = client.investigateReputation("g1");

		assertThat(reputation.people()).isEqualTo(1.5);
		assertThat(reputation.state()).isEqualTo(-2);
	}

	@Test
	void errorStatusBecomesApiExceptionWithStatusAndBody() {
		server.expect(requestTo(BASE + "/g1/solve/bad"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_HTML).body("<pre>Bad</pre>"));

		assertThatExceptionOfType(MugloarApiException.class)
				.isThrownBy(() -> client.solve("g1", "bad"))
				.satisfies(ex -> {
					assertThat(ex.getStatus()).isEqualTo(400);
					assertThat(ex.isClientError()).isTrue();
					assertThat(ex.getResponseBody()).contains("Bad");
					assertThat(ex.getMessage()).contains("HTTP 400");
				});
	}

	@Test
	void notFoundIsRecognised() {
		server.expect(requestTo(BASE + "/nope/messages")).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatExceptionOfType(MugloarApiException.class)
				.isThrownBy(() -> client.getMessages("nope"))
				.satisfies(ex -> assertThat(ex.getStatus()).isEqualTo(404));
	}

	@Test
	void emptyBodyIsAnError() {
		server.expect(requestTo(BASE + "/game/start")).andRespond(withSuccess());

		assertThatExceptionOfType(MugloarApiException.class)
				.isThrownBy(client::startGame)
				.withMessageContaining("empty body");
	}
}
