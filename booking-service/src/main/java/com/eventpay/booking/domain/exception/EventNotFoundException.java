package com.eventpay.booking.domain.exception;

import java.util.UUID;

/** El evento solicitado no existe (RF5). Java puro, sin anotaciones. */
public class EventNotFoundException extends RuntimeException {

  public EventNotFoundException(UUID eventId) {
    super("No existe el evento " + eventId);
  }
}
