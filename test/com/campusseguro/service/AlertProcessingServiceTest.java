package com.campusseguro.service;

import com.campusseguro.config.BaseDatos;
import com.campusseguro.entity.Agente;
import com.google.gson.JsonParser;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AlertProcessingServiceTest {

    @Before
    public void baseNueva() throws Exception {
        String ruta = System.getProperty("java.io.tmpdir") + "/campusseguro-proceso-prueba";
        new File(ruta + ".mv.db").delete();
        System.setProperty("campus.db", ruta);
        BaseDatos.crearTablas();
        BuildingService.cargarEdificios();
        // Puerto cerrado: simula un LDAP que no responde
        System.setProperty("LDAP_URL", "ldap://localhost:1");
    }

    @After
    public void limpiar() {
        System.clearProperty("LDAP_URL");
    }

    private static long alerta(String json) throws Exception {
        return AlertService.guardarAlerta(JsonParser.parseString(json).getAsJsonObject(), "SIMULADO");
    }

    // Fila de alerta con su enriquecimiento LDAP
    private static ResultSet fila(Connection con, long id) throws Exception {
        ResultSet rs = con.createStatement().executeQuery(
                "SELECT a.edificio_id, a.estado_procesamiento, l.resultado, l.uid"
                + " FROM alerta a LEFT JOIN enriquecimiento_ldap l ON l.alerta_id = a.id_interno"
                + " WHERE a.id_interno = " + id);
        assertTrue(rs.next());
        return rs;
    }

    // Toma el edificio del agente; aunque LDAP falle, la alerta queda procesada
    @Test
    public void asignaEdificioAunqueLdapFalle() throws Exception {
        BuildingService.guardarAgentes(List.of(new Agente("001", "pc-lab", "active")));
        BuildingService.asignarEdificio("001", 2);
        long id = alerta("{\"id\":\"1.1\",\"manager\":{\"name\":\"wazuh\"},\"agent\":{\"id\":\"001\"},\"data\":{\"srcuser\":\"ana\"}}");

        AlertProcessingService.procesar(id);

        try (Connection con = BaseDatos.conectar()) {
            ResultSet rs = fila(con, id);
            assertEquals(2, rs.getInt("edificio_id"));
            assertEquals("PROCESADA", rs.getString("estado_procesamiento"));
            assertEquals("ERROR_LDAP", rs.getString("resultado"));
            assertEquals("ana", rs.getString("uid"));
        }
    }

    // Sin usuario y con agente desconocido: SIN_USUARIO y Sin edificio
    @Test
    public void sinUsuarioNiEdificio() throws Exception {
        long id = alerta("{\"id\":\"2.2\",\"manager\":{\"name\":\"wazuh\"},\"agent\":{\"id\":\"999\"}}");

        AlertProcessingService.procesar(id);

        try (Connection con = BaseDatos.conectar()) {
            ResultSet rs = fila(con, id);
            assertNull(rs.getObject("edificio_id"));
            assertEquals("PROCESADA", rs.getString("estado_procesamiento"));
            assertEquals("SIN_USUARIO", rs.getString("resultado"));
        }
    }

    // Reasignar el agente después no cambia el edificio de la alerta ya procesada
    @Test
    public void reasignarAgenteNoCambiaHistorico() throws Exception {
        BuildingService.guardarAgentes(List.of(new Agente("001", "pc-lab", "active")));
        BuildingService.asignarEdificio("001", 2);
        long id = alerta("{\"id\":\"3.3\",\"manager\":{\"name\":\"wazuh\"},\"agent\":{\"id\":\"001\"}}");
        AlertProcessingService.procesar(id);

        BuildingService.asignarEdificio("001", 3);
        AlertProcessingService.procesar(id);

        try (Connection con = BaseDatos.conectar()) {
            assertEquals(2, fila(con, id).getInt("edificio_id"));
        }
    }

    // La recuperación solo devuelve las PENDIENTES, de la más antigua a la más nueva
    @Test
    public void pendientes() throws Exception {
        long primera = alerta("{\"id\":\"4.4\",\"manager\":{\"name\":\"wazuh\"}}");
        long segunda = alerta("{\"id\":\"5.5\",\"manager\":{\"name\":\"wazuh\"}}");
        long tercera = alerta("{\"id\":\"6.6\",\"manager\":{\"name\":\"wazuh\"}}");
        AlertProcessingService.procesar(segunda);

        assertEquals(List.of(primera, tercera), AlertProcessingService.pendientes());
    }
}
