package com.eventpay.identity.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "El email es obligatorio")
    String email,

    @NotBlank(message = "La password es obligatoria")
    String password) {
}
