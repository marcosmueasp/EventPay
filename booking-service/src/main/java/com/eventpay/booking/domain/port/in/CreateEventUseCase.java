package com.eventpay.booking.domain.port.in;

import com.eventpay.booking.domain.model.Event;

/** Alta de evento del catálogo de demo (RF1, RF3). */
public interface CreateEventUseCase {

  /**
   * Crea un evento con {@code sold = 0}. El {@code id} nuevo lo genera el servicio (RF1).
   *
   * @param name     nombre ya validado como no vacío por el borde HTTP (RF3)
   * @param capacity aforo; debe ser mayor que 0 (RF3)
   * @throws com.eventpay.booking.domain.exception.DomainValidationException si el nombre está vacío
   *     o la capacidad no es mayor que 0
   */
  Event create(String name, int capacity);
}
