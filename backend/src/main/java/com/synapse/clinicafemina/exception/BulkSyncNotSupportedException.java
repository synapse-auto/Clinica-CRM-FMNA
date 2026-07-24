package com.synapse.clinicafemina.exception;

public class BulkSyncNotSupportedException extends RuntimeException {

    public static final String CODE = "PROVIDER_SEM_SINCRONIZACAO_EM_MASSA";
    public static final String MESSAGE =
            "A integração Darwin utiliza consultas sob demanda e não oferece sincronização em massa.";

    public BulkSyncNotSupportedException() {
        super(MESSAGE);
    }
}
