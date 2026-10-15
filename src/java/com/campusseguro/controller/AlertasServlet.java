package com.campusseguro.controller;

import com.campusseguro.config.BaseDatos;
import com.campusseguro.config.Config;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = "AlertasServlet", urlPatterns = {"/api/alertas"})
public class AlertasServlet extends HttpServlet {

    private static final Gson GSON = new Gson();

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response) throws IOException {

        response.setContentType("application/json; charset=UTF-8");

        // 1. Comprobar que la clave de integración esté configurada.
        String claveConfigurada = Config.get("CAMPUS_ALERTAS_API_KEY");

        if (claveConfigurada == null || claveConfigurada.isBlank()) {
            responder(response, 503, "error",
                    "La clave de integración no está configurada.");
            return;
        }

        // 2. Validar la clave enviada en la petición.
        String claveRecibida = request.getHeader("X-API-Key");

        if (claveRecibida == null
                || !MessageDigest.isEqual(
                        claveConfigurada.getBytes(StandardCharsets.UTF_8),
                        claveRecibida.getBytes(StandardCharsets.UTF_8))) {

            responder(response, 401, "error", "No autorizado.");
            return;
        }

        // 3. Leer y validar el JSON.
        final JsonObject alerta;

        try {
            JsonObject recibido = JsonParser.parseString(
                    request.getReader()
                            .lines()
                            .reduce("", (a, b) -> a + b)
            ).getAsJsonObject();

            // Acepta tanto una alerta directa como un resultado de búsqueda
            // de Wazuh que contenga el objeto dentro de "_source".
            alerta = recibido.has("_source")
                    && recibido.get("_source").isJsonObject()
                    ? recibido.getAsJsonObject("_source")
                    : recibido;

            if (alerta.has("manager") && alerta.get("manager").isJsonObject()) {
                // El JSON de Wazuh ya tiene la estructura esperada.
            } else {
                throw new IllegalArgumentException("Falta el objeto manager.");
            }

        } catch (Exception e) {
            responder(response, 400, "error", "El JSON recibido no es válido.");
            return;
        }

        // 4. Extraer los campos de la alerta.
        String managerId = texto(alerta, "manager", "name");
        String idWazuh = texto(alerta, "id");

        // Si se recibe un resultado completo de búsqueda, el identificador
        // también puede encontrarse en el campo "_id" exterior.
        if (idWazuh == null || idWazuh.isBlank()) {
            responder(response, 400, "error",
                    "La alerta no contiene un identificador.");
            return;
        }

        if (managerId == null || managerId.isBlank()) {
            responder(response, 400, "error",
                    "La alerta no contiene el identificador del manager.");
            return;
        }

        String agenteId = texto(alerta, "agent", "id");
        String reglaId = texto(alerta, "rule", "id");
        String descripcion = texto(alerta, "rule", "description");
        Integer nivel = entero(alerta, "rule", "level");

        // Wazuh puede proporcionar timestamp o @timestamp.
        String fecha = texto(alerta, "timestamp");

        if (fecha == null) {
            fecha = texto(alerta, "@timestamp");
        }

        Timestamp fechaEvento;

        try {
            fechaEvento = convertirFecha(fecha);
        } catch (Exception e) {
            responder(response, 400, "error",
                    "La fecha del evento tiene un formato inválido.");
            return;
        }

        if (descripcion != null && descripcion.length() > 500) {
            descripcion = descripcion.substring(0, 500);
        }

        // 5. Guardar la alerta en H2.
        // Edificio y usuario quedan nulos hasta su procesamiento.
        String sql = "INSERT INTO alerta "
                + "(manager_id, id_wazuh, fecha_evento, fecha_recepcion, "
                + "agente_id, edificio_id, regla_id, nivel, descripcion, "
                + "usuario_evento, estado_procesamiento, origen) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection con = BaseDatos.conectar();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, managerId);
            ps.setString(2, idWazuh);

            if (fechaEvento == null) {
                ps.setNull(3, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(3, fechaEvento);
            }

            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));

            if (agenteId == null) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, agenteId);
            }

            ps.setNull(6, Types.INTEGER);

            if (reglaId == null) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, reglaId);
            }

            if (nivel == null) {
                ps.setNull(8, Types.INTEGER);
            } else {
                ps.setInt(8, nivel);
            }

            if (descripcion == null) {
                ps.setNull(9, Types.VARCHAR);
            } else {
                ps.setString(9, descripcion);
            }

            ps.setNull(10, Types.VARCHAR);
            ps.setString(11, "PENDIENTE");
            ps.setString(12, "REAL");

            ps.executeUpdate();

            responder(response, 202, "ok", "Alerta guardada.");

        } catch (SQLException e) {

            // SQLState 23505: restricción de unicidad de H2.
            // La combinación manager_id + id_wazuh ya existe.
            if ("23505".equals(e.getSQLState())) {
                responder(response, 202, "ok",
                        "La alerta ya estaba registrada.");
                return;
            }

            System.err.println("Error guardando alerta: " + e.getMessage());

            responder(response, 503, "error",
                    "No se pudo guardar la alerta.");
        }
    }
   

@Override
protected void doGet(
        HttpServletRequest request,
        HttpServletResponse response) throws IOException {

    response.setContentType("application/json; charset=UTF-8");

    String sql =
        "SELECT id_interno, manager_id, id_wazuh, fecha_evento, " +
        "fecha_recepcion, agente_id, edificio_id, regla_id, nivel, " +
        "descripcion, usuario_evento, estado_procesamiento, origen " +
        "FROM alerta ORDER BY fecha_recepcion DESC";

    List<Map<String, Object>> alertas = new ArrayList<>();

    try (
        Connection con = BaseDatos.conectar();
        PreparedStatement ps = con.prepareStatement(sql);
        ResultSet rs = ps.executeQuery()
    ) {

        while (rs.next()) {

            Map<String, Object> alerta = new LinkedHashMap<>();

            alerta.put("idInterno", rs.getLong("id_interno"));
            alerta.put("managerId", rs.getString("manager_id"));
            alerta.put("idWazuh", rs.getString("id_wazuh"));

            Timestamp fechaEvento = rs.getTimestamp("fecha_evento");
            alerta.put(
                "fechaEvento",
                fechaEvento == null ? null : fechaEvento.toString()
            );

            Timestamp fechaRecepcion = rs.getTimestamp("fecha_recepcion");
            alerta.put(
                "fechaRecepcion",
                fechaRecepcion == null ? null : fechaRecepcion.toString()
            );

            alerta.put("agenteId", rs.getString("agente_id"));

            int edificioId = rs.getInt("edificio_id");
            alerta.put("edificioId", rs.wasNull() ? null : edificioId);

            alerta.put("reglaId", rs.getString("regla_id"));

            int nivel = rs.getInt("nivel");
            alerta.put("nivel", rs.wasNull() ? null : nivel);

            alerta.put("descripcion", rs.getString("descripcion"));
            alerta.put("usuarioEvento", rs.getString("usuario_evento"));

            alerta.put(
                "estadoProcesamiento",
                rs.getString("estado_procesamiento")
            );

            alerta.put("origen", rs.getString("origen"));

            alertas.add(alerta);
        }

        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(GSON.toJson(alertas));

    } catch (SQLException e) {

        System.err.println(
            "Error consultando alertas: " + e.getMessage()
        );

        response.setStatus(
            HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        );

        response.getWriter().write(
            "{\"estado\":\"error\"," +
            "\"mensaje\":\"No se pudieron consultar las alertas.\"}"
        );
    }
}

    
    private String texto(JsonObject objeto, String... ruta) {
        JsonElement actual = objeto;

        for (String campo : ruta) {
            if (actual == null || !actual.isJsonObject()) {
                return null;
            }

            JsonObject o = actual.getAsJsonObject();

            if (!o.has(campo) || o.get(campo).isJsonNull()) {
                return null;
            }

            actual = o.get(campo);
        }

        if (!actual.isJsonPrimitive()) {
            return null;
        }

        return actual.getAsString();
    }

    private Integer entero(JsonObject objeto, String... ruta) {
        String valor = texto(objeto, ruta);

        if (valor == null) {
            return null;
        }

        try {
            return Integer.valueOf(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Timestamp convertirFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }

        // Normalizar offsets como +0000 a +00:00.
        String normalizada = fecha.replaceFirst(
                "([+-]\\d{2})(\\d{2})$", "$1:$2");

        try {
            return Timestamp.from(Instant.parse(normalizada));
        } catch (Exception e) {
            return Timestamp.from(
                    OffsetDateTime.parse(
                            normalizada,
                            DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    ).toInstant()
            );
        }
    }

    private void responder(HttpServletResponse response,
                           int codigo,
                           String estado,
                           String mensaje) throws IOException {

        response.setStatus(codigo);

        Map<String, String> cuerpo = new HashMap<>();
        cuerpo.put("estado", estado);
        cuerpo.put("mensaje", mensaje);

        response.getWriter().write(GSON.toJson(cuerpo));
    }
}