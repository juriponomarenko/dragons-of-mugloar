package com.ponomarenko.mugloar.client;

/**
 * Raised when the Mugloar API returns an error status or cannot be reached at all.
 */
public class MugloarApiException extends RuntimeException {

	/** Status code used when no HTTP response was received (timeout, connection refused). */
	public static final int NO_RESPONSE = 0;

	/** The API rate-limits clients with this status; the request itself was fine. */
	public static final int TOO_MANY_REQUESTS = 429;

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

	public boolean isClientError() {
		return status >= 400 && status < 500;
	}

	public boolean isTooManyRequests() {
		return status == TOO_MANY_REQUESTS;
	}

	/**
	 * Whether the API rejected this particular request (unknown or expired ad, bad id), as
	 * opposed to asking the client to slow down: a rate limit is a 4xx too, but retrying the
	 * same request later is exactly the right thing to do.
	 */
	public boolean isRejectedRequest() {
		return isClientError() && !isTooManyRequests();
	}
}
