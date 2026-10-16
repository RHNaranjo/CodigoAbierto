package com.campusseguro.service;

import com.campusseguro.config.BaseDatos;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;

/**
 * Guarda las alertas de Wazuh en la base de datos.
 *
 * @author rodrigonaranjo
 */
public class AlertService {

    // Campos donde Wazuh suele traer el usuario (según decodificador).
    // Revisar contra una alerta real antes de agregar más.
    private static final String[][] CAMPOS_USUARIO = {
        {"data", "srcuser"},
        {"data", "dstuser"},
        {"data", "win", "eventdata", "targetUserName"}
    };

    /**
     * Guarda una alerta como PENDIENTE.
     *
     * @param resultado alerta de Wazuh, sola o dentro de "_source"
     * @param origen REAL o SIMULADO
     * @return true si se guardó; false si ya existía
     * @throws IllegalArgumentException si falta manager.name o id
     */
    public static boolean guardarAlerta(JsonObject resultado, String origen) throws SQLException {
        JsonObject alerta = resultado.has("_source") && resultado.get("_source").isJsonObject()
                ? resultado.getAsJsonObject("_source")
                : resultado;

        String managerId = texto(alerta, "manager", "name");
        String idWazuh = texto(alerta, "id");
        if (managerId == null || managerId.isBlank() || idWazuh == null || idWazuh.isBlank()) {
            throw new IllegalArgumentException("La alerta no contiene manager.name o id.");
        }

        String fecha = texto(alerta, "timestamp");
        if (fecha == null) {
            fecha = texto(alerta, "@timestamp");
        }

        String descripcion = texto(alerta, "rule", "description");
        if (descripcion != null && descripcion.length() > 500) {
            descripcion = descripcion.substring(0, 500);
        }

        // Edificio y usuario del directorio se completan al procesar (paso 6)
        String sql = "INSERT INTO alerta (manager_id, id_wazuh, fecha_evento, fecha_recepcion,"
                + " agente_id, regla_id, nivel, descripcion, usuario_evento,"
                + " estado_procesamiento, origen) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDIENTE', ?)";

        try (Connection con = BaseDatos.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, managerId);
            ps.setString(2, idWazuh);
            ps.setObject(3, convertirFecha(fecha));
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.setObject(5, texto(alerta, "agent", "id"));
            ps.setObject(6, texto(alerta, "rule", "id"));
            ps.setObject(7, entero(alerta, "rule", "level"));
            ps.setObject(8, descripcion);
            ps.setObject(9, usuario(alerta));
            ps.setString(10, origen);
            ps.executeUpdate();
            return true;

        } catch (SQLException e) {
            // 23505: la combinación manager_id + id_wazuh ya existe
            if ("23505".equals(e.getSQLState())) {
                return false;
            }
            throw e;
        }
    }

    // Primer campo de usuario que traiga la alerta, o null
    private static String usuario(JsonObject alerta) {
        for (String[] ruta : CAMPOS_USUARIO) {
            String valor = texto(alerta, ruta);
            if (valor != null && !valor.isBlank()) {
                return valor.length() > 200 ? valor.substring(0, 200) : valor;
            }
        }
        return null;
    }

    // Valor de un campo anidado, por ejemplo texto(alerta, "rule", "id")
    private static String texto(JsonObject objeto, String... ruta) {
        JsonElement actual = objeto;
        for (String campo : ruta) {
            if (actual == null || !actual.isJsonObject() || !actual.getAsJsonObject().has(campo)) {
                return null;
            }
            actual = actual.getAsJsonObject().get(campo);
        }
        return actual.isJsonPrimitive() ? actual.getAsString() : null;
    }

    private static Integer entero(JsonObject objeto, String... ruta) {
        String valor = texto(objeto, ruta);
        try {
            return valor == null ? null : Integer.valueOf(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Wazuh manda fechas como 2026-10-15T18:10:46.123+0000
    private static Timestamp convertirFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        String normalizada = fecha.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2");
        return Timestamp.from(OffsetDateTime.parse(normalizada).toInstant());
    }
}
