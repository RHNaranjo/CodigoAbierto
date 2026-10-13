package com.campusseguro.config;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BaseDatosTest {

    // Conecta, ejecuta una consulta y revisa que se creó el archivo
    @Test
    public void conectaYCreaArchivo() throws Exception {
        String ruta = System.getProperty("java.io.tmpdir") + "/campusseguro-prueba";
        System.setProperty("campus.db", ruta);

        try (Connection con = BaseDatos.conectar();
             ResultSet rs = con.createStatement().executeQuery("SELECT 1")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
        assertTrue(new File(ruta + ".mv.db").exists());
    }
}
