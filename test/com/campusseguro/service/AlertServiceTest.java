package com.campusseguro.service;

import com.campusseguro.config.BaseDatos;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class AlertServiceTest {

    @Before
    public void baseNueva() throws Exception {
        String ruta = System.getProperty("java.io.tmpdir") + "/campusseguro-alertas-prueba";
        new File(ruta + ".mv.db").delete();
        System.setProperty("campus.db", ruta);
        BaseDatos.crearTablas();
    }

    private static JsonObject alerta(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    // Lee la única fila de alerta
    private static ResultSet primeraFila(Connection con) throws Exception {
        ResultSet rs = con.createStatement().executeQuery("SELECT * FROM alerta");
        assertTrue(rs.next());
        return rs;
    }

    // Alerta tal como la devuelve OpenSearch (dentro de _source)
    @Test
    public void guardaAlertaConUsuario() throws Exception {
        JsonObject hit = alerta("{\"_source\":{\"id\":\"1.1\",\"timestamp\":\"2026-10-15T18:10:46.123+0000\","
                + "\"manager\":{\"name\":\"wazuh\"},\"agent\":{\"id\":\"001\"},"
                + "\"rule\":{\"id\":\"5710\",\"level\":5,\"description\":\"Fallo de login\"},"
                + "\"data\":{\"srcuser\":\"ana\"}}}");

        assertTrue(AlertService.guardarAlerta(hit, "REAL"));

        try (Connection con = BaseDatos.conectar()) {
            ResultSet rs = primeraFila(con);
            assertEquals("ana", rs.getString("usuario_evento"));
            assertEquals("001", rs.getString("agente_id"));
            assertEquals(5, rs.getInt("nivel"));
            assertEquals("PENDIENTE", rs.getString("estado_procesamiento"));
            assertEquals("REAL", rs.getString("origen"));
            assertNotNull(rs.getTimestamp("fecha_evento"));
        }
    }

    // Un reenvío no duplica la alerta
    @Test
    public void alertaRepetidaNoSeDuplica() throws Exception {
        JsonObject a = alerta("{\"id\":\"2.2\",\"manager\":{\"name\":\"wazuh\"}}");

        assertTrue(AlertService.guardarAlerta(a, "SIMULADO"));
        assertFalse(AlertService.guardarAlerta(a, "SIMULADO"));

        try (Connection con = BaseDatos.conectar()) {
            ResultSet rs = con.createStatement().executeQuery("SELECT COUNT(*) FROM alerta");
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }

    // Un evento sin usuario ni agente se guarda con esos campos nulos
    @Test
    public void alertaSinUsuario() throws Exception {
        AlertService.guardarAlerta(alerta("{\"id\":\"3.3\",\"manager\":{\"name\":\"wazuh\"}}"), "SIMULADO");

        try (Connection con = BaseDatos.conectar()) {
            ResultSet rs = primeraFila(con);
            assertNull(rs.getString("usuario_evento"));
            assertNull(rs.getString("agente_id"));
            assertEquals("SIMULADO", rs.getString("origen"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void sinManagerSeRechaza() throws Exception {
        AlertService.guardarAlerta(alerta("{\"id\":\"4.4\"}"), "REAL");
    }
}
