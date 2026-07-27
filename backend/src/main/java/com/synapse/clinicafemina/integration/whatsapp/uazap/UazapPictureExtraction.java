package com.synapse.clinicafemina.integration.whatsapp.uazap;

public record UazapPictureExtraction(
        UazapPictureEnrichmentOutcome outcome,
        UazapPictureSource source
) {
    public static UazapPictureExtraction semFonte(UazapPictureEnrichmentOutcome outcome) {
        return new UazapPictureExtraction(outcome, null);
    }
}
