package com.ponomarenko.mugloar.strategy;

/** Coarse risk bucket for presenting ads to a human player. */
public enum RiskLevel {

	SAFE,
	MODERATE,
	DANGEROUS;

	public static RiskLevel of(double successRate) {
		if (successRate >= 0.8) {
			return SAFE;
		}
		if (successRate >= 0.5) {
			return MODERATE;
		}
		return DANGEROUS;
	}
}
