package com.eventpay.booking.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eventpay.booking.domain.exception.DomainValidationException;
import com.eventpay.booking.domain.exception.EventFullException;
import com.eventpay.booking.domain.exception.EventNotFoundException;
import com.eventpay.booking.domain.exception.IdempotencyConflictException;
import com.eventpay.booking.domain.exception.InvalidHeaderException;
import com.eventpay.booking.domain.model.Booking;
import com.eventpay.booking.domain.model.BookingStatus;
import com.eventpay.booking.domain.model.Event;
import com.eventpay.booking.domain.port.out.BookingRepositoryPort;
import com.eventpay.booking.domain.port.out.EventRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/** Casos de uso de reservas sin contexto Spring (RF4–RF13). */
class BookingServiceTest {

  private static final UUID EVENT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  private EventRepositoryPort events;
  private BookingRepositoryPort bookings;
  private BookingService service;

  @BeforeEach
  void setUp() {
    events = mock(EventRepositoryPort.class);
    bookings = mock(BookingRepositoryPort.class);
    service = new BookingService(events, bookings);
  }

  @Test
  void createsPendingBookingAndOccupiesPlace() {
    when(events.findById(EVENT_ID)).thenReturn(Optional.of(new Event(EVENT_ID, "Demo", 2, 0)));
    when(bookings.saveReservingPlace(any(Booking.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Booking created = service.createBooking(EVENT_ID, USER_ID, "clave-1");

    assertEquals(BookingStatus.PENDING, created.status());
    assertEquals(EVENT_ID, created.eventId());
    assertEquals(USER_ID, created.userId());
    assertEquals("clave-1", created.idempotencyKey());
    // El incremento de sold va dentro de saveReservingPlace: el servicio delega ahí (RF4, RF15).
    verify(bookings).saveReservingPlace(any(Booking.class));
  }

  @Test
  void unknownEventThrowsNotFoundWithoutSaving() {
    when(events.findById(EVENT_ID)).thenReturn(Optional.empty());

    assertThrows(
        EventNotFoundException.class,
        () -> service.createBooking(EVENT_ID, USER_ID, "clave-1"));

    verify(bookings, never()).saveReservingPlace(any(Booking.class));
  }

  @Test
  void fullEventThrowsConflictWithoutSaving() {
    when(events.findById(EVENT_ID)).thenReturn(Optional.of(new Event(EVENT_ID, "Demo", 1, 1)));

    EventFullException thrown =
        assertThrows(
            EventFullException.class,
            () -> service.createBooking(EVENT_ID, USER_ID, "clave-nueva"));

    assertEquals("Evento sin plazas disponibles", thrown.getMessage());
    verify(bookings, never()).saveReservingPlace(any(Booking.class));
  }

  @Test
  void repeatedKeyThrowsIdempotencyConflict() {
    when(events.findById(EVENT_ID)).thenReturn(Optional.of(new Event(EVENT_ID, "Demo", 2, 0)));
    when(bookings.existsByKey("usada")).thenReturn(true);

    IdempotencyConflictException thrown =
        assertThrows(
            IdempotencyConflictException.class,
            () -> service.createBooking(EVENT_ID, USER_ID, "usada"));

    assertEquals("Petición ya procesada: clave de idempotencia repetida", thrown.getMessage());
    verify(bookings, never()).saveReservingPlace(any(Booking.class));
  }

  @Test
  void repeatedKeyWithAnotherEventAlsoThrowsIdempotencyConflict() {
    UUID otherEventId = UUID.randomUUID();
    when(events.findById(otherEventId))
        .thenReturn(Optional.of(new Event(otherEventId, "Otro", 5, 0)));
    // La unicidad de la clave es global, no por evento (RF7, RF14).
    when(bookings.existsByKey("usada")).thenReturn(true);

    assertThrows(
        IdempotencyConflictException.class,
        () -> service.createBooking(otherEventId, USER_ID, "usada"));

    verify(bookings, never()).saveReservingPlace(any(Booking.class));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void blankKeyIsStoredAsNullAndSkipsIdempotencyCheck(String rawKey) {
    when(events.findById(EVENT_ID)).thenReturn(Optional.of(new Event(EVENT_ID, "Demo", 3, 0)));
    when(bookings.saveReservingPlace(any(Booking.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Booking created = service.createBooking(EVENT_ID, USER_ID, rawKey);

    assertNull(created.idempotencyKey());
    verify(bookings, never()).existsByKey(anyString());
  }

  @Test
  void keyLongerThan100CharsIsRejectedBeforeAnyLookup() {
    String tooLong = "x".repeat(101);

    assertThrows(
        DomainValidationException.class,
        () -> service.createBooking(EVENT_ID, USER_ID, tooLong));

    // 400 de validación antes que el 404 del evento (RF8, RF10).
    verifyNoInteractions(events, bookings);
  }

  @Test
  void missingIdentityIsRejectedBeforeAnyLookup() {
    // El header no-UUID lo corta el borde HTTP (RF9); aquí se fija que una identidad ausente nunca
    // llega a los puertos, así que el 400 siempre gana al 404 (RF10).
    assertThrows(
        InvalidHeaderException.class, () -> service.createBooking(EVENT_ID, null, "clave-1"));

    verifyNoInteractions(events, bookings);
  }

  @Test
  void unknownEventWinsOverRepeatedKey() {
    when(events.findById(EVENT_ID)).thenReturn(Optional.empty());
    when(bookings.existsByKey("usada")).thenReturn(true);

    assertThrows(
        EventNotFoundException.class,
        () -> service.createBooking(EVENT_ID, USER_ID, "usada"));

    verify(bookings, never()).existsByKey(anyString());
  }

  @Test
  void repeatedKeyWinsOverFullEvent() {
    when(events.findById(EVENT_ID)).thenReturn(Optional.of(new Event(EVENT_ID, "Demo", 1, 1)));
    when(bookings.existsByKey("usada")).thenReturn(true);

    assertThrows(
        IdempotencyConflictException.class,
        () -> service.createBooking(EVENT_ID, USER_ID, "usada"));

    verify(bookings, never()).saveReservingPlace(any(Booking.class));
  }

  @Test
  void bookingTakesUserIdFromTheIdentityArgument() {
    when(events.findById(EVENT_ID)).thenReturn(Optional.of(new Event(EVENT_ID, "Demo", 2, 0)));
    when(bookings.saveReservingPlace(any(Booking.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.createBooking(EVENT_ID, USER_ID, null);

    ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
    verify(bookings).saveReservingPlace(saved.capture());
    // La identidad viene del header y nunca de un userId del cuerpo (RF12).
    assertEquals(USER_ID, saved.getValue().userId());
  }

  @Test
  void listsOnlyBookingsOfTheRequestedUser() {
    Booking mine = Booking.pending(UUID.randomUUID(), EVENT_ID, USER_ID, null);
    when(bookings.findByUserId(USER_ID)).thenReturn(List.of(mine));

    List<Booking> found = service.listByUser(USER_ID);

    assertEquals(1, found.size());
    assertEquals(USER_ID, found.get(0).userId());
    verify(bookings).findByUserId(USER_ID);
  }

  @Test
  void userWithoutBookingsGetsEmptyList() {
    when(bookings.findByUserId(USER_ID)).thenReturn(List.of());

    assertTrue(service.listByUser(USER_ID).isEmpty());
  }

  @Test
  void normalizeKeyTrimsAndMapsBlankToNull() {
    assertNull(BookingService.normalizeKey(null));
    assertNull(BookingService.normalizeKey(""));
    assertNull(BookingService.normalizeKey("   "));
    assertEquals("clave-1", BookingService.normalizeKey(" clave-1 "));
  }

  @Test
  void missingEventIdIsRejectedAsValidationBeforeAnyLookup() {
    assertThrows(
        DomainValidationException.class, () -> service.createBooking(null, USER_ID, "clave-1"));

    verifyNoInteractions(events, bookings);
  }
}
