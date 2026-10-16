package com.campusseguro.service;

import com.campusseguro.config.Config;
import com.campusseguro.integration.OpenSearchClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Consulta al indexador de Wazuh las alertas nuevas cada 60 segundos, las
 * guarda como PENDIENTES y las manda a AlertProcessingService.
 *
 * @author rodrigonaranjo
 */
public class AlertSyncJob {

    private static final Logger LOGGER = Logger.getLogger(AlertSyncJob.class.getName());

    private static ScheduledExecutorService planificador;
    private static OpenSearchClient openSearch;

    // Resultado de la última consulta al indexador
    public static volatile String ultimaSincronizacion = null;
    public static volatile boolean desactualizado = true;

    public static void iniciar() {
        String url = Config.get("OPENSEARCH_URL");
        if (url == null || url.isBlank()) {
            LOGGER.warning("OPENSEARCH_URL no está configurada: no se sincronizan alertas");
            return;
        }
        // Si el indexador no tiene credenciales propias se usan las de la API
        openSearch = new OpenSearchClient(url,
                valor("OPENSEARCH_USER", "WAZUH_API_USER"),
                valor("OPENSEARCH_PASSWORD", "WAZUH_API_PASSWORD"));

        planificador = Executors.newSingleThreadScheduledExecutor();
        planificador.scheduleWithFixedDelay(AlertSyncJob::sincronizar, 0, 60, TimeUnit.SECONDS);
    }

    private static String valor(String nombre, String alterno) {
        String v = Config.get(nombre);
        return (v == null || v.isBlank()) ? Config.get(alterno) : v;
    }

    public static void sincronizar() {
        try {
            JsonArray alertas = openSearch.consultarAlertasRecientes();
            int nuevas = 0, duplicadas = 0, errores = 0;

            for (JsonElement e : alertas) {
                try {
                    Long id = AlertService.guardarAlerta(e.getAsJsonObject(), "REAL");
                    if (id == null) {
                        duplicadas++; // ya estaba: un reenvío no cuenta dos veces
                    } else {
                        nuevas++;
                        AlertProcessingService.encolar(id);
                    }
                } catch (Exception ex) {
                    errores++;
                    LOGGER.warning("No se pudo guardar una alerta: " + ex.getMessage());
                }
            }

            ultimaSincronizacion = LocalDateTime.now().toString();
            desactualizado = false;
            LOGGER.info("Alertas consultadas: " + alertas.size() + ", nuevas: " + nuevas
                    + ", duplicadas: " + duplicadas + ", errores: " + errores);
        } catch (Exception e) {
            // Si el indexador no responde se conservan las alertas anteriores
            desactualizado = true;
            LOGGER.warning("No se pudo consultar el indexador: " + e.toString());
        }
    }

    public static void detener() {
        if (planificador != null) {
            planificador.shutdownNow();
        }
    }
}
