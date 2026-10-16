package com.campusseguro.service;

import com.campusseguro.config.Config;
import com.campusseguro.integration.OpenSearchClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AlertSyncJob {

    private static final Logger LOGGER =
            Logger.getLogger(AlertSyncJob.class.getName());

    private static ScheduledExecutorService planificador;
    private static OpenSearchClient openSearch;

    public static volatile String ultimaSincronizacion = null;
    public static volatile boolean desactualizado = true;

    /**
     * Inicia la sincronización automática de alertas.
     */
    public static synchronized void iniciar() {

        if (planificador != null && !planificador.isShutdown()) {
            return;
        }

        String url = Config.get("OPENSEARCH_URL");
        String usuario = Config.get("WAZUH_API_USER");
        String password = Config.get("WAZUH_API_PASSWORD");

        if (url == null || url.isBlank()
                || usuario == null || usuario.isBlank()
                || password == null || password.isBlank()) {

            LOGGER.warning(
                    "Falta configurar OPENSEARCH_URL, OPENSEARCH_USER u OPENSEARCH_PASSWORD. "
                    + "No se sincronizarán las alertas.");
            return;
        }

        openSearch = new OpenSearchClient(url, usuario, password);

        planificador = Executors.newSingleThreadScheduledExecutor();

        // Primera ejecución inmediata; después, cada 60 segundos.
        planificador.scheduleWithFixedDelay(
                AlertSyncJob::sincronizar,
                0,
                60,
                TimeUnit.SECONDS
        );

        LOGGER.info("Sincronización automática de alertas iniciada.");
    }

    /**
     * Consulta OpenSearch y guarda las alertas recuperadas.
     */
    public static void sincronizar() {

        if (openSearch == null) {
            LOGGER.warning("El cliente de OpenSearch no está inicializado.");
            desactualizado = true;
            return;
        }

        try {
            JsonArray alertas = openSearch.consultarAlertasRecientes();

            int nuevas = 0;
            int duplicadas = 0;
            int errores = 0;

            for (int i = 0; i < alertas.size(); i++) {

                JsonObject alerta = alertas.get(i).getAsJsonObject();

                try {
                    boolean insertada = AlertService.guardarAlerta(alerta, "REAL");

                    if (insertada) {
                        nuevas++;
                    } else {
                        duplicadas++;
                    }

                } catch (Exception e) {
                    errores++;

                    LOGGER.log(
                            Level.WARNING,
                            "No se pudo guardar una alerta de OpenSearch.",
                            e
                    );
                }
            }

            ultimaSincronizacion = java.time.LocalDateTime.now().toString();
            desactualizado = errores > 0;

            LOGGER.info(
                    "Sincronización de alertas terminada. "
                    + "Consultadas: " + alertas.size()
                    + ", nuevas: " + nuevas
                    + ", duplicadas: " + duplicadas
                    + ", errores: " + errores
            );

        } catch (Exception e) {

            desactualizado = true;

            LOGGER.log(
                    Level.WARNING,
                    "No se pudieron consultar las alertas de OpenSearch.",
                    e
            );
        }
    }

    /**
     * Detiene el planificador al apagar la aplicación.
     */
    public static synchronized void detener() {

        if (planificador != null) {
            planificador.shutdownNow();
            planificador = null;
        }

        openSearch = null;

        LOGGER.info("Sincronización automática de alertas detenida.");
    }
}