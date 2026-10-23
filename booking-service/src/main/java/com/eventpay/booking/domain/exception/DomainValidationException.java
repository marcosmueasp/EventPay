package com.eventpay.booking.domain.exception;

/** Validación de una regla de negocio del dominio (RF3, RF8, RF9). Java puro, sin anotaciones. */
public class DomainValidationException extends RuntimeException {

  public DomainValidationException(String message) {
    super(message);
  }
}
