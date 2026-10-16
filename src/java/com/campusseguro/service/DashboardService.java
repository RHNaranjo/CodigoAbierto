package com.campusseguro.service;

import com.campusseguro.config.BaseDatos;
import com.campusseguro.dto.Filtro;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conteos y listados para el dashboard. Los filtros son números ya validados
 * en Filtro, por eso se pueden escribir directo en el SQL.
 *
 * @author rodrigonaranjo
 */
public class DashboardService {

    // Condiciones comunes: periodo, nivel y, fuera del modo demostración, solo REAL
    private static String where(Filtro f) {
        String w = " WHERE COALESCE(a.fecha_evento, a.fecha_recepcion) >= DATEADD('HOUR', -" + f.horas + ", LOCALTIMESTAMP)"
                + " AND COALESCE(a.nivel, 0) >= " + f.nivel;
        if (!f.demo) {
            w += " AND a.origen = 'REAL'";
        }
        return w;
    }

    private static String edificio(Filtro f) {
        if (f.edificio == null) {
            return "";
        }
        return f.edificio == 0 ? " AND a.edificio_id IS NULL AND a.estado_procesamiento = 'PROCESADA'"
                               : " AND a.edificio_id = " + f.edificio;
    }

    private static int contar(Connection con, String sql) throws SQLException {
        try (ResultSet rs = con.createStatement().executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // Por edificio: equipos registrados, activos y alertas del periodo
    public static Map<String, Object> resumen(Filtro f) throws SQLException {
        String sql = "SELECT e.id, e.nombre, e.poligono,"
                + " (SELECT COUNT(*) FROM agente g WHERE g.edificio_id = e.id) AS equipos,"
                + " (SELECT COUNT(*) FROM agente g WHERE g.edificio_id = e.id AND g.estado = 'active') AS activos,"
                + " (SELECT COUNT(*) FROM alerta a" + where(f) + " AND a.edificio_id = e.id) AS alertas"
                + " FROM edificio e ORDER BY e.id";

        List<Map<String, Object>> edificios = new ArrayList<>();
        Map<String, Object> resumen = new LinkedHashMap<>();

        try (Connection con = BaseDatos.conectar()) {
            try (ResultSet rs = con.createStatement().executeQuery(sql)) {
                while (rs.next()) {
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("id", rs.getInt("id"));
                    e.put("nombre", rs.getString("nombre"));
                    e.put("poligono", rs.getString("poligono"));
                    e.put("equipos", rs.getInt("equipos"));
                    e.put("activos", rs.getInt("activos"));
                    e.put("alertas", rs.getInt("alertas"));
                    edificios.add(e);
                }
            }
            resumen.put("edificios", edificios);
            resumen.put("sinEdificio", contar(con, "SELECT COUNT(*) FROM alerta a" + where(f)
                    + " AND a.edificio_id IS NULL AND a.estado_procesamiento = 'PROCESADA'"));
            resumen.put("equipos", contar(con, "SELECT COUNT(*) FROM agente"));
            resumen.put("activos", contar(con, "SELECT COUNT(*) FROM agente WHERE estado = 'active'"));
        }
        return resumen;
    }

    // Alertas del filtro, de la más reciente a la más antigua, por páginas
    public static Map<String, Object> alertas(Filtro f, int pagina, int tamano) throws SQLException {
        String desde = " FROM alerta a"
                + " LEFT JOIN edificio e ON e.id = a.edificio_id"
                + " LEFT JOIN agente g ON g.id_wazuh = a.agente_id"
                + " LEFT JOIN enriquecimiento_ldap l ON l.alerta_id = a.id_interno"
                + where(f) + edificio(f);
        String sql = "SELECT a.*, e.nombre AS edificio, g.nombre AS agente,"
                + " l.resultado, l.uid, l.nombre AS persona, l.correo, l.departamento" + desde
                + " ORDER BY COALESCE(a.fecha_evento, a.fecha_recepcion) DESC"
                + " LIMIT " + tamano + " OFFSET " + (pagina * tamano);

        List<Map<String, Object>> lista = new ArrayList<>();
        Map<String, Object> respuesta = new LinkedHashMap<>();

        try (Connection con = BaseDatos.conectar()) {
            respuesta.put("total", contar(con, "SELECT COUNT(*)" + desde));
            try (ResultSet rs = con.createStatement().executeQuery(sql)) {
                while (rs.next()) {
                    Map<String, Object> a = new LinkedHashMap<>();
                    a.put("idInterno", rs.getLong("id_interno"));
                    a.put("idWazuh", rs.getString("id_wazuh"));
                    a.put("fechaEvento", texto(rs.getTimestamp("fecha_evento")));
                    a.put("fechaRecepcion", texto(rs.getTimestamp("fecha_recepcion")));
                    a.put("agenteId", rs.getString("agente_id"));
                    a.put("agente", rs.getString("agente"));
                    a.put("edificioId", rs.getObject("edificio_id"));
                    a.put("edificio", rs.getString("edificio"));
                    a.put("reglaId", rs.getString("regla_id"));
                    a.put("nivel", rs.getObject("nivel"));
                    a.put("descripcion", rs.getString("descripcion"));
                    a.put("usuarioEvento", rs.getString("usuario_evento"));
                    a.put("estadoProcesamiento", rs.getString("estado_procesamiento"));
                    a.put("origen", rs.getString("origen"));

                    // Solo las alertas ya procesadas tienen resultado LDAP
                    if (rs.getString("resultado") != null) {
                        Map<String, Object> ldap = new LinkedHashMap<>();
                        ldap.put("resultado", rs.getString("resultado"));
                        ldap.put("uid", rs.getString("uid"));
                        ldap.put("nombre", rs.getString("persona"));
                        ldap.put("correo", rs.getString("correo"));
                        ldap.put("departamento", rs.getString("departamento"));
                        a.put("ldap", ldap);
                    }
                    lista.add(a);
                }
            }
        }
        respuesta.put("pagina", pagina);
        respuesta.put("tamano", tamano);
        respuesta.put("alertas", lista);
        return respuesta;
    }

    // Fechas de las alertas del filtro (para la gráfica por hora)
    public static List<Timestamp> fechas(Filtro f) throws SQLException {
        List<Timestamp> fechas = new ArrayList<>();
        try (Connection con = BaseDatos.conectar();
             ResultSet rs = con.createStatement().executeQuery(
                     "SELECT COALESCE(a.fecha_evento, a.fecha_recepcion) FROM alerta a" + where(f) + edificio(f))) {
            while (rs.next()) {
                fechas.add(rs.getTimestamp(1));
            }
        }
        return fechas;
    }

    private static String texto(Timestamp t) {
        return t == null ? null : t.toString();
    }
}
