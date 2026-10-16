package com.campusseguro.integration;

import com.campusseguro.config.Config;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Consulta al indexador de Wazuh (OpenSearch) las alertas de fallos de
 * autenticación. Solo lectura: no se modifica nada en Wazuh.
 */
public class OpenSearchClient {

    private static final Duration TIEMPO_LIMITE = Duration.ofSeconds(20);
    private static final int TAMANO_PAGINA = 500;
    private static final int MAXIMO_REGISTROS = 10000; // límite habitual de OpenSearch

    private final String url;
    private final String credenciales;
    private final String grupo;
    private final HttpClient cliente;

    public OpenSearchClient(String url, String usuario, String password) {
        this.url = url.replaceAll("/+$", "");
        this.credenciales = Base64.getEncoder()
                .encodeToString((usuario + ":" + password).getBytes(StandardCharsets.UTF_8));

        // Grupo de reglas a consultar; se puede cambiar en campus.properties
        String g = Config.get("ALERTAS_GRUPO");
        this.grupo = (g == null || g.isBlank()) ? "authentication_failed" : g;

        this.cliente = HttpClient.newBuilder()
                .sslContext(TrustAllSSLSocketFactory.contexto())
                .connectTimeout(TIEMPO_LIMITE)
                .build();
    }

    // Alertas del grupo en los últimos cinco minutos, de 500 en 500
    public JsonArray consultarAlertasRecientes() throws IOException, InterruptedException {
        String hasta = Instant.now().toString();
        String desde = Instant.now().minus(Duration.ofMinutes(5)).toString();

        JsonArray alertas = new JsonArray();

        while (alertas.size() < MAXIMO_REGISTROS) {
            String consulta = """
                {"size": %d, "from": %d,
                 "sort": [{"timestamp": "asc"}],
                 "_source": ["timestamp", "id", "manager.name", "agent.id",
                             "rule.id", "rule.description", "rule.level",
                             "data.srcuser", "data.dstuser", "data.win.eventdata.targetUserName"],
                 "query": {"bool": {"filter": [
                     {"term": {"rule.groups": %s}},
                     {"range": {"timestamp": {"gte": "%s", "lte": "%s"}}}]}}}
                """.formatted(TAMANO_PAGINA, alertas.size(), new Gson().toJson(grupo), desde, hasta);

            HttpRequest peticion = HttpRequest.newBuilder(URI.create(url + "/wazuh-alerts-*/_search"))
                    .header("Authorization", "Basic " + credenciales)
                    .header("Content-Type", "application/json")
                    .timeout(TIEMPO_LIMITE)
                    .POST(HttpRequest.BodyPublishers.ofString(consulta, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> respuesta = cliente.send(peticion,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (respuesta.statusCode() != 200) {
                throw new IOException("Error consultando OpenSearch (HTTP " + respuesta.statusCode() + ")");
            }

            JsonObject resultado = JsonParser.parseString(respuesta.body()).getAsJsonObject();
            JsonArray hits = resultado.getAsJsonObject("hits").getAsJsonArray("hits");
            alertas.addAll(hits);

            if (hits.size() < TAMANO_PAGINA) {
                break;
            }
        }
        return alertas;
    }
}
