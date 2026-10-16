package com.campusseguro.service;

import com.campusseguro.config.BaseDatos;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class AlertService {

    /**
     * Guarda una alerta de Wazuh en la base de datos.
     *
     * @param resultado Resultado de búsqueda de OpenSearch.
     *                   Puede contener la alerta dentro de "_source".
     * @return true si se insertó; false si ya existía.
     */
    public static boolean guardarAlerta(JsonObject resultado)
            throws SQLException {

        JsonObject alerta = resultado.has("_source")
                && resultado.get("_source").isJsonObject()
                ? resultado.getAsJsonObject("_source")
                : resultado;

        String managerId = texto(alerta, "manager", "name");
        String idWazuh = texto(alerta, "id");

        if (managerId == null || managerId.isBlank()
                || idWazuh == null || idWazuh.isBlank()) {
            throw new IllegalArgumentException(
                    "La alerta no contiene manager.name o id.");
        }

        String agenteId = texto(alerta, "agent", "id");
        String reglaId = texto(alerta, "rule", "id");
        String descripcion = texto(alerta, "rule", "description");
        Integer nivel = entero(alerta, "rule", "level");

        String fecha = texto(alerta, "timestamp");

        if (fecha == null) {
            fecha = texto(alerta, "@timestamp");
        }

        Timestamp fechaEvento = convertirFecha(fecha);

        if (descripcion != null && descripcion.length() > 500) {
            descripcion = descripcion.substring(0, 500);
        }

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
            return true;

        } catch (SQLException e) {
            // H2: violación de una restricción de unicidad.
            if ("23505".equals(e.getSQLState())) {
                return false;
            }

            throw e;
        }
    }

    private static String texto(JsonObject objeto, String... ruta) {
        com.google.gson.JsonElement actual = objeto;

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

    private static Integer entero(JsonObject objeto, String... ruta) {
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

    private static Timestamp convertirFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }

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
}