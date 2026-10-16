package com.campusseguro.controller;

import com.campusseguro.config.Config;
import com.campusseguro.dto.Filtro;
import com.campusseguro.service.AlertProcessingService;
import com.campusseguro.service.AlertService;
import com.campusseguro.service.DashboardService;
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
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/alertas: alertas con su resultado LDAP, filtradas y por páginas.
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

    // ?horas=24&nivel=0&demo=false&edificio=2&pagina=0&tamano=20
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        int pagina = Filtro.entero(request.getParameter("pagina"), 0);
        int tamano = Math.min(Filtro.entero(request.getParameter("tamano"), 20), 200);

        try {
            response.getWriter().write(GSON.toJson(DashboardService.alertas(Filtro.desde(request), pagina, tamano)));
        } catch (SQLException e) {
            System.err.println("Error consultando alertas: " + e.getMessage());
            responder(response, 500, "error", "No se pudieron consultar las alertas.");
        }
    }

    private void responder(HttpServletResponse response, int codigo, String estado, String mensaje) throws IOException {
        response.setStatus(codigo);
        Map<String, String> cuerpo = new LinkedHashMap<>();
        cuerpo.put("estado", estado);
        cuerpo.put("mensaje", mensaje);
        response.getWriter().write(GSON.toJson(cuerpo));
    }
}
