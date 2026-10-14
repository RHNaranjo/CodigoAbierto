package com.campusseguro.service;

import com.campusseguro.config.BaseDatos;
import com.campusseguro.entity.Agente;
import java.io.File;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class BuildingServiceTest {

    @Before
    public void baseNueva() throws Exception {
        String ruta = System.getProperty("java.io.tmpdir") + "/campusseguro-edificios-prueba";
        new File(ruta + ".mv.db").delete();
        System.setProperty("campus.db", ruta);
        BaseDatos.crearTablas();
    }

    // Cargar dos veces no debe duplicar los edificios
    @Test
    public void cargaTresEdificios() throws Exception {
        BuildingService.cargarEdificios();
        BuildingService.cargarEdificios();
        assertEquals(3, BuildingService.listarEdificios().size());
    }

    // Una nueva sincronización no debe borrar el edificio asignado a mano
    @Test
    public void sincronizarConservaAsignacion() throws Exception {
        BuildingService.cargarEdificios();
        BuildingService.guardarAgentes(List.of(new Agente("001", "pc-lab", "active")));
        assertTrue(BuildingService.asignarEdificio("001", 2));

        BuildingService.guardarAgentes(List.of(new Agente("001", "pc-lab", "disconnected")));

        Agente a = BuildingService.listarAgentes().get(0);
        assertEquals("disconnected", a.getEstado());
        assertEquals(Integer.valueOf(2), a.getEdificioId());
    }

    @Test
    public void agenteInexistente() throws Exception {
        assertFalse(BuildingService.asignarEdificio("999", 1));
    }
}
