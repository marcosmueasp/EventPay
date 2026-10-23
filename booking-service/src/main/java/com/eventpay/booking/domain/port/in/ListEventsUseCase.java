package com.eventpay.booking.domain.port.in;

import com.eventpay.booking.domain.model.Event;
import java.util.List;

/** Catálogo de eventos (RF2). */
public interface ListEventsUseCase {

  /**
   * Devuelve todos los eventos con su {@code sold} actual, o lista vacía si no hay ninguno (RF2).
   */
  List<Event> list();
}
