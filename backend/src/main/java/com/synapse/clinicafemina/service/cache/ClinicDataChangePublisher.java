package com.synapse.clinicafemina.service.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClinicDataChangePublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publish(Long clinicId) {
        eventPublisher.publishEvent(new ClinicDataChangedEvent(clinicId));
    }
}
