package com.eventpay.booking.domain.exception;

/**
 * El evento ya no tiene plazas disponibles (RF6, RF15). El mensaje es contrato literal de la API
 * (§5 de la spec 002).
 */
public class EventFullException extends RuntimeException {

  public static final String MESSAGE = "Evento sin plazas disponibles";

  public EventFullException() {
    super(MESSAGE);
  }
}
