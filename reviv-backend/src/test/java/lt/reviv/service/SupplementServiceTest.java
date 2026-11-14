package lt.reviv.service;

import lt.reviv.supplements.domain.Supplement;
import lt.reviv.supplements.domain.SupplementForm;
import lt.reviv.supplements.domain.SupplementInteraction;
import lt.reviv.supplements.domain.SupplementStatus;
import lt.reviv.supplements.dto.DistributorRequest;
import lt.reviv.supplements.dto.InteractionRequest;
import lt.reviv.supplements.dto.SupplementRequest;
import lt.reviv.supplements.dto.SupplementResponse;
import lt.reviv.supplements.exception.SupplementConflictException;
import lt.reviv.supplements.exception.SupplementNotFoundException;
import lt.reviv.supplements.exception.SupplementValidationException;
import lt.reviv.supplements.repository.SupplementInteractionRepository;
import lt.reviv.supplements.repository.SupplementRepository;
import lt.reviv.supplements.service.SupplementService;
import lt.reviv.supplements.service.SupplementUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-10: papildų katalogas (DP7 - Papildų valdymo komponentas). */
@ExtendWith(MockitoExtension.class)
class SupplementServiceTest {

    @Mock
    private SupplementRepository supplementRepository;

    @Mock
    private SupplementInteractionRepository interactionRepository;

    @Mock
    private SupplementUsage supplementUsage;

    @InjectMocks
    private SupplementService service;

    private static Supplement supplement(long id, String name) {
        Supplement supplement = new Supplement(name, "Veiklioji medžiaga", SupplementForm.TABLET,
                new BigDecimal("10"), "mg", new BigDecimal("50"), null);
        ReflectionTestUtils.setField(supplement, "id", id);
        return supplement;
    }

    private static SupplementRequest request(String name, String dose, String maxDose) {
        return new SupplementRequest(name, "Veiklioji medžiaga", SupplementForm.CAPSULE,
                new BigDecimal(dose), "mg", new BigDecimal(maxDose), null, null);
    }

    @Test
    void TA57_createSavesTrimmedValuesAndDistributors() {
        when(supplementRepository.existsByNameIgnoreCase("Vitaminas D3")).thenReturn(false);
        when(supplementRepository.save(any(Supplement.class))).thenAnswer(i -> i.getArgument(0));
        SupplementRequest request = new SupplementRequest("  Vitaminas D3 ", " Cholekalciferolis ",
                SupplementForm.LIQUID, new BigDecimal("25"), " mcg ", new BigDecimal("100"), "   ",
                List.of(new DistributorRequest(" Demo vaistinė ", " https://example.com/d3 ")));

        SupplementResponse response = service.create(request);

        ArgumentCaptor<Supplement> captor = ArgumentCaptor.forClass(Supplement.class);
        verify(supplementRepository).save(captor.capture());
        Supplement saved = captor.getValue();
        assertEquals("Vitaminas D3", saved.getName());
        assertEquals("Cholekalciferolis", saved.getActiveSubstance());
        assertEquals("mcg", saved.getDoseUnit());
        assertNull(saved.getMissedDoseRule(), "Tuščia taisyklė saugoma kaip null");
        assertEquals(1, saved.getDistributors().size());
        assertEquals("Demo vaistinė", saved.getDistributors().get(0).getName());
        assertEquals("https://example.com/d3", saved.getDistributors().get(0).getUrl());
        assertEquals(SupplementStatus.ACTIVE, response.status());
    }

    @Test
    void TA42_createRejectsDuplicateName() {
        when(supplementRepository.existsByNameIgnoreCase("Kalcis")).thenReturn(true);

        SupplementConflictException error = assertThrows(SupplementConflictException.class,
                () -> service.create(request("Kalcis", "500", "2500")));

        assertEquals("name", error.getField());
        verify(supplementRepository, never()).save(any(Supplement.class));
    }

    @Test
    void TA42_createRejectsMaxDoseBelowSingleDose() {
        SupplementValidationException error = assertThrows(SupplementValidationException.class,
                () -> service.create(request("Naujas papildas", "100", "50")));

        assertEquals("maxDailyDose", error.getField());
        verify(supplementRepository, never()).save(any(Supplement.class));
    }

    @Test
    void TA57_updateKeepsOwnNameAndChangesFields() {
        Supplement existing = supplement(7L, "Kalcis");
        when(supplementRepository.findWithDistributorsById(7L)).thenReturn(Optional.of(existing));
        when(supplementRepository.existsByNameIgnoreCaseAndIdNot("Kalcis", 7L)).thenReturn(false);
        when(supplementRepository.save(any(Supplement.class))).thenAnswer(i -> i.getArgument(0));

        SupplementResponse response = service.update(7L, request("Kalcis", "600", "2000"));

        assertEquals(0, new BigDecimal("600").compareTo(existing.getDoseAmount()));
        assertEquals(0, new BigDecimal("2000").compareTo(existing.getMaxDailyDose()));
        assertEquals("Kalcis", response.name());
    }

    @Test
    void TA42_updateRejectsNameUsedByAnotherSupplement() {
        Supplement existing = supplement(7L, "Kalcis");
        when(supplementRepository.findWithDistributorsById(7L)).thenReturn(Optional.of(existing));
        when(supplementRepository.existsByNameIgnoreCaseAndIdNot("Cinkas", 7L)).thenReturn(true);

        assertThrows(SupplementConflictException.class,
                () -> service.update(7L, request("Cinkas", "10", "50")));

        verify(supplementRepository, never()).save(any(Supplement.class));
    }

    @Test
    void TA57_updateUnknownSupplementThrowsNotFound() {
        when(supplementRepository.findWithDistributorsById(99L)).thenReturn(Optional.empty());

        assertThrows(SupplementNotFoundException.class,
                () -> service.update(99L, request("Bet koks", "10", "50")));
    }

    @Test
    void TA43_deleteRemovesSupplementNotUsedInPlans() {
        Supplement existing = supplement(3L, "Varis");
        when(supplementRepository.findWithDistributorsById(3L)).thenReturn(Optional.of(existing));
        when(supplementUsage.isUsedInPlans(3L)).thenReturn(false);

        Optional<SupplementResponse> result = service.delete(3L);

        assertTrue(result.isEmpty());
        verify(supplementRepository).delete(existing);
    }

    @Test
    void TA43_deleteMarksSupplementUsedInPlansAsInactive() {
        Supplement existing = supplement(3L, "Varis");
        when(supplementRepository.findWithDistributorsById(3L)).thenReturn(Optional.of(existing));
        when(supplementUsage.isUsedInPlans(3L)).thenReturn(true);
        when(supplementRepository.save(any(Supplement.class))).thenAnswer(i -> i.getArgument(0));

        Optional<SupplementResponse> result = service.delete(3L);

        assertTrue(result.isPresent());
        assertEquals(SupplementStatus.INACTIVE, result.get().status());
        assertEquals(SupplementStatus.INACTIVE, existing.getStatus());
        verify(supplementRepository, never()).delete(any(Supplement.class));
    }

    @Test
    void TA54_addInteractionStoresSinglePairForBothSupplements() {
        when(supplementRepository.findWithDistributorsById(5L)).thenReturn(Optional.of(supplement(5L, "Kalcis")));
        when(supplementRepository.existsById(2L)).thenReturn(true);
        when(interactionRepository.findBySupplementAIdAndSupplementBId(2L, 5L)).thenReturn(Optional.empty());
        when(interactionRepository.save(any(SupplementInteraction.class))).thenAnswer(i -> i.getArgument(0));

        service.addInteraction(5L, new InteractionRequest(2L, " Mažina pasisavimą ", " Vartoti skirtingu paros metu "));

        ArgumentCaptor<SupplementInteraction> captor = ArgumentCaptor.forClass(SupplementInteraction.class);
        verify(interactionRepository).save(captor.capture());
        SupplementInteraction saved = captor.getValue();
        assertEquals(2L, saved.getSupplementAId().longValue(), "Mažesnis ID visada A pusėje");
        assertEquals(5L, saved.getSupplementBId().longValue());
        assertEquals("Mažina pasisavimą", saved.getType());
        assertEquals("Vartoti skirtingu paros metu", saved.getRecommendation());
    }

    @Test
    void TA54_addInteractionRejectsSelfInteraction() {
        when(supplementRepository.findWithDistributorsById(4L)).thenReturn(Optional.of(supplement(4L, "Cinkas")));

        SupplementValidationException error = assertThrows(SupplementValidationException.class,
                () -> service.addInteraction(4L, new InteractionRequest(4L, "Tipas", "Rekomendacija")));

        assertEquals("otherSupplementId", error.getField());
        verify(interactionRepository, never()).save(any(SupplementInteraction.class));
    }

    @Test
    void TA54_addInteractionRejectsUnknownOtherSupplement() {
        when(supplementRepository.findWithDistributorsById(4L)).thenReturn(Optional.of(supplement(4L, "Cinkas")));
        when(supplementRepository.existsById(9L)).thenReturn(false);

        assertThrows(SupplementValidationException.class,
                () -> service.addInteraction(4L, new InteractionRequest(9L, "Tipas", "Rekomendacija")));

        verify(interactionRepository, never()).save(any(SupplementInteraction.class));
    }

    @Test
    void TA54_addInteractionRejectsExistingPair() {
        when(supplementRepository.findWithDistributorsById(5L)).thenReturn(Optional.of(supplement(5L, "Kalcis")));
        when(supplementRepository.existsById(2L)).thenReturn(true);
        when(interactionRepository.findBySupplementAIdAndSupplementBId(2L, 5L))
                .thenReturn(Optional.of(new SupplementInteraction(2L, 5L, "Tipas", "Rekomendacija")));

        assertThrows(SupplementConflictException.class,
                () -> service.addInteraction(5L, new InteractionRequest(2L, "Tipas", "Rekomendacija")));

        verify(interactionRepository, never()).save(any(SupplementInteraction.class));
    }

    @Test
    void TA54_removeInteractionDeletesPairOfThisSupplement() {
        SupplementInteraction interaction = new SupplementInteraction(2L, 5L, "Tipas", "Rekomendacija");
        ReflectionTestUtils.setField(interaction, "id", 11L);
        when(supplementRepository.findWithDistributorsById(5L)).thenReturn(Optional.of(supplement(5L, "Kalcis")));
        when(interactionRepository.findById(11L)).thenReturn(Optional.of(interaction));

        service.removeInteraction(5L, 11L);

        verify(interactionRepository).delete(interaction);
    }

    @Test
    void TA54_removeInteractionOfOtherSupplementsIsNotFound() {
        SupplementInteraction interaction = new SupplementInteraction(1L, 2L, "Tipas", "Rekomendacija");
        ReflectionTestUtils.setField(interaction, "id", 11L);
        when(supplementRepository.findWithDistributorsById(5L)).thenReturn(Optional.of(supplement(5L, "Kalcis")));
        when(interactionRepository.findById(11L)).thenReturn(Optional.of(interaction));

        assertThrows(SupplementNotFoundException.class, () -> service.removeInteraction(5L, 11L));

        verify(interactionRepository, never()).delete(any(SupplementInteraction.class));
    }

    @Test
    void TA57_listShowsInteractionOnBothSupplements() {
        Supplement zinc = supplement(1L, "Cinkas");
        Supplement copper = supplement(2L, "Varis");
        when(supplementRepository.findAllByOrderByNameAsc()).thenReturn(List.of(zinc, copper));
        when(interactionRepository.findAll())
                .thenReturn(List.of(new SupplementInteraction(1L, 2L, "Konkuruoja dėl pasisavimo", "Vartoti skirtingu metu")));

        List<SupplementResponse> result = service.list();

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).interactions().size());
        assertEquals("Varis", result.get(0).interactions().get(0).otherSupplementName());
        assertEquals(1, result.get(1).interactions().size());
        assertEquals("Cinkas", result.get(1).interactions().get(0).otherSupplementName());
    }
}