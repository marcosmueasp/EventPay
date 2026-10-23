package com.eventpay.booking.infrastructure.in.web.mapper;

import com.eventpay.booking.domain.model.Booking;
import com.eventpay.booking.infrastructure.in.web.dto.BookingResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/** Mapeo manual dominio -> DTO de reservas; sin MapStruct (RF4, RF11). */
@Component
public class BookingMapper {

  public BookingResponse toResponse(Booking booking) {
    return new BookingResponse(
        booking.id(), booking.eventId(), booking.userId(), booking.status());
  }

  public List<BookingResponse> toList(List<Booking> bookings) {
    return bookings.stream().map(this::toResponse).toList();
  }
}
