package com.eventpay.booking.infrastructure.in.web.dto;

import java.util.UUID;

/** Respuesta de {@code GET /api/events} y {@code POST /api/events} (RF1, RF2). */
public record EventResponse(UUID id, String name, int capacity, int sold) {
}
