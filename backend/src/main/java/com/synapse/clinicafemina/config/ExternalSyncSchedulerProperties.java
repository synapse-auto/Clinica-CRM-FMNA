package com.synapse.clinicafemina.config;

import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.external-sync.scheduler")
public class ExternalSyncSchedulerProperties {

    private boolean enabled;
    private String cron;
    private String timezone = "America/Sao_Paulo";
    private int startDaysBack = 90;
    private int endDaysForward = 90;

    @PostConstruct
    public void validate() {
        if (!enabled) {
            return;
        }
        if (cron == null || cron.isBlank() || !CronExpression.isValidExpression(cron)) {
            throw new IllegalStateException(
                    "EXTERNAL_SYNC_CRON deve ser um cron valido quando a sincronizacao automatica estiver habilitada");
        }
        try {
            ZoneId.of(timezone);
        } catch (Exception error) {
            throw new IllegalStateException("EXTERNAL_SYNC_TIMEZONE invalida", error);
        }
        if (startDaysBack < 0 || endDaysForward < 0) {
            throw new IllegalStateException(
                    "EXTERNAL_SYNC_START_DAYS_BACK e EXTERNAL_SYNC_END_DAYS_FORWARD devem ser nao negativos");
        }
        if ((long) startDaysBack + endDaysForward > 366) {
            throw new IllegalStateException(
                    "A janela da sincronizacao automatica nao pode exceder 366 dias");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public int getStartDaysBack() {
        return startDaysBack;
    }

    public void setStartDaysBack(int startDaysBack) {
        this.startDaysBack = startDaysBack;
    }

    public int getEndDaysForward() {
        return endDaysForward;
    }

    public void setEndDaysForward(int endDaysForward) {
        this.endDaysForward = endDaysForward;
    }

    public ZoneId zoneId() {
        return ZoneId.of(timezone);
    }
}
