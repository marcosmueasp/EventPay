package com.eventpay.booking.infrastructure.out.persistence;

import com.eventpay.booking.domain.model.Event;
import com.eventpay.booking.domain.port.out.EventRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adaptador JPA de eventos (RF1, RF2, RF4, RF5). */
@Component
public class EventPersistenceAdapter implements EventRepositoryPort {

  private final JpaEventRepo events;

  public EventPersistenceAdapter(JpaEventRepo events) {
    this.events = events;
  }

  @Override
  public Event save(Event event) {
    return events.save(EventEntity.fromDomain(event)).toDomain();
  }

  @Override
  public Optional<Event> findById(UUID eventId) {
    return events.findById(eventId).map(EventEntity::toDomain);
  }

  @Override
  public List<Event> findAll() {
    return events.findAll().stream().map(EventEntity::toDomain).toList();
  }
}
