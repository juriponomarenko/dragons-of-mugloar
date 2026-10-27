package com.ponomarenko.mugloar.domain;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import com.ponomarenko.mugloar.client.dto.AdDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Turns raw {@link AdDto}s into decoded {@link Ad}s.
 * <p>
 * The game obfuscates some ads ({@code encrypted = 1} is Base64, {@code encrypted = 2} is
 * ROT13). Rather than trusting the code blindly, the decoder tries the hinted scheme first
 * and verifies the result by checking that the decoded probability is a label the game is
 * known to use. If the hint does not produce a recognisable label, the other schemes are
 * tried, which keeps the player working should the API add or renumber a scheme.
 */
@Component
public class AdDecoder {

	private static final Logger log = LoggerFactory.getLogger(AdDecoder.class);

	public List<Ad> decodeAll(List<AdDto> dtos) {
		List<Ad> ads = new ArrayList<>(dtos.size());
		for (AdDto dto : dtos) {
			ads.add(decode(dto));
		}
		return ads;
	}

	public Ad decode(AdDto dto) {
		Objects.requireNonNull(dto, "dto");
		Encryption hinted = Encryption.fromCode(dto.encrypted());
		if (hinted == Encryption.NONE) {
			return build(dto, UnaryOperator.identity(), Encryption.NONE);
		}
		for (Encryption candidate : candidateOrder(hinted)) {
			UnaryOperator<String> decoder = decoderFor(candidate);
			try {
				if (Probability.isKnownLabel(decoder.apply(dto.probability()))) {
					return build(dto, decoder, candidate);
				}
			}
			catch (IllegalArgumentException ex) {
				// not valid input for this scheme, try the next one
			}
		}
		log.warn("Could not decode ad {} with encryption code {}; using raw values", dto.adId(), dto.encrypted());
		return build(dto, UnaryOperator.identity(), Encryption.UNKNOWN);
	}

	private static List<Encryption> candidateOrder(Encryption hinted) {
		List<Encryption> order = new ArrayList<>(List.of(Encryption.BASE64, Encryption.ROT13));
		if (order.remove(hinted)) {
			order.add(0, hinted);
		}
		return order;
	}

	private static UnaryOperator<String> decoderFor(Encryption encryption) {
		return switch (encryption) {
			case BASE64 -> AdDecoder::base64;
			case ROT13 -> AdDecoder::rot13;
			default -> UnaryOperator.identity();
		};
	}

	private static Ad build(AdDto dto, UnaryOperator<String> decode, Encryption encryption) {
		String label = decode.apply(dto.probability());
		return new Ad(
				decode.apply(dto.adId()),
				decode.apply(dto.message()),
				parseReward(dto.reward(), decode),
				dto.expiresIn(),
				Probability.fromLabel(label),
				label,
				encryption);
	}

	/** Reward is documented as a string and may itself be encoded; be lenient. */
	static int parseReward(String raw, UnaryOperator<String> decode) {
		if (raw == null) {
			return 0;
		}
		String value = raw.trim();
		try {
			return Integer.parseInt(value);
		}
		catch (NumberFormatException ignored) {
			try {
				return Integer.parseInt(decode.apply(value).trim());
			}
			catch (RuntimeException ex) {
				return 0;
			}
		}
	}

	static String base64(String value) {
		if (value == null) {
			return null;
		}
		return new String(Base64.getDecoder().decode(value.trim()), StandardCharsets.UTF_8);
	}

	static String rot13(String value) {
		if (value == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder(value.length());
		for (char c : value.toCharArray()) {
			if (c >= 'a' && c <= 'z') {
				sb.append((char) ('a' + (c - 'a' + 13) % 26));
			}
			else if (c >= 'A' && c <= 'Z') {
				sb.append((char) ('A' + (c - 'A' + 13) % 26));
			}
			else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
