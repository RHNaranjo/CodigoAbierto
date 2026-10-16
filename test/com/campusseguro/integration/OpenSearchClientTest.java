package com.campusseguro.integration;

import com.google.gson.JsonArray;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Prueba el cliente contra un indexador falso en localhost.
 */
public class OpenSearchClientTest {

    private String ruta;
    private String autorizacion;
    private String consulta;

    @Test
    public void consultaPorGrupoConCredenciales() throws Exception {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/", ex -> {
            ruta = ex.getRequestURI().getPath();
            autorizacion = ex.getRequestHeaders().getFirst("Authorization");
            consulta = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            byte[] cuerpo = "{\"hits\":{\"hits\":[{\"_source\":{\"id\":\"1.1\"}}]}}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, cuerpo.length);
            ex.getResponseBody().write(cuerpo);
            ex.close();
        });
        servidor.start();

        try {
            String url = "http://localhost:" + servidor.getAddress().getPort();
            JsonArray alertas = new OpenSearchClient(url, "u", "p").consultarAlertasRecientes();

            assertEquals(1, alertas.size());
            assertEquals("/wazuh-alerts-*/_search", ruta);
            assertEquals("Basic dTpw", autorizacion); // base64 de "u:p"
            assertTrue(consulta.contains("\"authentication_failed\""));
            assertTrue(consulta.contains("data.srcuser"));
        } finally {
            servidor.stop(0);
        }
    }
}
