package com.eventpay.booking.domain.model;

/**
 * Estado de una reserva. En D2 solo existe {@code PENDING}: {@code PAID} y {@code REJECTED} llegan
 * con el resultado del pago (spec 003).
 */
public enum BookingStatus {
  PENDING
}
