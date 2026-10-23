package com.eventpay.booking.infrastructure.in.web.dto;

/** Formato de error común de la API: {@code {error, message}} (§5 de la spec 002, heredado de 001). */
public record ErrorBody(String error, String message) {
}
