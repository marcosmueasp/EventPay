package com.eventpay.booking.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventpay.booking.domain.exception.DomainValidationException;
import com.eventpay.booking.domain.exception.EventFullException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regla de aforo del evento (RF1, RF4, RF6, RF15). Sin contexto Spring (RNF2). */
class EventTest {

  private static final UUID ID = UUID.randomUUID();

  @Test
  void newEventStartsWithAllPlacesAvailable() {
    Event event = new Event(ID, "Demo", 10, 0);

    assertTrue(event.hasCapacity());
    assertEquals(10, event.available());
    assertEquals(0, event.sold());
  }

  @Test
  void eventWithOnePlaceLeftStillHasCapacity() {
    Event event = new Event(ID, "Demo", 2, 1);

    assertTrue(event.hasCapacity());
    assertEquals(1, event.available());
  }

  @Test
  void fullEventHasNoCapacityAndZeroAvailable() {
    Event event = new Event(ID, "Demo", 2, 2);

    assertFalse(event.hasCapacity());
    assertEquals(0, event.available());
  }

  @Test
  void reserveReturnsCopyWithSoldIncremented() {
    Event event = new Event(ID, "Demo", 2, 0);

    Event reserved = event.reserve();

    assertEquals(1, reserved.sold());
    assertEquals(1, reserved.available());
    assertEquals("Demo", reserved.name());
    assertEquals(ID, reserved.id());
    // El evento original no se muta: reserve() devuelve una copia.
    assertEquals(0, event.sold());
  }

  @Test
  void reservingLastPlaceLeavesEventFull() {
    Event event = new Event(ID, "Demo", 1, 0);

    Event reserved = event.reserve();

    assertEquals(1, reserved.sold());
    assertFalse(reserved.hasCapacity());
    assertEquals(0, reserved.available());
  }

  @Test
  void reservingFullEventThrowsDomainExceptionWithContractMessage() {
    Event full = new Event(ID, "Demo", 1, 1);

    EventFullException thrown = assertThrows(EventFullException.class, full::reserve);

    assertEquals("Evento sin plazas disponibles", thrown.getMessage());
    assertEquals(1, full.sold());
  }

  @Test
  void cannotBuildEventWithoutId() {
    assertThrows(DomainValidationException.class, () -> new Event(null, "Demo", 1, 0));
  }

  @Test
  void cannotBuildEventWithBlankName() {
    assertThrows(DomainValidationException.class, () -> new Event(ID, null, 1, 0));
    assertThrows(DomainValidationException.class, () -> new Event(ID, "", 1, 0));
    assertThrows(DomainValidationException.class, () -> new Event(ID, "   ", 1, 0));
  }

  @Test
  void cannotBuildEventWithNonPositiveCapacity() {
    assertThrows(DomainValidationException.class, () -> new Event(ID, "Demo", 0, 0));
    assertThrows(DomainValidationException.class, () -> new Event(ID, "Demo", -1, 0));
  }

  @Test
  void cannotBuildEventWithSoldOutOfBounds() {
    assertThrows(DomainValidationException.class, () -> new Event(ID, "Demo", 2, -1));
    assertThrows(DomainValidationException.class, () -> new Event(ID, "Demo", 2, 3));
  }
}
