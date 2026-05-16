package com.sokind.chat.common.error;

import java.time.OffsetDateTime;

import com.sokind.chat.common.time.UtcDateTimes;

import jakarta.validation.ConstraintViolationException;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
		return ResponseEntity.status(exception.getStatus())
			.body(new ErrorResponse(exception.getCode(), exception.getMessage(), now()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.findFirst()
			.map(error -> error.getField() + " " + error.getDefaultMessage())
			.orElse("Request validation failed");
		return ResponseEntity.badRequest()
			.body(new ErrorResponse("VALIDATION_FAILED", message, now()));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
		return ResponseEntity.badRequest()
			.body(new ErrorResponse("VALIDATION_FAILED", exception.getMessage(), now()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.badRequest()
			.body(new ErrorResponse("BAD_REQUEST", exception.getMessage(), now()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		String name = exception.getName();
		return ResponseEntity.badRequest()
			.body(new ErrorResponse(
				"INVALID_REQUEST_PARAMETER",
				"Invalid request parameter: " + name,
				now()
			));
	}

	@ExceptionHandler(ConversionFailedException.class)
	public ResponseEntity<ErrorResponse> handleConversionFailed(ConversionFailedException exception) {
		return ResponseEntity.badRequest()
			.body(new ErrorResponse(
				"INVALID_REQUEST_PARAMETER",
				"Invalid request parameter format",
				now()
			));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingRequestParameter(MissingServletRequestParameterException exception) {
		return ResponseEntity.badRequest()
			.body(new ErrorResponse(
				"MISSING_REQUEST_PARAMETER",
				"Missing request parameter: " + exception.getParameterName(),
				now()
			));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException exception) {
		return ResponseEntity.badRequest()
			.body(new ErrorResponse("INVALID_REQUEST_BODY", "Request body is malformed", now()));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ErrorResponse("RESOURCE_NOT_FOUND", "Resource not found", now()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(new ErrorResponse("INTERNAL_SERVER_ERROR", "Unexpected server error", now()));
	}

	private OffsetDateTime now() {
		return UtcDateTimes.toOffsetDateTime(UtcDateTimes.now());
	}
}
