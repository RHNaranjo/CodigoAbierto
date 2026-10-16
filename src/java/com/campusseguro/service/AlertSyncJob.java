package com.campusseguro.service;

import com.campusseguro.config.Config;
import com.campusseguro.integration.OpenSearchClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AlertSyncJob {

    private static final Logger LOGGER =
            Logger.getLogger(AlertSyncJob.class.getName());

    private static ScheduledExecutorService planificador;
    private static OpenSearchClient openSearch;

    public static volatile String ultimaSincronizacion = null;
    public static volatile boolean desactualizado = true;

    // Grupo de dos hilos para procesar alertas.
    private static ExecutorService procesadores;
    private static final int NUM_HILOS = 2;

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
                    "Falta configurar OPENSEARCH_URL, "
                    + "WAZUH_API_USER o WAZUH_API_PASSWORD. "
                    + "No se sincronizarán las alertas."
            );

            return;
        }

        openSearch = new OpenSearchClient(url, usuario, password);

        // Crear los dos hilos de procesamiento.
        if (procesadores == null || procesadores.isShutdown()) {
            procesadores = Executors.newFixedThreadPool(NUM_HILOS);
        }

        // Planificador de sincronizaciones.
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
     * Consulta OpenSearch y distribuye las alertas entre dos hilos.
     */
    public static void sincronizar() {

        if (openSearch == null) {
            LOGGER.warning(
                    "El cliente de OpenSearch no está inicializado."
            );

            desactualizado = true;
            return;
        }

        try {

            // 1. Consultar las alertas de OpenSearch.
            JsonArray alertas = openSearch.consultarAlertasRecientes();

            int total = alertas.size();

            // Contadores compartidos entre ambos hilos.
            AtomicInteger nuevas = new AtomicInteger(0);
            AtomicInteger duplicadas = new AtomicInteger(0);
            AtomicInteger errores = new AtomicInteger(0);

            // 2. Dividir las alertas en dos grupos.
            int mitad = (total + 1) / 2;

            JsonArray alertasHilo1 = new JsonArray();
            JsonArray alertasHilo2 = new JsonArray();

            for (int i = 0; i < total; i++) {

                JsonElement alerta = alertas.get(i);

                if (i < mitad) {
                    alertasHilo1.add(alerta);
                } else {
                    alertasHilo2.add(alerta);
                }
            }

            LOGGER.info(
                    "Distribuyendo " + total + " alertas. "
                    + "Hilo 1: " + alertasHilo1.size()
                    + ", Hilo 2: " + alertasHilo2.size()
            );

            // 3. Crear las tareas de los dos hilos.
            Future<?> tarea1 = procesadores.submit(
                    () -> procesarLote(
                            1,
                            alertasHilo1,
                            nuevas,
                            duplicadas,
                            errores
                    )
            );

            Future<?> tarea2 = procesadores.submit(
                    () -> procesarLote(
                            2,
                            alertasHilo2,
                            nuevas,
                            duplicadas,
                            errores
                    )
            );

            // 4. Esperar a que ambos hilos terminen.
            try {

                tarea1.get();
                tarea2.get();

            } catch (InterruptedException e) {

                tarea1.cancel(true);
                tarea2.cancel(true);

                Thread.currentThread().interrupt();
                desactualizado = true;

                LOGGER.log(
                        Level.WARNING,
                        "La sincronización fue interrumpida.",
                        e
                );

                return;

            } catch (ExecutionException e) {

                desactualizado = true;

                LOGGER.log(
                        Level.SEVERE,
                        "Error ejecutando un hilo de procesamiento.",
                        e
                );

                return;
            }

            // 5. Actualizar el estado de la sincronización.
            ultimaSincronizacion =
                    java.time.LocalDateTime.now().toString();

            desactualizado = errores.get() > 0;

            LOGGER.info(
                    "Sincronización de alertas terminada. "
                    + "Consultadas: " + total
                    + ", nuevas: " + nuevas.get()
                    + ", duplicadas: " + duplicadas.get()
                    + ", errores: " + errores.get()
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
     * Procesa un grupo de alertas.
     * Este método es ejecutado por cada hilo.
     */
    private static void procesarLote(
            int indice,
            JsonArray alertas,
            AtomicInteger nuevas,
            AtomicInteger duplicadas,
            AtomicInteger errores) {

        String nombreHilo = Thread.currentThread().getName();

        LOGGER.info(
                nombreHilo + ": Iniciando procesamiento "
                + indice + ". Alertas asignadas: " + alertas.size()
        );

        for (int i = 0; i < alertas.size(); i++) {

            // No continuar si el hilo fue interrumpido.
            if (Thread.currentThread().isInterrupted()) {
                LOGGER.info(nombreHilo + ": Procesamiento interrumpido.");
                return;
            }

            JsonObject alerta = alertas.get(i).getAsJsonObject();

            try {

                boolean insertada =
                        AlertService.guardarAlerta(alerta, "REAL");

                if (insertada) {
                    nuevas.incrementAndGet();
                } else {
                    duplicadas.incrementAndGet();
                }

            } catch (Exception e) {

                errores.incrementAndGet();

                LOGGER.log(
                        Level.WARNING,
                        nombreHilo
                        + ": No se pudo guardar una alerta de OpenSearch.",
                        e
                );
            }
        }

        LOGGER.info(
                nombreHilo + ": Finalizó el procesamiento "
                + indice
        );
    }

    /**
     * Detiene el planificador y los hilos al apagar la aplicación.
     */
    public static synchronized void detener() {

        if (planificador != null) {
            planificador.shutdownNow();
            planificador = null;
        }

        if (procesadores != null) {
            procesadores.shutdownNow();
            procesadores = null;
        }

        openSearch = null;

        LOGGER.info(
                "Sincronización automática de alertas detenida."
        );
    }
}