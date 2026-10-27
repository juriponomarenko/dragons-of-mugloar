package com.ponomarenko.mugloar.web;

import java.util.List;

import com.ponomarenko.mugloar.client.MugloarApiException;
import com.ponomarenko.mugloar.service.GameNotFoundException;
import com.ponomarenko.mugloar.service.GameOverException;
import com.ponomarenko.mugloar.service.InvalidActionException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every failure to an RFC 9457 problem document, so the frontend can rely on one error
 * shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(GameNotFoundException.class)
	ProblemDetail gameNotFound(GameNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Game not found", ex.getMessage());
	}

	@ExceptionHandler(GameOverException.class)
	ProblemDetail gameOver(GameOverException ex) {
		return problem(HttpStatus.CONFLICT, "Game over", ex.getMessage());
	}

	@ExceptionHandler(InvalidActionException.class)
	ProblemDetail invalidAction(InvalidActionException ex) {
		return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Action not possible", ex.getMessage());
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ProblemDetail constraintViolation(ConstraintViolationException ex) {
		List<String> errors = ex.getConstraintViolations().stream()
				.map(v -> v.getPropertyPath() + ": " + v.getMessage())
				.sorted()
				.toList();
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request validation failed");
		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler(MugloarApiException.class)
	ProblemDetail upstream(MugloarApiException ex) {
		log.warn("Upstream game API failure: {}", ex.getMessage());
		HttpStatus status = ex.getStatus() == MugloarApiException.NO_RESPONSE ? HttpStatus.SERVICE_UNAVAILABLE
				: HttpStatus.BAD_GATEWAY;
		ProblemDetail problem = problem(status, "Game API error", ex.getMessage());
		problem.setProperty("upstreamStatus", ex.getStatus());
		return problem;
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception ex) {
		log.error("Unhandled error", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred");
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<String> errors = ex.getParameterValidationResults().stream()
				.flatMap(r -> r.getResolvableErrors().stream().map(e -> describe(r, e)))
				.sorted()
				.toList();
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request validation failed");
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<String> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + ": " + e.getDefaultMessage())
				.sorted()
				.toList();
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request validation failed");
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}

	/** Names the offending field for body errors and the parameter for everything else. */
	private static String describe(ParameterValidationResult result, MessageSourceResolvable error) {
		String name = error instanceof FieldError fieldError ? fieldError.getField()
				: result.getMethodParameter().getParameterName();
		return name + ": " + error.getDefaultMessage();
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}
