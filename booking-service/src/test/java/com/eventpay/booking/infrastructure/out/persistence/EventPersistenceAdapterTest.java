package com.eventpay.booking.infrastructure.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eventpay.booking.domain.model.Event;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Adaptador de eventos sin base de datos real (RF1, RF2, RF5). */
class EventPersistenceAdapterTest {

  private static final UUID EVENT_ID = UUID.randomUUID();

  private JpaEventRepo events;
  private EventPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    events = mock(JpaEventRepo.class);
    adapter = new EventPersistenceAdapter(events);
  }

  @Test
  void savePersistsTheDomainEventAndReturnsIt() {
    Event event = new Event(EVENT_ID, "Demo", 10, 3);
    when(events.save(any(EventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Event saved = adapter.save(event);

    assertEquals(event, saved);
    ArgumentCaptor<EventEntity> entity = ArgumentCaptor.forClass(EventEntity.class);
    verify(events).save(entity.capture());
    assertEquals(EVENT_ID, entity.getValue().getId());
    assertEquals("Demo", entity.getValue().getName());
    assertEquals(10, entity.getValue().getCapacity());
    assertEquals(3, entity.getValue().getSold());
  }

  @Test
  void findByIdMapsEntityToDomain() {
    when(events.findById(EVENT_ID)).thenReturn(Optional.of(new EventEntity(EVENT_ID, "Demo", 5, 2)));

    Optional<Event> found = adapter.findById(EVENT_ID);

    assertTrue(found.isPresent());
    assertEquals(new Event(EVENT_ID, "Demo", 5, 2), found.get());
  }

  @Test
  void findByIdReturnsEmptyWhenTheEventDoesNotExist() {
    when(events.findById(EVENT_ID)).thenReturn(Optional.empty());

    assertTrue(adapter.findById(EVENT_ID).isEmpty());
  }

  @Test
  void findAllMapsEveryEntity() {
    UUID otherId = UUID.randomUUID();
    when(events.findAll())
        .thenReturn(List.of(new EventEntity(EVENT_ID, "A", 2, 1), new EventEntity(otherId, "B", 4, 0)));

    List<Event> all = adapter.findAll();

    assertEquals(2, all.size());
    assertEquals(1, all.get(0).sold());
    assertEquals(4, all.get(1).capacity());
  }
}
