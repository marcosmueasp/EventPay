package com.eventpay.booking.infrastructure.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio JPA de eventos (RF1, RF2, RF4, RF5). */
public interface JpaEventRepo extends JpaRepository<EventEntity, UUID> {

  /**
   * Escritura condicional del aforo (RF15): ocupa una plaza solo si quedan libres. Devuelve las
   * filas afectadas, y ese número —no un {@code if} previo— es la garantía contra la sobreventa.
   *
   * <p>{@code flushAutomatically} vuelca la reserva pendiente antes del update y
   * {@code clearAutomatically} evita que el contexto de persistencia sirva un {@code sold} antiguo.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update EventEntity e set e.sold = e.sold + 1 where e.id = :id and e.sold < e.capacity")
  int incrementSoldIfAvailable(@Param("id") UUID id);
}
