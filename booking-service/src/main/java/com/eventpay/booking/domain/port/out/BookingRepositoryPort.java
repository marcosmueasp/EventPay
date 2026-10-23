package com.eventpay.booking.domain.port.out;

import com.eventpay.booking.domain.model.Booking;
import java.util.List;
import java.util.UUID;

/**
 * Persistencia de reservas (RF4, RF7, RF11, RF14, RF15). Sin detalles de JPA y, a propósito, sin
 * puerto de publicación de eventos: eso nace en la spec 003 (RF16).
 */
public interface BookingRepositoryPort {

  /**
   * Indica si la clave ya está usada por alguna reserva, con cualquier evento (RF7). Solo se llama
   * con claves presentes: las reservas sin clave se guardan con {@code null} y no participan.
   */
  boolean existsByKey(String idempotencyKey);

  /** Reservas del usuario indicado (RF11). */
  List<Booking> findByUserId(UUID userId);

  /**
   * Guarda la reserva y ocupa una plaza de su evento en la misma transacción (RF4, RF15): la
   * comprobación de aforo es una escritura condicional, no un {@code if} previo.
   *
   * @throws com.eventpay.booking.domain.exception.IdempotencyConflictException si la clave choca con
   *     la restricción de unicidad, aunque dos peticiones lleguen a la vez (RF7, RF14)
   * @throws com.eventpay.booking.domain.exception.EventFullException si ya no quedan plazas; en ese
   *     caso no queda ninguna reserva guardada (RF6, RF15)
   */
  Booking saveReservingPlace(Booking booking);
}
