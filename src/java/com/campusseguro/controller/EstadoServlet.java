package com.campusseguro.controller;

import com.campusseguro.config.BaseDatos;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Verifica que la aplicación y la base de datos responden.
 *
 * @author rodrigonaranjo
 */
@WebServlet(name = "EstadoServlet", urlPatterns = {"/api/estado"})
public class EstadoServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");

        try (Connection con = BaseDatos.conectar()) {
            String version = con.getMetaData().getDatabaseProductVersion();
            response.getWriter().write("{\"estado\":\"ok\", \"h2\":\"" + version + "\"}");
        } catch (SQLException e) {
            System.err.println("Error conectando a H2: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().write("{\"estado\":\"error\"}");
        }
    }
}
