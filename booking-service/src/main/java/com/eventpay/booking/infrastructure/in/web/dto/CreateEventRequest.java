package com.eventpay.booking.infrastructure.in.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code POST /api/events} (RF1, RF3). */
public record CreateEventRequest(
    @NotBlank(message = "El nombre del evento es obligatorio")
    String name,

    @NotNull(message = "La capacidad del evento es obligatoria")
    @Min(value = 1, message = "La capacidad del evento debe ser mayor que 0")
    Integer capacity) {
}
