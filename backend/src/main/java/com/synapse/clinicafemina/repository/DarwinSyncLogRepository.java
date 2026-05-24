package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.DarwinSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DarwinSyncLogRepository extends JpaRepository<DarwinSyncLog, Long> {

    @Query("SELECT d FROM DarwinSyncLog d WHERE d.status = 'SUCESSO' ORDER BY d.iniciadoEm DESC LIMIT 1")
    Optional<DarwinSyncLog> findUltimoSucesso();
}
