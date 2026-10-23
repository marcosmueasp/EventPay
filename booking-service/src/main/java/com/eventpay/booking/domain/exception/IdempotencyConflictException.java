package com.eventpay.booking.domain.exception;

/**
 * La clave de idempotencia ya está usada por otra reserva, con cualquier evento (RF7, RF14). El
 * mensaje es contrato literal de la API (§5 de la spec 002).
 */
public class IdempotencyConflictException extends RuntimeException {

  public static final String MESSAGE = "Petición ya procesada: clave de idempotencia repetida";

  public IdempotencyConflictException() {
    super(MESSAGE);
  }
}
