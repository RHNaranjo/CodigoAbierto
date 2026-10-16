package com.campusseguro.config;

import com.campusseguro.service.AgentSyncJob;
import com.campusseguro.service.AlertProcessingService;
import com.campusseguro.service.AlertSyncJob;
import com.campusseguro.service.BuildingService;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Se ejecuta al desplegar y al detener la aplicación en Tomcat.
 *
 * @author rodrigonaranjo
 */
@WebListener
public class Inicio implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(Inicio.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Las gráficas se dibujan sin pantalla (no se abren ventanas Swing)
        System.setProperty("java.awt.headless", "true");
        try {
            BaseDatos.crearTablas();
            BuildingService.cargarEdificios();
            LOGGER.info("Base de datos lista");
        } catch (SQLException e) {
            LOGGER.severe("Error preparando la base de datos: " + e.getMessage());
        }
        if (Config.get("LDAP_URL") == null) {
            LOGGER.warning("LDAP_URL no está configurada: las alertas quedarán con ERROR_LDAP");
        }
        AgentSyncJob.iniciar();
        AlertProcessingService.iniciar(); // antes que AlertSyncJob, que le manda alertas
        AlertSyncJob.iniciar();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        AgentSyncJob.detener();
        AlertSyncJob.detener();
        AlertProcessingService.detener();
    }
}
