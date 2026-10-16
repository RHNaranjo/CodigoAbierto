package com.campusseguro.integration;

import com.campusseguro.config.Config;
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

public class OpenSearchClient {

    private static final Duration TIEMPO_LIMITE = Duration.ofSeconds(20);
    private static final int TAMANO_PAGINA = 500;

    private final String url;
    private final String usuario;
    private final String password;
    private final HttpClient cliente;

    public OpenSearchClient(String url, String usuario, String password) {
        this.url = url.replaceAll("/+$", "");
        this.usuario = usuario;
        this.password = password;

        this.cliente = HttpClient.newBuilder()
                .sslContext(TrustAllSSLSocketFactory.contexto())
                .connectTimeout(TIEMPO_LIMITE)
                .build();
    }

    /**
     * Consulta las alertas de los últimos cinco minutos.
   
     */
    public JsonArray consultarAlertasRecientes()
            throws IOException, InterruptedException {

        Instant ahora = Instant.now();
        Instant desde = ahora.minus(Duration.ofMinutes(5));

        JsonArray alertas = new JsonArray();
        int desdeRegistro = 0;

        while (true) {
            JsonObject consulta = new JsonObject();
            consulta.addProperty("size", TAMANO_PAGINA);
            consulta.addProperty("from", desdeRegistro);

            JsonObject rango = new JsonObject();
            rango.addProperty("gte", desde.toString());
            rango.addProperty("lte", ahora.toString());

            JsonObject timestamp = new JsonObject();
            timestamp.add("range", new JsonObject());
            timestamp.getAsJsonObject("range").add("timestamp", rango);

            JsonObject query = new JsonObject();
            query.add("bool", new JsonObject());
            query.getAsJsonObject("bool").add("filter", new JsonArray());
            query.getAsJsonObject("bool").getAsJsonArray("filter").add(timestamp);

            consulta.add("query", query);

            JsonArray orden = new JsonArray();
            JsonObject criterioOrden = new JsonObject();
            JsonObject configuracionOrden = new JsonObject();
            configuracionOrden.addProperty("order", "asc");
            criterioOrden.add("timestamp", configuracionOrden);
            orden.add(criterioOrden);
            consulta.add("sort", orden);

            JsonArray campos = new JsonArray();
            campos.add("timestamp");
            campos.add("@timestamp");
            campos.add("id");
            campos.add("manager.name");
            campos.add("agent.id");
            campos.add("rule.id");
            campos.add("rule.description");
            campos.add("rule.level");
            consulta.add("_source", campos);

            HttpRequest peticion = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/wazuh-alerts-*/_search"))
                    .timeout(TIEMPO_LIMITE)
                    .header("Authorization", "Basic " + credenciales())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            consulta.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> respuesta = cliente.send(
                    peticion,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (respuesta.statusCode() != 200) {
                throw new IOException(
                        "Error consultando OpenSearch (HTTP "
                        + respuesta.statusCode() + ")");
            }

            JsonObject resultado = JsonParser.parseString(
                    respuesta.body()).getAsJsonObject();

            JsonArray hits = resultado
                    .getAsJsonObject("hits")
                    .getAsJsonArray("hits");

            for (int i = 0; i < hits.size(); i++) {
                alertas.add(hits.get(i));
            }

            if (hits.size() < TAMANO_PAGINA) {
                break;
            }

            desdeRegistro += hits.size();

            // Evita exceder el límite habitual de resultados de OpenSearch.
            if (desdeRegistro >= 10000) {
                break;
            }
        }

        return alertas;
    }

    private String credenciales() {
        String texto = usuario + ":" + password;

        return Base64.getEncoder().encodeToString(
                texto.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Construye el cliente utilizando la configuración de CampusSeguro.
     */
    public static OpenSearchClient desdeConfiguracion() {
        return new OpenSearchClient(
                Config.get("OPENSEARCH_URL"),
                Config.get("WAZUH_API_USER"),
                Config.get("WAZUH_API_PASSWORD")
        );
    }
}