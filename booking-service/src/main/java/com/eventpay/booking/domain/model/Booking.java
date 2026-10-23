package com.eventpay.booking.domain.model;

import com.eventpay.booking.domain.exception.DomainValidationException;
import java.util.UUID;

/**
 * Reserva de una plaza de un evento por parte de un usuario. POJO puro e inmutable: nace en
 * {@code PENDING} y ninguna operación de D2 cambia su estado (RF4, RF12, RF13).
 *
 * @param idempotencyKey clave de idempotencia, {@code null} si la petición no la trae (RF7)
 */
public record Booking(
    UUID id, UUID eventId, UUID userId, BookingStatus status, String idempotencyKey) {

  public Booking {
    if (id == null) {
      throw new DomainValidationException("El identificador de la reserva es obligatorio");
    }
    if (eventId == null) {
      throw new DomainValidationException("El identificador del evento es obligatorio");
    }
    if (userId == null) {
      throw new DomainValidationException("El identificador del usuario es obligatorio");
    }
    if (status == null) {
      throw new DomainValidationException("El estado de la reserva es obligatorio");
    }
    if (idempotencyKey != null && idempotencyKey.isBlank()) {
      // La clave ausente o en blanco se representa con null; el servicio la normaliza (RF7).
      throw new DomainValidationException(
          "La clave de idempotencia no puede estar vacía; usa null si no hay clave");
    }
  }

  /** Reserva nueva en el único estado de D2. */
  public static Booking pending(UUID id, UUID eventId, UUID userId, String idempotencyKey) {
    return new Booking(id, eventId, userId, BookingStatus.PENDING, idempotencyKey);
  }
}
