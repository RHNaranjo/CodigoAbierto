package com.campusseguro.service;

import com.campusseguro.config.BaseDatos;
import com.campusseguro.dto.Filtro;
import com.campusseguro.entity.Agente;
import com.google.gson.JsonParser;
import java.io.File;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class DashboardServiceTest {

    @Before
    public void baseNueva() throws Exception {
        String ruta = System.getProperty("java.io.tmpdir") + "/campusseguro-dashboard-prueba";
        new File(ruta + ".mv.db").delete();
        System.setProperty("campus.db", ruta);
        BaseDatos.crearTablas();
        BuildingService.cargarEdificios();
        BuildingService.guardarAgentes(List.of(new Agente("001", "pc-lab", "active")));
        BuildingService.asignarEdificio("001", 2);

        // 3 reales y 1 simulada en Laboratorios
        alerta("r1", "REAL");
        alerta("r2", "REAL");
        alerta("r3", "REAL");
        alerta("s1", "SIMULADO");
    }

    private static void alerta(String id, String origen) throws Exception {
        Long interno = AlertService.guardarAlerta(JsonParser.parseString(
                "{\"id\":\"" + id + "\",\"manager\":{\"name\":\"wazuh\"},\"agent\":{\"id\":\"001\"},\"rule\":{\"level\":5}}")
                .getAsJsonObject(), origen);
        AlertProcessingService.procesar(interno);
    }

    @SuppressWarnings("unchecked")
    private static int alertasDe(Map<String, Object> resumen, int edificio) {
        for (Map<String, Object> e : (List<Map<String, Object>>) resumen.get("edificios")) {
            if ((Integer) e.get("id") == edificio) {
                return (Integer) e.get("alertas");
            }
        }
        return -1;
    }

    // Sin modo demostración no cuentan las simuladas
    @Test
    public void simuladasSoloEnDemo() throws Exception {
        Filtro f = new Filtro();
        assertEquals(3, alertasDe(DashboardService.resumen(f), 2));
        f.demo = true;
        assertEquals(4, alertasDe(DashboardService.resumen(f), 2));
        assertEquals(0, alertasDe(DashboardService.resumen(f), 1));
    }

    @Test
    public void nivelMinimo() throws Exception {
        Filtro f = new Filtro();
        f.nivel = 10;
        assertEquals(0, alertasDe(DashboardService.resumen(f), 2));
    }

    @Test
    public void paginas() throws Exception {
        Map<String, Object> r = DashboardService.alertas(new Filtro(), 1, 2);
        assertEquals(3, r.get("total"));
        assertEquals(1, ((List<?>) r.get("alertas")).size());
    }
}
