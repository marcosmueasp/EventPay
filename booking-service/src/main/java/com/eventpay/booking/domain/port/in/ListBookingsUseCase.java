package com.eventpay.booking.domain.port.in;

import com.eventpay.booking.domain.model.Booking;
import java.util.List;
import java.util.UUID;

/** Reservas del usuario que hace la petición (RF11, RF12). */
public interface ListBookingsUseCase {

  /**
   * Devuelve solo las reservas del usuario indicado, o lista vacía si no tiene ninguna (RF11). El
   * {@code userId} es el del header interno, ya parseado (RF12).
   */
  List<Booking> listByUser(UUID userId);
}
