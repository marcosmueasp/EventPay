package com.eventpay.booking.infrastructure.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/bookings} (RF4, RF8). No admite {@code userId}: la identidad sale del
 * header interno {@code X-User-Id} (RF12).
 */
public record CreateBookingRequest(
    @NotNull(message = "El eventId es obligatorio")
    UUID eventId,

    @Size(max = 100, message = "La clave de idempotencia no puede superar los 100 caracteres")
    String idempotencyKey) {
}
