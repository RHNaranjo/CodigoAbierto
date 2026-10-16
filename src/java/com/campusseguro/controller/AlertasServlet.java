package com.campusseguro.controller;

import com.campusseguro.config.BaseDatos;
import com.campusseguro.config.Config;
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
 * GET /api/alertas: alertas guardadas.
 * POST /api/alertas: guarda una alerta SIMULADA (cabecera X-API-Key).
 * Las alertas REALES las trae AlertSyncJob desde el indexador de Wazuh.
 *
 * @author rodrigonaranjo
 */
@WebServlet(name = "AlertasServlet", urlPatterns = {"/api/alertas"})
public class AlertasServlet extends HttpServlet {

    private static final Gson GSON = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");

        String sql = "SELECT * FROM alerta ORDER BY fecha_recepcion DESC";
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
