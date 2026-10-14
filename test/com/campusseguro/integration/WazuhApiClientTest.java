package com.campusseguro.integration;

import com.campusseguro.entity.Agente;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Prueba el cliente contra un servidor falso de Wazuh en localhost.
 */
public class WazuhApiClientTest {

    private int tokensPedidos = 0;

    private static void responder(HttpExchange ex, int codigo, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(codigo, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    // El primer token "expira" (401) y los agentes vienen en dos páginas
    @Test
    public void renuevaTokenYPagina() throws Exception {
        HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/security/user/authenticate", ex -> {
            tokensPedidos++;
            responder(ex, 200, "token" + tokensPedidos);
        });
        servidor.createContext("/agents", ex -> {
            if (!ex.getRequestHeaders().getFirst("Authorization").equals("Bearer token2")) {
                responder(ex, 401, "{}");
            } else if (ex.getRequestURI().getQuery().contains("offset=0")) {
                responder(ex, 200, "{\"data\":{\"total_affected_items\":2,\"affected_items\":[{\"id\":\"000\",\"name\":\"manager\",\"status\":\"active\"}]}}");
            } else {
                responder(ex, 200, "{\"data\":{\"total_affected_items\":2,\"affected_items\":[{\"id\":\"001\",\"name\":\"pc-lab\",\"status\":\"disconnected\"}]}}");
            }
        });
        servidor.start();

        try {
            String url = "http://localhost:" + servidor.getAddress().getPort();
            List<Agente> agentes = new WazuhApiClient(url, "u", "p").consultarAgentes();

            assertEquals(2, tokensPedidos);
            assertEquals(2, agentes.size());
            assertEquals("001", agentes.get(1).getIdWazuh());
            assertEquals("disconnected", agentes.get(1).getEstado());
        } finally {
            servidor.stop(0);
        }
    }
}
