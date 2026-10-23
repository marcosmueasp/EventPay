package com.eventpay.booking.domain.port.out;

import com.eventpay.booking.domain.model.Event;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia de eventos (RF1, RF2, RF4, RF5). Sin detalles de JPA. */
public interface EventRepositoryPort {

  /** Persiste un evento y devuelve la versión guardada (RF1). */
  Event save(Event event);

  /** Busca un evento por identificador; vacío si no existe (RF5). */
  Optional<Event> findById(UUID eventId);

  /** Devuelve todos los eventos (RF2). */
  List<Event> findAll();
}
