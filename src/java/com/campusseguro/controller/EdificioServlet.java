package com.campusseguro.controller;

import com.campusseguro.service.BuildingService;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

/**
 * GET /api/edificios: lista de edificios.
 *
 * @author rodrigonaranjo
 */
@WebServlet(name = "EdificioServlet", urlPatterns = {"/api/edificios"})
public class EdificioServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        try {
            response.getWriter().write(gson.toJson(BuildingService.listarEdificios()));
        } catch (SQLException e) {
            System.err.println("Error listando edificios: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"No se pudo consultar la base\"}");
        }
    }
}
