package com.synapse.clinicafemina.integration.whatsapp.uazap;

public class UazapProfilePhotoDownloadException extends RuntimeException {

    private final String motivo;
    private final boolean temporaria;
    private final boolean semFoto;

    private UazapProfilePhotoDownloadException(String motivo, boolean temporaria, boolean semFoto) {
        super(motivo);
        this.motivo = motivo;
        this.temporaria = temporaria;
        this.semFoto = semFoto;
    }

    public static UazapProfilePhotoDownloadException temporaria(String motivo) {
        return new UazapProfilePhotoDownloadException(motivo, true, false);
    }

    public static UazapProfilePhotoDownloadException permanente(String motivo) {
        return new UazapProfilePhotoDownloadException(motivo, false, false);
    }

    public static UazapProfilePhotoDownloadException semFoto(String motivo) {
        return new UazapProfilePhotoDownloadException(motivo, false, true);
    }

    public String motivo() {
        return motivo;
    }

    public boolean temporaria() {
        return temporaria;
    }

    public boolean semFoto() {
        return semFoto;
    }
}
