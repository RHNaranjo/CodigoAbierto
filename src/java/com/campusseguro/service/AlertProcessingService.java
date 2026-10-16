package com.campusseguro.service;

import com.campusseguro.config.BaseDatos;
import com.campusseguro.entity.EnriquecimientoLdap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Procesa en segundo plano las alertas PENDIENTES: edificio del agente,
 * consulta LDAP y estado PROCESADA. Dos hilos y una cola de 100 tareas
 * (sección 10 de CampusSeguro.md). Si la cola está llena la alerta sigue
 * PENDIENTE y la recoge la tarea de recuperación, igual que tras un reinicio.
 */
public class AlertProcessingService {

    private static final Logger LOGGER = Logger.getLogger(AlertProcessingService.class.getName());

    private static ThreadPoolExecutor hilos;
    private static ScheduledExecutorService planificador;

    public static void iniciar() {
        hilos = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100));
        // Recupera las pendientes al arrancar y luego cada 60 s
        planificador = Executors.newSingleThreadScheduledExecutor();
        planificador.scheduleWithFixedDelay(AlertProcessingService::recuperarPendientes, 0, 60, TimeUnit.SECONDS);
    }

    // Manda una alerta a la cola. Devuelve false si está llena.
    public static boolean encolar(long idAlerta) {
        if (hilos == null) {
            return false;
        }
        try {
            hilos.execute(() -> procesar(idAlerta));
            return true;
        } catch (RejectedExecutionException e) {
            LOGGER.warning("Cola llena: la alerta " + idAlerta + " queda PENDIENTE");
            return false;
        }
    }

    public static void recuperarPendientes() {
        try {
            for (long id : pendientes()) {
                if (!encolar(id)) {
                    break;
                }
            }
        } catch (SQLException e) {
            LOGGER.warning("No se pudieron leer las alertas pendientes: " + e.getMessage());
        }
    }

    // Las más antiguas primero; a lo más 100, el tamaño de la cola
    static List<Long> pendientes() throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection con = BaseDatos.conectar();
             ResultSet rs = con.createStatement().executeQuery(
                     "SELECT id_interno FROM alerta WHERE estado_procesamiento = 'PENDIENTE' ORDER BY id_interno LIMIT 100")) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    // Una alerta. Si falla la base queda PENDIENTE y se reintenta en la recuperación.
    static void procesar(long idAlerta) {
        try (Connection con = BaseDatos.conectar()) {
            String agenteId;
            String usuario;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT agente_id, usuario_evento FROM alerta WHERE id_interno = ? AND estado_procesamiento = 'PENDIENTE'")) {
                ps.setLong(1, idAlerta);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return; // ya la procesó otro hilo
                    }
                    agenteId = rs.getString("agente_id");
                    usuario = rs.getString("usuario_evento");
                }
            }

            // El edificio se copia a la alerta: si después reasignan el agente,
            // el histórico no cambia. Sin agente o sin asignación queda null (Sin edificio).
            Integer edificioId = BuildingService.edificioDeAgente(agenteId);

            // Si LDAP falla se registra ERROR_LDAP y la alerta se procesa igual
            EnriquecimientoLdap ldap = LdapUserService.buscar(usuario);
            try (PreparedStatement ps = con.prepareStatement(
                    "MERGE INTO enriquecimiento_ldap (alerta_id, resultado, uid, nombre, correo, departamento, fecha_consulta)"
                    + " KEY (alerta_id) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setLong(1, idAlerta);
                ps.setString(2, ldap.getResultado());
                ps.setString(3, ldap.getUid());
                ps.setString(4, ldap.getNombre());
                ps.setString(5, ldap.getCorreo());
                ps.setString(6, ldap.getDepartamento());
                ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
                ps.executeUpdate();
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE alerta SET edificio_id = ?, estado_procesamiento = 'PROCESADA' WHERE id_interno = ?")) {
                ps.setObject(1, edificioId);
                ps.setLong(2, idAlerta);
                ps.executeUpdate();
            }

        } catch (SQLException e) {
            LOGGER.warning("No se pudo procesar la alerta " + idAlerta + ": " + e.getMessage());
        }
    }

    public static void detener() {
        if (planificador != null) {
            planificador.shutdownNow();
        }
        if (hilos != null) {
            hilos.shutdownNow();
        }
    }
}
