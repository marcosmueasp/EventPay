package com.eventpay.booking.infrastructure.in.web.dto;

import com.eventpay.booking.domain.model.BookingStatus;
import java.util.UUID;

/** Respuesta de {@code POST /api/bookings} y {@code GET /api/bookings/me} (RF4, RF11). */
public record BookingResponse(UUID id, UUID eventId, UUID userId, BookingStatus status) {
}
