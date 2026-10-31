package com.ponomarenko.mugloar.service;

import com.ponomarenko.mugloar.domain.Reputation;

/**
 * The outcome of a reputation investigation: what was found and the turn it cost.
 *
 * @param reputation the reputation as the API reported it
 * @param turn the recorded turn
 */
public record Investigation(Reputation reputation, TurnRecord turn) {
}
