package com.eventpay.booking.infrastructure.out.persistence;

import com.eventpay.booking.domain.exception.EventFullException;
import com.eventpay.booking.domain.exception.IdempotencyConflictException;
import com.eventpay.booking.domain.model.Booking;
import com.eventpay.booking.domain.port.out.BookingRepositoryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Adaptador JPA de reservas (RF4, RF7, RF11, RF14, RF15). No abre transacción propia: la unidad de
 * trabajo es la de {@code BookingService.createBooking}.
 */
@Component
public class BookingPersistenceAdapter implements BookingRepositoryPort {

  private final JpaBookingRepo bookings;
  private final JpaEventRepo events;

  public BookingPersistenceAdapter(JpaBookingRepo bookings, JpaEventRepo events) {
    this.bookings = bookings;
    this.events = events;
  }

  @Override
  public boolean existsByKey(String idempotencyKey) {
    return bookings.existsByIdempotencyKey(idempotencyKey);
  }

  @Override
  public List<Booking> findByUserId(UUID userId) {
    return bookings.findByUserId(userId).stream().map(BookingEntity::toDomain).toList();
  }

  /**
   * Inserta la reserva y después ocupa la plaza con el {@code UPDATE} condicional, en ese orden y en
   * la misma transacción: si la clave de idempotencia choca, gana su {@code 409} sobre el de aforo
   * (RF10); si no hay plaza, el {@code 409} de aforo revierte también la reserva (RF15).
   *
   * <p>{@code saveAndFlush} es necesario: sin volcar el insert, la violación de unicidad no
   * aparecería hasta el commit, es decir, <em>después</em> del update y con el aforo ya decidido.
   */
  @Override
  public Booking saveReservingPlace(Booking booking) {
    try {
      bookings.saveAndFlush(BookingEntity.fromDomain(booking));
    } catch (DataIntegrityViolationException ex) {
      throw new IdempotencyConflictException();
    }

    int updatedRows = events.incrementSoldIfAvailable(booking.eventId());
    if (updatedRows == 0) {
      throw new EventFullException();
    }
    return booking;
  }
}
