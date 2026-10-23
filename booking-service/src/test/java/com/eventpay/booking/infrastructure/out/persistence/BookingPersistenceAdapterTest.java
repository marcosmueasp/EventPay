package com.eventpay.booking.infrastructure.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eventpay.booking.domain.exception.EventFullException;
import com.eventpay.booking.domain.exception.IdempotencyConflictException;
import com.eventpay.booking.domain.model.Booking;
import com.eventpay.booking.domain.model.BookingStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

/** Adaptador de reservas sin base de datos real: aforo por escritura condicional (RF4, RF7, RF15). */
class BookingPersistenceAdapterTest {

  private static final UUID EVENT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  private JpaBookingRepo bookings;
  private JpaEventRepo events;
  private BookingPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    bookings = mock(JpaBookingRepo.class);
    events = mock(JpaEventRepo.class);
    adapter = new BookingPersistenceAdapter(bookings, events);
  }

  @Test
  void savesBookingAndIncrementsSoldWhenOneRowIsUpdated() {
    Booking booking = Booking.pending(UUID.randomUUID(), EVENT_ID, USER_ID, "clave-1");
    when(bookings.saveAndFlush(any(BookingEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(events.incrementSoldIfAvailable(EVENT_ID)).thenReturn(1);

    Booking saved = adapter.saveReservingPlace(booking);

    assertEquals(booking, saved);
    ArgumentCaptor<BookingEntity> entity = ArgumentCaptor.forClass(BookingEntity.class);
    verify(bookings).saveAndFlush(entity.capture());
    assertEquals(booking.id(), entity.getValue().getId());
    assertEquals(EVENT_ID, entity.getValue().getEventId());
    assertEquals(USER_ID, entity.getValue().getUserId());
    assertEquals(BookingStatus.PENDING, entity.getValue().getStatus());
    assertEquals("clave-1", entity.getValue().getIdempotencyKey());
    verify(events).incrementSoldIfAvailable(EVENT_ID);
  }

  @Test
  void throwsEventFullWhenConditionalUpdateAffectsNoRows() {
    Booking booking = Booking.pending(UUID.randomUUID(), EVENT_ID, USER_ID, "clave-nueva");
    when(bookings.saveAndFlush(any(BookingEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    // El UPDATE ... WHERE sold < capacity no actualiza nada: la última plaza ya está ocupada.
    when(events.incrementSoldIfAvailable(EVENT_ID)).thenReturn(0);

    EventFullException thrown =
        assertThrows(EventFullException.class, () -> adapter.saveReservingPlace(booking));

    assertEquals("Evento sin plazas disponibles", thrown.getMessage());
    // El insert va primero (para que gane el 409 de idempotencia); el rollback de la transacción del
    // servicio es lo que deja la tabla sin esa reserva (RF15).
    verify(bookings).saveAndFlush(any(BookingEntity.class));
  }

  @Test
  void translatesUniqueViolationToIdempotencyConflictWithoutTouchingCapacity() {
    Booking booking = Booking.pending(UUID.randomUUID(), EVENT_ID, USER_ID, "usada");
    when(bookings.saveAndFlush(any(BookingEntity.class)))
        .thenThrow(new DataIntegrityViolationException("uk_bookings_idempotency_key"));

    IdempotencyConflictException thrown =
        assertThrows(IdempotencyConflictException.class, () -> adapter.saveReservingPlace(booking));

    assertEquals("Petición ya procesada: clave de idempotencia repetida", thrown.getMessage());
    // Si la clave choca, el aforo ni se toca (RF7, RF10).
    verify(events, never()).incrementSoldIfAvailable(any(UUID.class));
  }

  @Test
  void insertsBookingBeforeRunningTheConditionalUpdate() {
    Booking booking = Booking.pending(UUID.randomUUID(), EVENT_ID, USER_ID, "clave-1");
    when(bookings.saveAndFlush(any(BookingEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(events.incrementSoldIfAvailable(EVENT_ID)).thenReturn(1);

    adapter.saveReservingPlace(booking);

    InOrder inOrder = inOrder(bookings, events);
    inOrder.verify(bookings).saveAndFlush(any(BookingEntity.class));
    inOrder.verify(events).incrementSoldIfAvailable(EVENT_ID);
  }

  @Test
  void bookingWithoutKeyIsStoredAsNullAndDoesNotCollide() {
    when(bookings.saveAndFlush(any(BookingEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(events.incrementSoldIfAvailable(EVENT_ID)).thenReturn(1);

    adapter.saveReservingPlace(Booking.pending(UUID.randomUUID(), EVENT_ID, USER_ID, null));
    adapter.saveReservingPlace(Booking.pending(UUID.randomUUID(), EVENT_ID, USER_ID, null));

    ArgumentCaptor<BookingEntity> saved = ArgumentCaptor.forClass(BookingEntity.class);
    verify(bookings, times(2)).saveAndFlush(saved.capture());
    saved.getAllValues().forEach(entity -> assertNull(entity.getIdempotencyKey()));
  }

  @Test
  void existsByKeyDelegatesToRepository() {
    when(bookings.existsByIdempotencyKey("usada")).thenReturn(true);

    assertTrue(adapter.existsByKey("usada"));
    verify(bookings).existsByIdempotencyKey("usada");
  }

  @Test
  void findByUserIdMapsEntitiesToDomain() {
    BookingEntity entity = new BookingEntity(
        UUID.randomUUID(), EVENT_ID, USER_ID, BookingStatus.PENDING, null);
    when(bookings.findByUserId(USER_ID)).thenReturn(List.of(entity));

    List<Booking> found = adapter.findByUserId(USER_ID);

    assertEquals(1, found.size());
    assertEquals(entity.getId(), found.get(0).id());
    assertEquals(EVENT_ID, found.get(0).eventId());
    assertEquals(USER_ID, found.get(0).userId());
    assertEquals(BookingStatus.PENDING, found.get(0).status());
    assertNull(found.get(0).idempotencyKey());
  }
}
