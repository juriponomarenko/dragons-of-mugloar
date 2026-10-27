package com.ponomarenko.mugloar.domain;

/**
 * Encoding schemes the game applies to some ads. The API signals them with the numeric
 * {@code encrypted} field.
 */
public enum Encryption {

	NONE(null),
	BASE64(1),
	ROT13(2),
	UNKNOWN(-1);

	private final Integer code;

	Encryption(Integer code) {
		this.code = code;
	}

	public Integer code() {
		return code;
	}

	public static Encryption fromCode(Integer code) {
		if (code == null) {
			return NONE;
		}
		for (Encryption encryption : values()) {
			if (code.equals(encryption.code)) {
				return encryption;
			}
		}
		return UNKNOWN;
	}
}
