package com.campusseguro.service;

import com.campusseguro.config.Config;
import com.campusseguro.entity.Agente;
import com.campusseguro.integration.WazuhApiClient;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Sincroniza los agentes de Wazuh cada 60 segundos en un hilo aparte.
 *
 * @author rodrigonaranjo
 */
public class AgentSyncJob {

    private static final Logger LOGGER = Logger.getLogger(AgentSyncJob.class.getName());

    private static ScheduledExecutorService planificador;
    private static WazuhApiClient wazuh;

    // Resultado de la última sincronización; lo muestra /api/agentes
    public static volatile String ultimaSincronizacion = null;
    public static volatile boolean desactualizado = true;

    public static void iniciar() {
        String url = Config.get("WAZUH_API_URL");
        if (url == null || url.isBlank()) {
            LOGGER.warning("WAZUH_API_URL no está configurada: no se sincronizan agentes");
            return;
        }
        wazuh = new WazuhApiClient(url, Config.get("WAZUH_API_USER"), Config.get("WAZUH_API_PASSWORD"));

        planificador = Executors.newSingleThreadScheduledExecutor();
        planificador.scheduleWithFixedDelay(AgentSyncJob::sincronizar, 0, 60, TimeUnit.SECONDS);
    }

    public static void sincronizar() {
        try {
            List<Agente> agentes = wazuh.consultarAgentes();
            BuildingService.guardarAgentes(agentes);
            ultimaSincronizacion = LocalDateTime.now().toString();
            desactualizado = false;
            LOGGER.info("Agentes sincronizados: " + agentes.size());
        } catch (Exception e) {
            // Si Wazuh no responde se conservan los datos anteriores
            desactualizado = true;
            LOGGER.warning("No se pudo sincronizar con Wazuh: " + e.toString());
        }
    }

    public static void detener() {
        if (planificador != null) {
            planificador.shutdownNow();
        }
    }
}
