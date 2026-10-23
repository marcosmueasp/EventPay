package com.eventpay.booking.infrastructure.in.web;

import com.eventpay.booking.domain.port.in.CreateEventUseCase;
import com.eventpay.booking.domain.port.in.ListEventsUseCase;
import com.eventpay.booking.infrastructure.in.web.dto.CreateEventRequest;
import com.eventpay.booking.infrastructure.in.web.dto.EventResponse;
import com.eventpay.booking.infrastructure.in.web.mapper.EventMapper;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo de eventos de demo/seed: no exige el header interno (RF1, RF2). */
@RestController
@RequestMapping("/api/events")
public class EventController {

  private final CreateEventUseCase createEvent;
  private final ListEventsUseCase listEvents;
  private final EventMapper mapper;

  public EventController(
      CreateEventUseCase createEvent, ListEventsUseCase listEvents, EventMapper mapper) {
    this.createEvent = createEvent;
    this.listEvents = listEvents;
    this.mapper = mapper;
  }

  @GetMapping
  public List<EventResponse> list() {
    return mapper.toList(listEvents.list());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public EventResponse create(@Valid @RequestBody CreateEventRequest request) {
    return mapper.toResponse(createEvent.create(request.name(), request.capacity()));
  }
}
