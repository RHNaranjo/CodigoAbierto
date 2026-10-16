package com.campusseguro.controller;

import com.campusseguro.dto.Filtro;
import com.campusseguro.service.AgentSyncJob;
import com.campusseguro.service.AlertSyncJob;
import com.campusseguro.service.DashboardService;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * GET /api/edificios/resumen: conteos por edificio para el mapa, más el
 * estado de las sincronizaciones con Wazuh.
 *
 * @author rodrigonaranjo
 */
@WebServlet(name = "ResumenServlet", urlPatterns = {"/api/edificios/resumen"})
public class ResumenServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        try {
            Map<String, Object> resumen = DashboardService.resumen(Filtro.desde(request));
            resumen.put("actualizado", LocalDateTime.now().withNano(0).toString());
            resumen.put("agentesDesactualizados", AgentSyncJob.desactualizado);
            resumen.put("alertasDesactualizadas", AlertSyncJob.desactualizado);
            response.getWriter().write(gson.toJson(resumen));
        } catch (SQLException e) {
            System.err.println("Error en el resumen: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"No se pudo consultar la base\"}");
        }
    }
}
