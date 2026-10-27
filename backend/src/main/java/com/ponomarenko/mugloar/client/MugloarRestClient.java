package com.ponomarenko.mugloar.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import com.ponomarenko.mugloar.client.dto.AdDto;
import com.ponomarenko.mugloar.client.dto.GameStartResponse;
import com.ponomarenko.mugloar.client.dto.PurchaseResponse;
import com.ponomarenko.mugloar.client.dto.ReputationResponse;
import com.ponomarenko.mugloar.client.dto.ShopItemDto;
import com.ponomarenko.mugloar.client.dto.SolveResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link MugloarApiClient} backed by Spring's {@link RestClient}.
 */
@Component
public class MugloarRestClient implements MugloarApiClient {

	private static final Logger log = LoggerFactory.getLogger(MugloarRestClient.class);

	private static final ParameterizedTypeReference<List<AdDto>> AD_LIST = new ParameterizedTypeReference<>() {
	};

	private static final ParameterizedTypeReference<List<ShopItemDto>> SHOP_LIST = new ParameterizedTypeReference<>() {
	};

	private final RestClient restClient;

	public MugloarRestClient(RestClient mugloarApiRestClient) {
		this.restClient = mugloarApiRestClient;
	}

	@Override
	public GameStartResponse startGame() {
		return execute("start game", () -> restClient.post()
				.uri("/game/start")
				.retrieve()
				.onStatus(HttpStatusCode::isError, this::raise)
				.body(GameStartResponse.class));
	}

	@Override
	public List<AdDto> getMessages(String gameId) {
		return execute("get messages", () -> restClient.get()
				.uri("/{gameId}/messages", gameId)
				.retrieve()
				.onStatus(HttpStatusCode::isError, this::raise)
				.body(AD_LIST));
	}

	@Override
	public SolveResponse solve(String gameId, String adId) {
		return execute("solve ad", () -> restClient.post()
				.uri("/{gameId}/solve/{adId}", gameId, adId)
				.retrieve()
				.onStatus(HttpStatusCode::isError, this::raise)
				.body(SolveResponse.class));
	}

	@Override
	public List<ShopItemDto> getShop(String gameId) {
		return execute("get shop", () -> restClient.get()
				.uri("/{gameId}/shop", gameId)
				.retrieve()
				.onStatus(HttpStatusCode::isError, this::raise)
				.body(SHOP_LIST));
	}

	@Override
	public PurchaseResponse buy(String gameId, String itemId) {
		return execute("buy item", () -> restClient.post()
				.uri("/{gameId}/shop/buy/{itemId}", gameId, itemId)
				.retrieve()
				.onStatus(HttpStatusCode::isError, this::raise)
				.body(PurchaseResponse.class));
	}

	@Override
	public ReputationResponse investigateReputation(String gameId) {
		return execute("investigate reputation", () -> restClient.post()
				.uri("/{gameId}/investigate/reputation", gameId)
				.retrieve()
				.onStatus(HttpStatusCode::isError, this::raise)
				.body(ReputationResponse.class));
	}

	private <T> T execute(String operation, Supplier<T> call) {
		try {
			T body = call.get();
			if (body == null) {
				throw new MugloarApiException(MugloarApiException.NO_RESPONSE, "",
						"Mugloar API returned an empty body for '" + operation + "'");
			}
			return body;
		}
		catch (MugloarApiException ex) {
			throw ex;
		}
		catch (ResourceAccessException ex) {
			log.warn("Mugloar API unreachable during '{}': {}", operation, ex.getMessage());
			throw new MugloarApiException(MugloarApiException.NO_RESPONSE, "",
					"Mugloar API unreachable during '" + operation + "': " + ex.getMessage(), ex);
		}
		catch (RestClientException ex) {
			throw new MugloarApiException(MugloarApiException.NO_RESPONSE, "",
					"Mugloar API call '" + operation + "' failed: " + ex.getMessage(), ex);
		}
	}

	private void raise(HttpRequest request, ClientHttpResponse response) throws IOException {
		int status = response.getStatusCode().value();
		String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
		log.debug("Mugloar API {} {} -> {}", request.getMethod(), request.getURI(), status);
		throw new MugloarApiException(status, body, "Mugloar API responded with HTTP " + status + " for "
				+ request.getMethod() + " " + request.getURI().getPath());
	}
}
