package com.ponomarenko.mugloar.client.dto;

/**
 * Raw ad as returned by the API. When {@code encrypted} is set, the textual fields are
 * encoded and {@code reward} may arrive as a string, hence it is kept as text here.
 */
public record AdDto(String adId, String message, String reward, int expiresIn, Integer encrypted, String probability) {
}
