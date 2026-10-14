package com.campusseguro.config;

import com.campusseguro.service.AgentSyncJob;
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
        try {
            BaseDatos.crearTablas();
            BuildingService.cargarEdificios();
            LOGGER.info("Base de datos lista");
        } catch (SQLException e) {
            LOGGER.severe("Error preparando la base de datos: " + e.getMessage());
        }
        AgentSyncJob.iniciar();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        AgentSyncJob.detener();
    }
}
