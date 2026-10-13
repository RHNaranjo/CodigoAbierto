package com.campusseguro.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Conexión a la base H2 en modo archivo.
 *
 * @author rodrigonaranjo
 */
public class BaseDatos {

    static {
        try {
            // En Tomcat hay que cargar el driver a mano
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("No se encontró el driver de H2: " + e.getMessage());
        }
    }

    public static Connection conectar() throws SQLException {
        // Ruta del archivo; se puede cambiar con -Dcampus.db=...
        String ruta = System.getProperty("campus.db",
                System.getProperty("user.home") + "/CampusSeguro/campusseguro");
        return DriverManager.getConnection("jdbc:h2:file:" + ruta, "sa", "");
    }
}
