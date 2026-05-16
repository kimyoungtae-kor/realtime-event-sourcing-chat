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
			.map(error -> error.getField() + " 값이 올바르지 않습니다")
			.orElse("요청 값 검증에 실패했습니다");
		return ResponseEntity.badRequest()
			.body(new ErrorResponse("VALIDATION_FAILED", message, now()));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
		return ResponseEntity.badRequest()
			.body(new ErrorResponse("VALIDATION_FAILED", "요청 값 검증에 실패했습니다", now()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.badRequest()
			.body(new ErrorResponse("BAD_REQUEST", "잘못된 요청입니다", now()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		String name = exception.getName();
		return ResponseEntity.badRequest()
			.body(new ErrorResponse(
				"INVALID_REQUEST_PARAMETER",
				"요청 파라미터가 올바르지 않습니다: " + name,
				now()
			));
	}

	@ExceptionHandler(ConversionFailedException.class)
	public ResponseEntity<ErrorResponse> handleConversionFailed(ConversionFailedException exception) {
		return ResponseEntity.badRequest()
			.body(new ErrorResponse(
				"INVALID_REQUEST_PARAMETER",
				"요청 파라미터 형식이 올바르지 않습니다",
				now()
			));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingRequestParameter(MissingServletRequestParameterException exception) {
		return ResponseEntity.badRequest()
			.body(new ErrorResponse(
				"MISSING_REQUEST_PARAMETER",
				"필수 요청 파라미터가 없습니다: " + exception.getParameterName(),
				now()
			));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException exception) {
		return ResponseEntity.badRequest()
			.body(new ErrorResponse("INVALID_REQUEST_BODY", "요청 본문 형식이 올바르지 않습니다", now()));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ErrorResponse("RESOURCE_NOT_FOUND", "리소스를 찾을 수 없습니다", now()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(new ErrorResponse("INTERNAL_SERVER_ERROR", "예상하지 못한 서버 오류가 발생했습니다", now()));
	}

	private OffsetDateTime now() {
		return UtcDateTimes.toOffsetDateTime(UtcDateTimes.now());
	}
}
