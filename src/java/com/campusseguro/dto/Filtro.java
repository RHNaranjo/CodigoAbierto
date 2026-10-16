package com.campusseguro.dto;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Filtros del dashboard, leídos de la URL:
 * ?horas=24&nivel=0&demo=false&edificio=2 (edificio=0 es "Sin edificio").
 */
public class Filtro {

    public int horas = 24;          // periodo hacia atrás
    public int nivel = 0;           // nivel mínimo de la regla
    public boolean demo = false;    // true incluye las alertas SIMULADAS
    public Integer edificio = null; // null = todos

    public static Filtro desde(HttpServletRequest request) {
        Filtro f = new Filtro();
        f.horas = entero(request.getParameter("horas"), 24);
        f.nivel = entero(request.getParameter("nivel"), 0);
        f.demo = "true".equals(request.getParameter("demo"));
        String edificio = request.getParameter("edificio");
        if (edificio != null && !edificio.isBlank()) {
            f.edificio = entero(edificio, 0);
        }
        return f;
    }

    // Número de la URL; si no es válido, el valor por defecto
    public static int entero(String valor, int defecto) {
        try {
            return valor == null ? defecto : Math.max(0, Integer.parseInt(valor));
        } catch (NumberFormatException e) {
            return defecto;
        }
    }
}
