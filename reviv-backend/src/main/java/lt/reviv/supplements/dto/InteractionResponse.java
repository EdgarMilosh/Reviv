package lt.reviv.supplements.dto;

public record InteractionResponse(
        Long id,
        Long otherSupplementId,
        String otherSupplementName,
        String type,
        String recommendation
) {
}