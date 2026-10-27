package com.ponomarenko.mugloar.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import com.ponomarenko.mugloar.client.dto.AdDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdDecoderTest {

	private final AdDecoder decoder = new AdDecoder();

	private static String b64(String value) {
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void plainAdIsCopiedThrough() {
		AdDto dto = new AdDto("abc123", "Help Thais to reach an agreement", "18", 7, null, "Piece of cake");

		Ad ad = decoder.decode(dto);

		assertThat(ad.adId()).isEqualTo("abc123");
		assertThat(ad.message()).isEqualTo("Help Thais to reach an agreement");
		assertThat(ad.reward()).isEqualTo(18);
		assertThat(ad.expiresIn()).isEqualTo(7);
		assertThat(ad.probability()).isEqualTo(Probability.PIECE_OF_CAKE);
		assertThat(ad.probabilityLabel()).isEqualTo("Piece of cake");
		assertThat(ad.encryption()).isEqualTo(Encryption.NONE);
		assertThat(ad.wasEncrypted()).isFalse();
	}

	@Test
	void base64AdIsDecodedWhenHinted() {
		AdDto dto = new AdDto(b64("xYz789"), b64("Escort Leandro to the peninsula"), "75", 3, 1, b64("Quite likely"));

		Ad ad = decoder.decode(dto);

		assertThat(ad.adId()).isEqualTo("xYz789");
		assertThat(ad.message()).isEqualTo("Escort Leandro to the peninsula");
		assertThat(ad.reward()).isEqualTo(75);
		assertThat(ad.probability()).isEqualTo(Probability.QUITE_LIKELY);
		assertThat(ad.encryption()).isEqualTo(Encryption.BASE64);
	}

	@Test
	void rot13AdIsDecodedWhenHinted() {
		AdDto dto = new AdDto(AdDecoder.rot13("rot13id"), AdDecoder.rot13("Rescue a chicken"), "40", 2, 2,
				AdDecoder.rot13("Walk in the park"));

		Ad ad = decoder.decode(dto);

		assertThat(ad.adId()).isEqualTo("rot13id");
		assertThat(ad.message()).isEqualTo("Rescue a chicken");
		assertThat(ad.probability()).isEqualTo(Probability.WALK_IN_THE_PARK);
		assertThat(ad.encryption()).isEqualTo(Encryption.ROT13);
	}

	@Test
	void wrongHintFallsBackToTheSchemeThatProducesAKnownLabel() {
		// Hinted as ROT13 but actually Base64.
		AdDto dto = new AdDto(b64("id1"), b64("Some task"), "10", 1, 2, b64("Gamble"));

		Ad ad = decoder.decode(dto);

		assertThat(ad.probability()).isEqualTo(Probability.GAMBLE);
		assertThat(ad.adId()).isEqualTo("id1");
		assertThat(ad.encryption()).isEqualTo(Encryption.BASE64);
	}

	@Test
	void unknownEncryptionCodeStillTriesKnownSchemes() {
		AdDto dto = new AdDto(AdDecoder.rot13("id2"), AdDecoder.rot13("Some task"), "10", 1, 99,
				AdDecoder.rot13("Risky"));

		Ad ad = decoder.decode(dto);

		assertThat(ad.probability()).isEqualTo(Probability.RISKY);
		assertThat(ad.encryption()).isEqualTo(Encryption.ROT13);
	}

	@Test
	void undecodableAdKeepsRawValuesAndIsMarkedUnknown() {
		AdDto dto = new AdDto("!!not-base64!!", "???", "10", 1, 1, "???");

		Ad ad = decoder.decode(dto);

		assertThat(ad.adId()).isEqualTo("!!not-base64!!");
		assertThat(ad.probability()).isEqualTo(Probability.UNKNOWN);
		assertThat(ad.encryption()).isEqualTo(Encryption.UNKNOWN);
	}

	@Test
	void encodedRewardIsDecodedAndGarbageRewardBecomesZero() {
		AdDto encodedReward = new AdDto(b64("id"), b64("task"), b64("42"), 1, 1, b64("Sure thing"));
		AdDto garbageReward = new AdDto("id", "task", "lots", 1, null, "Sure thing");
		AdDto missingReward = new AdDto("id", "task", null, 1, null, "Sure thing");

		assertThat(decoder.decode(encodedReward).reward()).isEqualTo(42);
		assertThat(decoder.decode(garbageReward).reward()).isZero();
		assertThat(decoder.decode(missingReward).reward()).isZero();
	}

	@Test
	void decodeAllPreservesOrder() {
		List<Ad> ads = decoder.decodeAll(List.of(
				new AdDto("a", "first", "1", 1, null, "Sure thing"),
				new AdDto(b64("b"), b64("second"), "2", 1, 1, b64("Gamble"))));

		assertThat(ads).extracting(Ad::adId).containsExactly("a", "b");
	}

	@Test
	void rot13IsItsOwnInverseAndLeavesNonLettersAlone() {
		String original = "Hello, World! 123";
		assertThat(AdDecoder.rot13(AdDecoder.rot13(original))).isEqualTo(original);
		assertThat(AdDecoder.rot13("abc XYZ")).isEqualTo("nop KLM");
		assertThat(AdDecoder.rot13(null)).isNull();
		assertThat(AdDecoder.base64(null)).isNull();
	}
}
