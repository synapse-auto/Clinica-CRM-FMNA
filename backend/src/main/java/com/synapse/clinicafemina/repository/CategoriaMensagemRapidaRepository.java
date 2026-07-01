package com.synapse.clinicafemina.repository;

import com.synapse.clinicafemina.domain.CategoriaMensagemRapida;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaMensagemRapidaRepository extends JpaRepository<CategoriaMensagemRapida, Short> {

    List<CategoriaMensagemRapida> findAllByOrderByRotuloAsc();
}
