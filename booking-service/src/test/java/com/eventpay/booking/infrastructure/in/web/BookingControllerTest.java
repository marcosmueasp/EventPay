package com.eventpay.booking.infrastructure.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventpay.booking.domain.exception.EventFullException;
import com.eventpay.booking.domain.exception.EventNotFoundException;
import com.eventpay.booking.domain.exception.IdempotencyConflictException;
import com.eventpay.booking.domain.model.Booking;
import com.eventpay.booking.domain.model.BookingStatus;
import com.eventpay.booking.domain.port.in.CreateBookingUseCase;
import com.eventpay.booking.domain.port.in.ListBookingsUseCase;
import com.eventpay.booking.infrastructure.in.web.mapper.BookingMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Contrato HTTP de reservas de §5 de la spec 002, sin base de datos (RF4–RF12). */
@WebMvcTest(BookingController.class)
@Import(BookingMapper.class)
class BookingControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID EVENT_ID = UUID.randomUUID();

  @Autowired private MockMvc mvc;

  @MockBean private CreateBookingUseCase createBooking;

  @MockBean private ListBookingsUseCase listBookings;

  @Test
  void createsPendingBookingAndReturns201() throws Exception {
    Booking booking = Booking.pending(UUID.randomUUID(), EVENT_ID, USER_ID, "clave-1");
    when(createBooking.createBooking(EVENT_ID, USER_ID, "clave-1")).thenReturn(booking);

    mvc.perform(
            post("/api/bookings")
                .header(BookingController.USER_ID_HEADER, USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + EVENT_ID + "\",\"idempotencyKey\":\"clave-1\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(booking.id().toString()))
        .andExpect(jsonPath("$.eventId").value(EVENT_ID.toString()))
        .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.status").value(BookingStatus.PENDING.name()));
  }

  @Test
  void ignoresUserIdFromTheBodyAndUsesTheHeader() throws Exception {
    UUID otherUser = UUID.randomUUID();
    Booking booking = Booking.pending(UUID.randomUUID(), EVENT_ID, USER_ID, null);
    when(createBooking.createBooking(eq(EVENT_ID), eq(USER_ID), any())).thenReturn(booking);

    mvc.perform(
            post("/api/bookings")
                .header(BookingController.USER_ID_HEADER, USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"eventId\":\""
                        + EVENT_ID
                        + "\",\"userId\":\""
                        + otherUser
                        + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value(USER_ID.toString()));
  }

  @Test
  void rejectsMissingIdentityHeaderWith400() throws Exception {
    mvc.perform(
            post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + EVENT_ID + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"))
        .andExpect(jsonPath("$.message").value("El header X-User-Id es obligatorio"));

    verifyNoInteractions(createBooking);
  }

  @Test
  void rejectsNonUuidIdentityHeaderWith400() throws Exception {
    mvc.perform(
            post("/api/bookings")
                .header(BookingController.USER_ID_HEADER, "no-es-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + EVENT_ID + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"))
        .andExpect(jsonPath("$.message").value("El header X-User-Id debe ser un UUID válido"));

    verifyNoInteractions(createBooking);
  }

  @Test
  void rejectsBlankIdentityHeaderWith400() throws Exception {
    mvc.perform(
            post("/api/bookings")
                .header(BookingController.USER_ID_HEADER, "   ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + EVENT_ID + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"));

    verifyNoInteractions(createBooking);
  }

  @Test
  void rejectsMissingEventIdWith400() throws Exception {
    mvc.perform(
            post("/api/bookings")
                .header(BookingController.USER_ID_HEADER, USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"clave-1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"))
        .andExpect(jsonPath("$.message").value("El eventId es obligatorio"));

    verifyNoInteractions(createBooking);
  }

  @Test
  void rejectsEventIdThatIsNotUuidWith400() throws Exception {
    mvc.perform(
            post("/api/bookings")
                .header(BookingController.USER_ID_HEADER, USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"no-es-uuid\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"));

    verifyNoInteractions(createBooking);
  }

  @Test
  void rejectsIdempotencyKeyLongerThan100CharsWith400() throws Exception {
    String key = "x".repeat(101);

    mvc.perform(
            post("/api/bookings")
                .header(BookingController.USER_ID_HEADER, USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + EVENT_ID + "\",\"idempotencyKey\":\"" + key + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"))
        .andExpect(
            jsonPath("$.message")
                .value("La clave de idempotencia no puede superar los 100 caracteres"));

    verifyNoInteractions(createBooking);
  }

  @Test
  void unknownEventReturns404() throws Exception {
    when(createBooking.createBooking(any(UUID.class), any(UUID.class), any()))
        .thenThrow(new EventNotFoundException(EVENT_ID));

    mvc.perform(
            post("/api/bookings")
                .header(BookingController.USER_ID_HEADER, USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + EVENT_ID + "\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not_found"))
        .andExpect(jsonPath("$.message").value("No existe el evento " + EVENT_ID));
  }

  @Test
  void fullEventReturns409WithLiteralMessage() throws Exception {
    when(createBooking.createBooking(any(UUID.class), any(UUID.class), any()))
        .thenThrow(new EventFullException());

    mvc.perform(
            post("/api/bookings")
                .header(BookingController.USER_ID_HEADER, USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + EVENT_ID + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("conflict"))
        .andExpect(jsonPath("$.message").value("Evento sin plazas disponibles"));
  }

  @Test
  void repeatedKeyReturns409WithLiteralMessage() throws Exception {
    when(createBooking.createBooking(any(UUID.class), any(UUID.class), any()))
        .thenThrow(new IdempotencyConflictException());

    mvc.perform(
            post("/api/bookings")
                .header(BookingController.USER_ID_HEADER, USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + EVENT_ID + "\",\"idempotencyKey\":\"usada\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("conflict"))
        .andExpect(
            jsonPath("$.message")
                .value("Petición ya procesada: clave de idempotencia repetida"));
  }

  @Test
  void listsOnlyTheBookingsOfTheHeaderIdentity() throws Exception {
    Booking mine = Booking.pending(UUID.randomUUID(), EVENT_ID, USER_ID, null);
    when(listBookings.listByUser(USER_ID)).thenReturn(List.of(mine));

    mvc.perform(
            get("/api/bookings/me").header(BookingController.USER_ID_HEADER, USER_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(mine.id().toString()))
        .andExpect(jsonPath("$[0].userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$[0].status").value("PENDING"));

    verify(listBookings).listByUser(USER_ID);
  }

  @Test
  void userWithoutBookingsGetsEmptyArray() throws Exception {
    when(listBookings.listByUser(USER_ID)).thenReturn(List.of());

    mvc.perform(
            get("/api/bookings/me").header(BookingController.USER_ID_HEADER, USER_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void myBookingsWithoutIdentityHeaderReturn400() throws Exception {
    mvc.perform(get("/api/bookings/me"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"));

    verifyNoInteractions(listBookings);
  }

  @Test
  void myBookingsWithNonUuidHeaderReturn400() throws Exception {
    mvc.perform(get("/api/bookings/me").header(BookingController.USER_ID_HEADER, "123"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"));

    verifyNoInteractions(listBookings);
  }
}
