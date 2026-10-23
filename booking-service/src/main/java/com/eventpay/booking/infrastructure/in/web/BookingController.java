package com.eventpay.booking.infrastructure.in.web;

import com.eventpay.booking.domain.exception.InvalidHeaderException;
import com.eventpay.booking.domain.port.in.CreateBookingUseCase;
import com.eventpay.booking.domain.port.in.ListBookingsUseCase;
import com.eventpay.booking.infrastructure.in.web.dto.BookingResponse;
import com.eventpay.booking.infrastructure.in.web.dto.CreateBookingRequest;
import com.eventpay.booking.infrastructure.in.web.mapper.BookingMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reservas del usuario identificado por el header interno {@code X-User-Id} (RF4, RF9, RF11, RF12).
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

  static final String USER_ID_HEADER = "X-User-Id";

  private final CreateBookingUseCase createBooking;
  private final ListBookingsUseCase listBookings;
  private final BookingMapper mapper;

  public BookingController(
      CreateBookingUseCase createBooking, ListBookingsUseCase listBookings, BookingMapper mapper) {
    this.createBooking = createBooking;
    this.listBookings = listBookings;
    this.mapper = mapper;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BookingResponse create(
      @RequestHeader(name = USER_ID_HEADER, required = false) String rawUserId,
      @Valid @RequestBody CreateBookingRequest request) {
    UUID userId = parseUserId(rawUserId);
    return mapper.toResponse(
        createBooking.createBooking(request.eventId(), userId, request.idempotencyKey()));
  }

  @GetMapping("/me")
  public List<BookingResponse> mine(
      @RequestHeader(name = USER_ID_HEADER, required = false) String rawUserId) {
    return mapper.toList(listBookings.listByUser(parseUserId(rawUserId)));
  }

  /**
   * El header es el borde HTTP de la identidad: se parsea aquí y nunca en el dominio (RF9, RF12). Se
   * declara {@code required = false} para responder el formato de error propio de la API en vez del
   * 400 por defecto de Spring cuando falta.
   */
  private static UUID parseUserId(String rawUserId) {
    if (rawUserId == null || rawUserId.isBlank()) {
      throw new InvalidHeaderException("El header X-User-Id es obligatorio");
    }
    try {
      return UUID.fromString(rawUserId.trim());
    } catch (IllegalArgumentException ex) {
      throw new InvalidHeaderException("El header X-User-Id debe ser un UUID válido");
    }
  }
}
