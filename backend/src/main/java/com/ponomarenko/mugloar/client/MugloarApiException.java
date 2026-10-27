package com.ponomarenko.mugloar.client;

/**
 * Raised when the Mugloar API returns an error status or cannot be reached at all.
 */
public class MugloarApiException extends RuntimeException {

	/** Status code used when no HTTP response was received (timeout, connection refused). */
	public static final int NO_RESPONSE = 0;

	private final int status;

	private final String responseBody;

	public MugloarApiException(int status, String responseBody, String message, Throwable cause) {
		super(message, cause);
		this.status = status;
		this.responseBody = responseBody;
	}

	public MugloarApiException(int status, String responseBody, String message) {
		this(status, responseBody, message, null);
	}

	public int getStatus() {
		return status;
	}

	public String getResponseBody() {
		return responseBody;
	}

	public boolean isNotFound() {
		return status == 404;
	}

	public boolean isClientError() {
		return status >= 400 && status < 500;
	}
}
