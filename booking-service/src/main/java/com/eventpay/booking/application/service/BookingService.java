package com.eventpay.booking.application.service;

import com.eventpay.booking.domain.exception.DomainValidationException;
import com.eventpay.booking.domain.exception.EventFullException;
import com.eventpay.booking.domain.exception.EventNotFoundException;
import com.eventpay.booking.domain.exception.IdempotencyConflictException;
import com.eventpay.booking.domain.exception.InvalidHeaderException;
import com.eventpay.booking.domain.model.Booking;
import com.eventpay.booking.domain.model.Event;
import com.eventpay.booking.domain.port.in.CreateBookingUseCase;
import com.eventpay.booking.domain.port.in.ListBookingsUseCase;
import com.eventpay.booking.domain.port.out.BookingRepositoryPort;
import com.eventpay.booking.domain.port.out.EventRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de reservas (RF4–RF13). */
@Service
public class BookingService implements CreateBookingUseCase, ListBookingsUseCase {

  /** Límite de la clave de idempotencia que fija el contrato (RF8). */
  static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

  private final EventRepositoryPort events;
  private final BookingRepositoryPort bookings;

  public BookingService(EventRepositoryPort events, BookingRepositoryPort bookings) {
    this.events = events;
    this.bookings = bookings;
  }

  /**
   * {@inheritDoc}
   *
   * <p>El orden de los pasos es la precedencia de RF10: validación (400) -> evento inexistente
   * (404) -> clave repetida (409) -> aforo lleno (409). Se devuelve siempre el primer rechazo.
   *
   * <p>La transacción empieza aquí (plan §5.6): la escritura condicional del aforo y el rollback de
   * la reserva son una sola unidad, y el adaptador queda sustituible sin cambiar la semántica.
   */
  @Override
  @Transactional
  public Booking createBooking(UUID eventId, UUID userId, String idempotencyKey) {
    String key = normalizeKey(idempotencyKey);
    if (key != null && key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
      throw new DomainValidationException(
          "La clave de idempotencia no puede superar los "
              + MAX_IDEMPOTENCY_KEY_LENGTH
              + " caracteres");
    }
    if (eventId == null) {
      throw new DomainValidationException("El identificador del evento es obligatorio");
    }
    if (userId == null) {
      // La identidad sale del header interno; sin ella no se mira siquiera si el evento existe (RF9).
      throw new InvalidHeaderException("Falta el header interno X-User-Id");
    }

    Event event =
        events.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));

    // La unicidad de la clave es global, no por usuario ni por evento (RF7).
    if (key != null && bookings.existsByKey(key)) {
      throw new IdempotencyConflictException();
    }

    // Comprobación orientativa: la garantía real contra la sobreventa es la escritura condicional
    // del puerto dentro de esta misma transacción (RF15).
    if (!event.hasCapacity()) {
      throw new EventFullException();
    }

    Booking booking = Booking.pending(UUID.randomUUID(), eventId, userId, key);
    return bookings.saveReservingPlace(booking);
  }

  @Override
  public List<Booking> listByUser(UUID userId) {
    if (userId == null) {
      throw new InvalidHeaderException("Falta el header interno X-User-Id");
    }
    return bookings.findByUserId(userId);
  }

  /**
   * Clave presente = texto recortado; ausente, vacía o solo con espacios = sin clave ({@code null}),
   * que en base de datos se guarda como {@code NULL} y no participa en el {@code unique} (RF7).
   */
  static String normalizeKey(String rawKey) {
    if (rawKey == null) {
      return null;
    }
    String trimmed = rawKey.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
