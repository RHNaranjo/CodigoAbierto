package com.campusseguro.config;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

/**
 * Lee la configuración: primero de propiedades del sistema (-D, útil en
 * pruebas), luego de variables de entorno y, si no existen, del archivo
 * ~/CampusSeguro/campus.properties (fuera del repositorio).
 *
 * @author rodrigonaranjo
 */
public class Config {

    private static final Properties archivo = new Properties();

    static {
        File f = new File(System.getProperty("user.home"), "CampusSeguro/campus.properties");
        if (f.exists()) {
            try (FileReader lector = new FileReader(f)) {
                archivo.load(lector);
            } catch (IOException e) {
                System.err.println("No se pudo leer " + f + ": " + e.getMessage());
            }
        }
    }

    public static String get(String nombre) {
        String valor = System.getProperty(nombre);
        if (valor == null) {
            valor = System.getenv(nombre);
        }
        return valor != null ? valor : archivo.getProperty(nombre);
    }
}
