package com.eventpay.booking.infrastructure.in.web;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventpay.booking.domain.exception.DomainValidationException;
import com.eventpay.booking.domain.model.Event;
import com.eventpay.booking.domain.port.in.CreateEventUseCase;
import com.eventpay.booking.domain.port.in.ListEventsUseCase;
import com.eventpay.booking.infrastructure.in.web.mapper.EventMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Contrato HTTP de eventos de §5 de la spec 002, sin base de datos (RF1–RF3). */
@WebMvcTest(EventController.class)
@Import(EventMapper.class)
class EventControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean private CreateEventUseCase createEvent;

  @MockBean private ListEventsUseCase listEvents;

  @Test
  void listsEventsWithoutIdentityHeader() throws Exception {
    when(listEvents.list()).thenReturn(List.of());

    mvc.perform(get("/api/events"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void listsEventsWithTheirSoldCount() throws Exception {
    UUID id = UUID.randomUUID();
    when(listEvents.list()).thenReturn(List.of(new Event(id, "Demo", 3, 2)));

    mvc.perform(get("/api/events"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id.toString()))
        .andExpect(jsonPath("$[0].name").value("Demo"))
        .andExpect(jsonPath("$[0].capacity").value(3))
        .andExpect(jsonPath("$[0].sold").value(2));
  }

  @Test
  void createsEventWithoutIdentityHeaderAndReturns201() throws Exception {
    UUID id = UUID.randomUUID();
    when(createEvent.create("Demo", 2)).thenReturn(new Event(id, "Demo", 2, 0));

    mvc.perform(
            post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Demo\",\"capacity\":2}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.name").value("Demo"))
        .andExpect(jsonPath("$.capacity").value(2))
        .andExpect(jsonPath("$.sold").value(0));
  }

  @Test
  void rejectsBlankNameWith400() throws Exception {
    mvc.perform(
            post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \",\"capacity\":2}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"))
        .andExpect(jsonPath("$.message").value("El nombre del evento es obligatorio"));

    verifyNoInteractions(createEvent);
  }

  @Test
  void rejectsMissingNameWith400() throws Exception {
    mvc.perform(
            post("/api/events").contentType(MediaType.APPLICATION_JSON).content("{\"capacity\":2}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"));

    verifyNoInteractions(createEvent);
  }

  @Test
  void rejectsZeroCapacityWith400() throws Exception {
    mvc.perform(
            post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Demo\",\"capacity\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"))
        .andExpect(jsonPath("$.message").value("La capacidad del evento debe ser mayor que 0"));

    verifyNoInteractions(createEvent);
  }

  @Test
  void rejectsMissingCapacityWith400() throws Exception {
    mvc.perform(
            post("/api/events").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Demo\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"))
        .andExpect(jsonPath("$.message").value("La capacidad del evento es obligatoria"));

    verifyNoInteractions(createEvent);
  }

  @Test
  void rejectsMalformedBodyWith400() throws Exception {
    mvc.perform(
            post("/api/events").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"))
        .andExpect(jsonPath("$.message").value("El cuerpo de la petición no es válido"));

    verifyNoInteractions(createEvent);
  }

  @Test
  void mapsDomainValidationFromTheServiceTo400() throws Exception {
    when(createEvent.create(anyString(), anyInt()))
        .thenThrow(new DomainValidationException("El nombre del evento no puede estar vacío"));

    mvc.perform(
            post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Demo\",\"capacity\":2}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"))
        .andExpect(jsonPath("$.message").value("El nombre del evento no puede estar vacío"));
  }
}
