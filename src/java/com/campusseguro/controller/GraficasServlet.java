package com.campusseguro.controller;

import com.campusseguro.dto.Filtro;
import com.campusseguro.service.ChartService;
import com.campusseguro.service.DashboardService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;

/**
 * GET /graficas/alertas-por-edificio.png: barras del periodo seleccionado.
 * GET /graficas/alertas-por-hora.png: línea de las últimas 24 horas.
 * Aceptan los mismos filtros que /api/alertas.
 *
 * @author rodrigonaranjo
 */
@WebServlet(name = "GraficasServlet", urlPatterns = {"/graficas/*"})
public class GraficasServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Filtro f = Filtro.desde(request);
        JFreeChart chart;

        try {
            if ("/alertas-por-edificio.png".equals(request.getPathInfo())) {
                f.edificio = null; // las barras siempre muestran todos los edificios
                chart = ChartService.porEdificio(DashboardService.resumen(f));
            } else if ("/alertas-por-hora.png".equals(request.getPathInfo())) {
                f.horas = 24;
                chart = ChartService.porHora(DashboardService.fechas(f));
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        } catch (SQLException e) {
            System.err.println("Error generando gráfica: " + e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        response.setContentType("image/png");
        response.setHeader("Cache-Control", "no-store");
        int ancho = Filtro.entero(request.getParameter("ancho"), 520);
        int alto = Filtro.entero(request.getParameter("alto"), 240);
        ChartUtils.writeChartAsPNG(response.getOutputStream(), chart, Math.min(ancho, 1600), Math.min(alto, 1000));
    }
}
