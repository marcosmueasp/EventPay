package com.eventpay.booking.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eventpay.booking.domain.exception.DomainValidationException;
import com.eventpay.booking.domain.model.Event;
import com.eventpay.booking.domain.port.out.EventRepositoryPort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Casos de uso de eventos sin contexto Spring (RF1–RF3). */
class EventServiceTest {

  private EventRepositoryPort events;
  private EventService service;

  @BeforeEach
  void setUp() {
    events = mock(EventRepositoryPort.class);
    service = new EventService(events);
  }

  @Test
  void createsEventWithSoldZero() {
    when(events.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Event created = service.create("Demo", 10);

    assertNotNull(created.id());
    assertEquals("Demo", created.name());
    assertEquals(10, created.capacity());
    assertEquals(0, created.sold());
    assertEquals(10, created.available());
    verify(events).save(created);
  }

  @Test
  void listsEmptyCatalog() {
    when(events.findAll()).thenReturn(List.of());

    assertTrue(service.list().isEmpty());
    verify(events).findAll();
  }

  @Test
  void rejectsBlankNameWithoutPersisting() {
    assertThrows(DomainValidationException.class, () -> service.create("", 10));
    assertThrows(DomainValidationException.class, () -> service.create("   ", 10));
    assertThrows(DomainValidationException.class, () -> service.create(null, 10));

    verifyNoInteractions(events);
  }

  @Test
  void rejectsNonPositiveCapacityWithoutPersisting() {
    assertThrows(DomainValidationException.class, () -> service.create("Demo", 0));
    assertThrows(DomainValidationException.class, () -> service.create("Demo", -3));

    verifyNoInteractions(events);
  }
}
