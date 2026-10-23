package com.eventpay.booking.domain.model;

import com.eventpay.booking.domain.exception.DomainValidationException;
import com.eventpay.booking.domain.exception.EventFullException;
import java.util.UUID;

/**
 * Evento reservable. POJO puro, inmutable: concentra la regla de aforo y el incremento de
 * {@code sold} (RF1, RF4, RF6, RF15). Invariante: {@code 0 <= sold <= capacity}.
 */
public record Event(UUID id, String name, int capacity, int sold) {

  public Event {
    if (id == null) {
      throw new DomainValidationException("El identificador del evento es obligatorio");
    }
    if (name == null || name.isBlank()) {
      throw new DomainValidationException("El nombre del evento no puede estar vacío");
    }
    if (capacity <= 0) {
      throw new DomainValidationException("La capacidad del evento debe ser mayor que 0");
    }
    if (sold < 0 || sold > capacity) {
      throw new DomainValidationException(
          "Las plazas ocupadas deben estar entre 0 y la capacidad del evento");
    }
  }

  /** Plazas libres: {@code capacity - sold}. Nunca negativa por el invariante. */
  public int available() {
    return capacity - sold;
  }

  /** Quedan plazas por reservar. */
  public boolean hasCapacity() {
    return sold < capacity;
  }

  /**
   * Devuelve una copia con una plaza más ocupada. No muta este evento para que la regla no dependa
   * de quién tenga la referencia, y para que el rollback de la reserva no deje el agregado tocado.
   *
   * @throws EventFullException si ya no quedan plazas
   */
  public Event reserve() {
    if (!hasCapacity()) {
      throw new EventFullException();
    }
    return new Event(id, name, capacity, sold + 1);
  }
}
