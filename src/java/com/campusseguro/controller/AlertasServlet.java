package com.campusseguro.controller;

import com.campusseguro.config.BaseDatos;
import com.campusseguro.config.Config;
import com.campusseguro.service.AlertProcessingService;
import com.campusseguro.service.AlertService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/alertas: alertas guardadas con su resultado LDAP.
 * POST /api/alertas: guarda una alerta SIMULADA (cabecera X-API-Key).
 * Las alertas REALES las trae AlertSyncJob desde el indexador de Wazuh.
 *
 * @author rodrigonaranjo
 */
@WebServlet(name = "AlertasServlet", urlPatterns = {"/api/alertas"})
public class AlertasServlet extends HttpServlet {

    private static final Gson GSON = new Gson();
    private static final int TAMANO_MAXIMO = 100_000; // bytes

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");

        // La clave debe estar configurada y coincidir con la recibida
        String clave = Config.get("CAMPUS_ALERTAS_API_KEY");
        if (clave == null || clave.isBlank()) {
            responder(response, 503, "error", "La clave de integración no está configurada.");
            return;
        }
        String recibida = request.getHeader("X-API-Key");
        if (recibida == null || !MessageDigest.isEqual(
                clave.getBytes(StandardCharsets.UTF_8), recibida.getBytes(StandardCharsets.UTF_8))) {
            responder(response, 401, "error", "No autorizado.");
            return;
        }

        // Se lee un byte de más para detectar que el JSON excede el límite
        byte[] cuerpo = request.getInputStream().readNBytes(TAMANO_MAXIMO + 1);
        if (cuerpo.length > TAMANO_MAXIMO) {
            responder(response, 413, "error", "El JSON es demasiado grande.");
            return;
        }

        try {
            JsonObject alerta = JsonParser.parseString(new String(cuerpo, StandardCharsets.UTF_8)).getAsJsonObject();
            Long id = AlertService.guardarAlerta(alerta, "SIMULADO");
            if (id == null) {
                responder(response, 202, "ok", "La alerta ya estaba registrada.");
                return;
            }
            // Primero persistida, después a procesar (si la cola está llena, la recuperación la recoge)
            AlertProcessingService.encolar(id);
            responder(response, 202, "ok", "Alerta guardada.");
        } catch (SQLException e) {
            System.err.println("Error guardando alerta: " + e.getMessage());
            responder(response, 503, "error", "No se pudo guardar la alerta.");
        } catch (RuntimeException e) {
            // JSON mal formado, sin id o manager.name, o con fecha inválida
            responder(response, 400, "error", "El JSON recibido no es válido.");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");

        String sql = "SELECT a.*, l.resultado, l.uid, l.nombre, l.correo, l.departamento"
                + " FROM alerta a LEFT JOIN enriquecimiento_ldap l ON l.alerta_id = a.id_interno"
                + " ORDER BY a.fecha_recepcion DESC";
        List<Map<String, Object>> alertas = new ArrayList<>();

        try (Connection con = BaseDatos.conectar();
             ResultSet rs = con.createStatement().executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> alerta = new LinkedHashMap<>();
                alerta.put("idInterno", rs.getLong("id_interno"));
                alerta.put("managerId", rs.getString("manager_id"));
                alerta.put("idWazuh", rs.getString("id_wazuh"));
                alerta.put("fechaEvento", fecha(rs.getTimestamp("fecha_evento")));
                alerta.put("fechaRecepcion", fecha(rs.getTimestamp("fecha_recepcion")));
                alerta.put("agenteId", rs.getString("agente_id"));
                alerta.put("edificioId", rs.getObject("edificio_id"));
                alerta.put("reglaId", rs.getString("regla_id"));
                alerta.put("nivel", rs.getObject("nivel"));
                alerta.put("descripcion", rs.getString("descripcion"));
                alerta.put("usuarioEvento", rs.getString("usuario_evento"));
                alerta.put("estadoProcesamiento", rs.getString("estado_procesamiento"));
                alerta.put("origen", rs.getString("origen"));

                // Solo las alertas ya procesadas tienen resultado LDAP
                if (rs.getString("resultado") != null) {
                    Map<String, Object> ldap = new LinkedHashMap<>();
                    ldap.put("resultado", rs.getString("resultado"));
                    ldap.put("uid", rs.getString("uid"));
                    ldap.put("nombre", rs.getString("nombre"));
                    ldap.put("correo", rs.getString("correo"));
                    ldap.put("departamento", rs.getString("departamento"));
                    alerta.put("ldap", ldap);
                }
                alertas.add(alerta);
            }
            response.getWriter().write(GSON.toJson(alertas));

        } catch (SQLException e) {
            System.err.println("Error consultando alertas: " + e.getMessage());
            responder(response, 500, "error", "No se pudieron consultar las alertas.");
        }
    }

    private static String fecha(Timestamp t) {
        return t == null ? null : t.toString();
    }

    private void responder(HttpServletResponse response, int codigo, String estado, String mensaje) throws IOException {
        response.setStatus(codigo);
        Map<String, String> cuerpo = new LinkedHashMap<>();
        cuerpo.put("estado", estado);
        cuerpo.put("mensaje", mensaje);
        response.getWriter().write(GSON.toJson(cuerpo));
    }
}
