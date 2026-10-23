package com.eventpay.booking.domain.port.in;

import com.eventpay.booking.domain.model.Booking;
import java.util.UUID;

/** Alta de reserva (RF4–RF10, RF12, RF13, RF15). */
public interface CreateBookingUseCase {

  /**
   * Crea una reserva en {@code PENDING} y ocupa una plaza del evento (RF4).
   *
   * <p>Los UUID llegan ya parseados por la capa web: un header {@code X-User-Id} ausente o que no
   * sea UUID se rechaza con 400 antes de entrar aquí (RF9). La clave de idempotencia llega en crudo
   * y es este caso de uso quien la normaliza: {@code null}, cadena vacía o solo espacios equivalen a
   * "sin clave" (RF7).
   *
   * <p>Orden de rechazos, siempre el primero que se cumpla (RF10): validación de entrada ->
   * evento inexistente -> clave repetida -> aforo lleno.
   *
   * @param eventId        evento a reservar (RF4)
   * @param userId         identidad tomada del header interno, nunca del cuerpo (RF12)
   * @param idempotencyKey clave cruda del cliente, hasta 100 caracteres; puede ser {@code null}
   * @throws com.eventpay.booking.domain.exception.DomainValidationException si la identidad no está
   *     presente o la clave supera los 100 caracteres (RF8, RF9)
   * @throws com.eventpay.booking.domain.exception.EventNotFoundException si el evento no existe
   *     (RF5)
   * @throws com.eventpay.booking.domain.exception.IdempotencyConflictException si la clave ya está
   *     usada, con el mismo o con otro evento (RF7, RF14)
   * @throws com.eventpay.booking.domain.exception.EventFullException si el evento está lleno (RF6)
   */
  Booking createBooking(UUID eventId, UUID userId, String idempotencyKey);
}
