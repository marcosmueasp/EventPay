package com.eventpay.booking.infrastructure.out.persistence;

import com.eventpay.booking.domain.model.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Tabla {@code events} de {@code booking-db} (RF1, RF2, RF4). */
@Entity
@Table(name = "events")
public class EventEntity {

  @Id
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private int capacity;

  @Column(nullable = false)
  private int sold;

  /** Requerido por JPA. */
  protected EventEntity() {}

  public EventEntity(UUID id, String name, int capacity, int sold) {
    this.id = id;
    this.name = name;
    this.capacity = capacity;
    this.sold = sold;
  }

  /** Mapeo manual dominio -> tabla; sin MapStruct. */
  static EventEntity fromDomain(Event event) {
    return new EventEntity(event.id(), event.name(), event.capacity(), event.sold());
  }

  /** Mapeo manual tabla -> dominio; el constructor del record revalida el invariante. */
  Event toDomain() {
    return new Event(id, name, capacity, sold);
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getCapacity() {
    return capacity;
  }

  public int getSold() {
    return sold;
  }
}
