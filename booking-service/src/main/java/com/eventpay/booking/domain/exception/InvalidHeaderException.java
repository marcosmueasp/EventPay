package com.eventpay.booking.domain.exception;

/** El header interno {@code X-User-Id} falta o no es un UUID válido (RF9). Java puro. */
public class InvalidHeaderException extends RuntimeException {

  public InvalidHeaderException(String message) {
    super(message);
  }
}
