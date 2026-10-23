package com.eventpay.booking.infrastructure.in.web;

import com.eventpay.booking.domain.exception.DomainValidationException;
import com.eventpay.booking.domain.exception.EventFullException;
import com.eventpay.booking.domain.exception.EventNotFoundException;
import com.eventpay.booking.domain.exception.IdempotencyConflictException;
import com.eventpay.booking.domain.exception.InvalidHeaderException;
import com.eventpay.booking.infrastructure.in.web.dto.ErrorBody;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce excepciones al formato {@code {error, message}} de §5 de la spec 002. Los mensajes de los
 * dos {@code 409} son contrato literal y los aporta la propia excepción de dominio.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorBody validation(MethodArgumentNotValidException exception) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .findFirst()
            .orElse("Petición inválida");
    return new ErrorBody("bad_request", message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorBody malformed() {
    return new ErrorBody("bad_request", "El cuerpo de la petición no es válido");
  }

  /** Validación de negocio (RF3, RF8) y header {@code X-User-Id} inválido (RF9). */
  @ExceptionHandler({DomainValidationException.class, InvalidHeaderException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorBody badRequest(RuntimeException exception) {
    return new ErrorBody("bad_request", exception.getMessage());
  }

  @ExceptionHandler(EventNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorBody notFound(EventNotFoundException exception) {
    return new ErrorBody("not_found", exception.getMessage());
  }

  /** Aforo lleno (RF6) y clave de idempotencia repetida (RF7), con mensajes literales distintos. */
  @ExceptionHandler({EventFullException.class, IdempotencyConflictException.class})
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorBody conflict(RuntimeException exception) {
    return new ErrorBody("conflict", exception.getMessage());
  }
}
