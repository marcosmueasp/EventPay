package com.eventpay.booking.application.service;

import com.eventpay.booking.domain.model.Event;
import com.eventpay.booking.domain.port.in.CreateEventUseCase;
import com.eventpay.booking.domain.port.in.ListEventsUseCase;
import com.eventpay.booking.domain.port.out.EventRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Casos de uso de eventos (RF1–RF3). */
@Service
public class EventService implements CreateEventUseCase, ListEventsUseCase {

  private final EventRepositoryPort events;

  public EventService(EventRepositoryPort events) {
    this.events = events;
  }

  /**
   * {@inheritDoc}
   *
   * <p>El {@code Event} nace con {@code sold = 0} y su constructor valida nombre y capacidad, así
   * que una entrada inválida lanza {@code DomainValidationException} antes de tocar persistencia
   * (RF1, RF3).
   */
  @Override
  public Event create(String name, int capacity) {
    Event event = new Event(UUID.randomUUID(), name, capacity, 0);
    return events.save(event);
  }

  @Override
  public List<Event> list() {
    return events.findAll();
  }
}
