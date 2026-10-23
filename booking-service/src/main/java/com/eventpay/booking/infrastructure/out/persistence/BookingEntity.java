package com.eventpay.booking.infrastructure.out.persistence;

import com.eventpay.booking.domain.model.Booking;
import com.eventpay.booking.domain.model.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Tabla {@code bookings} de {@code booking-db} (RF4, RF7, RF14). */
@Entity
@Table(name = "bookings")
public class BookingEntity {

  @Id
  private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BookingStatus status;

  /** Anulable y único: varias reservas sin clave conviven como {@code NULL} (RF7, RF14). */
  @Column(name = "idempotency_key", unique = true)
  private String idempotencyKey;

  /** Requerido por JPA. */
  protected BookingEntity() {}

  public BookingEntity(
      UUID id, UUID eventId, UUID userId, BookingStatus status, String idempotencyKey) {
    this.id = id;
    this.eventId = eventId;
    this.userId = userId;
    this.status = status;
    this.idempotencyKey = idempotencyKey;
  }

  /** Mapeo manual dominio -> tabla; sin MapStruct. */
  static BookingEntity fromDomain(Booking booking) {
    return new BookingEntity(
        booking.id(),
        booking.eventId(),
        booking.userId(),
        booking.status(),
        booking.idempotencyKey());
  }

  /** Mapeo manual tabla -> dominio. */
  Booking toDomain() {
    return new Booking(id, eventId, userId, status, idempotencyKey);
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getUserId() {
    return userId;
  }

  public BookingStatus getStatus() {
    return status;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }
}
