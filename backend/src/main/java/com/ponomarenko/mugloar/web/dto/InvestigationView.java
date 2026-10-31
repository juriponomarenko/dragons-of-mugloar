package com.ponomarenko.mugloar.web.dto;

import com.ponomarenko.mugloar.domain.Reputation;

/** A reputation investigation: what was found and the turn it cost. */
public record InvestigationView(Reputation reputation, TurnView turn) {
}
