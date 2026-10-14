package com.campusseguro.service;

import com.campusseguro.config.BaseDatos;
import com.campusseguro.entity.Agente;
import com.campusseguro.entity.Edificio;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Edificios, agentes y la asignación agente -> edificio.
 *
 * @author rodrigonaranjo
 */
public class BuildingService {

    // Los tres edificios del prototipo; MERGE evita duplicarlos en cada arranque
    public static void cargarEdificios() throws SQLException {
        String sql = "MERGE INTO edificio (id, nombre, poligono) KEY (id) VALUES"
                + " (1, 'Biblioteca', 'biblioteca'),"
                + " (2, 'Laboratorios', 'laboratorios'),"
                + " (3, 'Administración', 'administracion')";
        try (Connection con = BaseDatos.conectar()) {
            con.createStatement().execute(sql);
        }
    }

    public static List<Edificio> listarEdificios() throws SQLException {
        List<Edificio> edificios = new ArrayList<>();
        try (Connection con = BaseDatos.conectar();
             ResultSet rs = con.createStatement().executeQuery("SELECT id, nombre, poligono FROM edificio ORDER BY id")) {
            while (rs.next()) {
                edificios.add(new Edificio(rs.getInt("id"), rs.getString("nombre"), rs.getString("poligono")));
            }
        }
        return edificios;
    }

    public static List<Agente> listarAgentes() throws SQLException {
        List<Agente> agentes = new ArrayList<>();
        try (Connection con = BaseDatos.conectar();
             ResultSet rs = con.createStatement().executeQuery("SELECT * FROM agente ORDER BY id_wazuh")) {
            while (rs.next()) {
                Agente a = new Agente(rs.getString("id_wazuh"), rs.getString("nombre"), rs.getString("estado"));
                a.setEdificioId((Integer) rs.getObject("edificio_id"));
                Timestamp fecha = rs.getTimestamp("ultima_sincronizacion");
                a.setUltimaSincronizacion(fecha == null ? null : fecha.toString());
                agentes.add(a);
            }
        }
        return agentes;
    }

    // Guarda los agentes que vienen de Wazuh. No toca edificio_id,
    // así no se pierde la asignación manual.
    public static void guardarAgentes(List<Agente> agentes) throws SQLException {
        String sql = "MERGE INTO agente (id_wazuh, nombre, estado, ultima_sincronizacion) KEY (id_wazuh) VALUES (?, ?, ?, ?)";
        Timestamp ahora = new Timestamp(System.currentTimeMillis());
        try (Connection con = BaseDatos.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (Agente a : agentes) {
                ps.setString(1, a.getIdWazuh());
                ps.setString(2, a.getNombre());
                ps.setString(3, a.getEstado());
                ps.setTimestamp(4, ahora);
                ps.executeUpdate();
            }
        }
    }

    // Asigna un edificio a un agente (null lo deja "Sin edificio").
    // Devuelve false si el agente no existe.
    public static boolean asignarEdificio(String idWazuh, Integer edificioId) throws SQLException {
        try (Connection con = BaseDatos.conectar();
             PreparedStatement ps = con.prepareStatement("UPDATE agente SET edificio_id = ? WHERE id_wazuh = ?")) {
            if (edificioId == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, edificioId);
            }
            ps.setString(2, idWazuh);
            return ps.executeUpdate() > 0;
        }
    }
}
