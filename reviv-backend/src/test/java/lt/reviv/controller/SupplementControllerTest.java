package lt.reviv.controller;

import lt.reviv.supplements.controller.SupplementController;
import lt.reviv.supplements.domain.SupplementForm;
import lt.reviv.supplements.domain.SupplementStatus;
import lt.reviv.supplements.dto.InteractionRequest;
import lt.reviv.supplements.dto.SupplementRequest;
import lt.reviv.supplements.dto.SupplementResponse;
import lt.reviv.supplements.service.SupplementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-10: DP3 - Papildų kontrolerio komponentas. */
@ExtendWith(MockitoExtension.class)
class SupplementControllerTest {

    @Mock
    private SupplementService service;

    @InjectMocks
    private SupplementController controller;

    private static SupplementResponse response(SupplementStatus status) {
        return new SupplementResponse(1L, "Kalcis", "Kalcio citratas", SupplementForm.TABLET,
                new BigDecimal("500"), "mg", new BigDecimal("2500"), null, status, List.of(), List.of());
    }

    private static SupplementRequest request() {
        return new SupplementRequest("Kalcis", "Kalcio citratas", SupplementForm.TABLET,
                new BigDecimal("500"), "mg", new BigDecimal("2500"), null, null);
    }

    @Test
    void TA57_createDelegatesToService() {
        SupplementRequest request = request();
        SupplementResponse response = response(SupplementStatus.ACTIVE);
        when(service.create(request)).thenReturn(response);

        assertSame(response, controller.create(request));
        verify(service).create(request);
    }

    @Test
    void TA43_deleteReturnsNoContentWhenSupplementWasRemoved() {
        when(service.delete(3L)).thenReturn(Optional.empty());

        ResponseEntity<SupplementResponse> result = controller.delete(3L);

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        assertNull(result.getBody());
    }

    @Test
    void TA43_deleteReturnsInactiveSupplementWhenItIsInUse() {
        SupplementResponse inactive = response(SupplementStatus.INACTIVE);
        when(service.delete(3L)).thenReturn(Optional.of(inactive));

        ResponseEntity<SupplementResponse> result = controller.delete(3L);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(inactive, result.getBody());
    }

    @Test
    void TA54_addInteractionDelegatesToService() {
        InteractionRequest request = new InteractionRequest(2L, "Mažina pasisavimą", "Vartoti skirtingu paros metu");
        SupplementResponse response = response(SupplementStatus.ACTIVE);
        when(service.addInteraction(5L, request)).thenReturn(response);

        assertSame(response, controller.addInteraction(5L, request));
    }
}