package com.eventpay.booking.infrastructure.in.web.mapper;

import com.eventpay.booking.domain.model.Event;
import com.eventpay.booking.infrastructure.in.web.dto.EventResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/** Mapeo manual dominio -> DTO de eventos; sin MapStruct (RF1, RF2). */
@Component
public class EventMapper {

  public EventResponse toResponse(Event event) {
    return new EventResponse(event.id(), event.name(), event.capacity(), event.sold());
  }

  public List<EventResponse> toList(List<Event> events) {
    return events.stream().map(this::toResponse).toList();
  }
}
