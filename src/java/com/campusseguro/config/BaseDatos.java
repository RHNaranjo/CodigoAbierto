package com.campusseguro.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

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

    // Tablas del modelo de datos (sección 11 de CampusSeguro.md)
    private static final String[] TABLAS = {
        "CREATE TABLE IF NOT EXISTS edificio ("
            + " id INT PRIMARY KEY,"
            + " nombre VARCHAR(50) NOT NULL,"
            + " poligono VARCHAR(50) NOT NULL)",

        "CREATE TABLE IF NOT EXISTS agente ("
            + " id_wazuh VARCHAR(10) PRIMARY KEY,"
            + " nombre VARCHAR(100),"
            + " estado VARCHAR(30),"
            + " edificio_id INT REFERENCES edificio(id),"
            + " ultima_sincronizacion TIMESTAMP)",

        "CREATE TABLE IF NOT EXISTS alerta ("
            + " id_interno BIGINT AUTO_INCREMENT PRIMARY KEY,"
            + " manager_id VARCHAR(100) NOT NULL,"
            + " id_wazuh VARCHAR(50) NOT NULL,"
            + " fecha_evento TIMESTAMP,"
            + " fecha_recepcion TIMESTAMP,"
            + " agente_id VARCHAR(10),"
            + " edificio_id INT,"
            + " regla_id VARCHAR(20),"
            + " nivel INT,"
            + " descripcion VARCHAR(500),"
            + " usuario_evento VARCHAR(200),"
            + " estado_procesamiento VARCHAR(20),"
            + " origen VARCHAR(10),"
            + " UNIQUE (manager_id, id_wazuh))", // evita duplicar reenvíos

        "CREATE TABLE IF NOT EXISTS enriquecimiento_ldap ("
            + " alerta_id BIGINT PRIMARY KEY REFERENCES alerta(id_interno),"
            + " resultado VARCHAR(20),"
            + " uid VARCHAR(100),"
            + " nombre VARCHAR(200),"
            + " correo VARCHAR(200),"
            + " departamento VARCHAR(100),"
            + " fecha_consulta TIMESTAMP)"
    };

    public static void crearTablas() throws SQLException {
        try (Connection con = conectar(); Statement st = con.createStatement()) {
            for (String sql : TABLAS) {
                st.execute(sql);
            }
        }
    }
}
