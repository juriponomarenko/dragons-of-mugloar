package com.ponomarenko.mugloar.web.dto;

import java.util.List;

public record AdView(String adId, String message, int reward, int expiresIn, String probability, double successRate,
		double expectedValue, double score, String risk, boolean eligible, boolean recommended, String encryption,
		List<String> notes) {
}
