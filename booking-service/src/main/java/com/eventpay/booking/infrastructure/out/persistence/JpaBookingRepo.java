package com.eventpay.booking.infrastructure.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio JPA de reservas (RF7, RF11, RF14). */
public interface JpaBookingRepo extends JpaRepository<BookingEntity, UUID> {

  /** Unicidad global de la clave, con cualquier evento (RF7). Las claves {@code NULL} no cuentan. */
  boolean existsByIdempotencyKey(String idempotencyKey);

  /** Reservas de un usuario (RF11). */
  List<BookingEntity> findByUserId(UUID userId);
}
