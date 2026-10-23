package com.eventpay.booking.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eventpay.booking.domain.exception.DomainValidationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Reserva recién creada: siempre PENDING y con la identidad del usuario del header (RF4, RF12, RF13). */
class BookingTest {

  private static final UUID EVENT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  @Test
  void pendingBookingAlwaysStartsAsPending() {
    Booking booking = Booking.pending(UUID.randomUUID(), EVENT_ID, USER_ID, "clave-1");

    assertEquals(BookingStatus.PENDING, booking.status());
    assertEquals(EVENT_ID, booking.eventId());
    assertEquals(USER_ID, booking.userId());
    assertEquals("clave-1", booking.idempotencyKey());
  }

  @Test
  void pendingBookingWithoutKeyKeepsItNull() {
    Booking booking = Booking.pending(UUID.randomUUID(), EVENT_ID, USER_ID, null);

    assertNull(booking.idempotencyKey());
  }

  @Test
  void pendingBookingRejectsNullIdentifiers() {
    assertThrows(DomainValidationException.class,
        () -> Booking.pending(null, EVENT_ID, USER_ID, null));
    assertThrows(DomainValidationException.class,
        () -> Booking.pending(UUID.randomUUID(), null, USER_ID, null));
    assertThrows(DomainValidationException.class,
        () -> Booking.pending(UUID.randomUUID(), EVENT_ID, null, null));
  }

  @Test
  void bookingRejectsBlankKey() {
    // Una clave en blanco no llega hasta aquí: el servicio la normaliza a null (RF7).
    assertThrows(DomainValidationException.class,
        () -> Booking.pending(UUID.randomUUID(), EVENT_ID, USER_ID, "   "));
  }
}
