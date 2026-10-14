package com.campusseguro.controller;

import com.campusseguro.service.AgentSyncJob;
import com.campusseguro.service.BuildingService;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/agentes: agentes, estado y edificio.
 * PUT /api/agentes?id=001&edificio=2: asigna un edificio (sin "edificio" lo quita).
 *
 * @author rodrigonaranjo
 */
@WebServlet(name = "AgenteServlet", urlPatterns = {"/api/agentes"})
public class AgenteServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        try {
            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("desactualizado", AgentSyncJob.desactualizado);
            respuesta.put("ultimaSincronizacion", AgentSyncJob.ultimaSincronizacion);
            respuesta.put("agentes", BuildingService.listarAgentes());
            response.getWriter().write(gson.toJson(respuesta));
        } catch (SQLException e) {
            System.err.println("Error listando agentes: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"No se pudo consultar la base\"}");
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        String id = request.getParameter("id");
        String edificio = request.getParameter("edificio");

        if (id == null || id.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Falta el parámetro id\"}");
            return;
        }

        try {
            Integer edificioId = (edificio == null || edificio.isBlank()) ? null : Integer.valueOf(edificio);
            if (BuildingService.asignarEdificio(id, edificioId)) {
                response.getWriter().write("{\"estado\":\"ok\"}");
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"error\":\"Agente no encontrado\"}");
            }
        } catch (NumberFormatException | SQLException e) {
            // Edificio que no es número o que no existe
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Edificio no válido\"}");
        }
    }
}
