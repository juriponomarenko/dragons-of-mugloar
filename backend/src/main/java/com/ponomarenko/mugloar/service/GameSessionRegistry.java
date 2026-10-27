package com.ponomarenko.mugloar.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory store of game sessions. Games are short-lived and tied to the upstream API, so a
 * simple map is sufficient; a persistent store could replace this behind the same interface.
 */
@Component
public class GameSessionRegistry {

	private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

	public GameSession register(GameSession session) {
		sessions.put(session.gameId(), session);
		return session;
	}

	public GameSession require(String gameId) {
		GameSession session = sessions.get(gameId);
		if (session == null) {
			throw new GameNotFoundException(gameId);
		}
		return session;
	}

	public List<GameSession> all() {
		return sessions.values().stream()
				.sorted(Comparator.comparing(GameSession::createdAt).reversed())
				.toList();
	}
}
