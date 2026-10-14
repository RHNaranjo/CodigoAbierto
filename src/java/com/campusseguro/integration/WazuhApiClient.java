package com.campusseguro.integration;

import com.campusseguro.entity.Agente;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Cliente de la API del manager de Wazuh: token y consulta de agentes.
 *
 * @author rodrigonaranjo
 */
public class WazuhApiClient {

    private static final Duration TIEMPO_LIMITE = Duration.ofSeconds(10);

    private final String url;
    private final String usuario;
    private final String password;
    private final HttpClient cliente;
    private String token;

    public WazuhApiClient(String url, String usuario, String password) {
        this.url = url;
        this.usuario = usuario;
        this.password = password;
        this.cliente = HttpClient.newBuilder()
                .sslContext(TrustAllSSLSocketFactory.contexto())
                .connectTimeout(TIEMPO_LIMITE)
                .build();
    }

    // POST /security/user/authenticate?raw=true devuelve el token en texto plano
    private void autenticar() throws IOException, InterruptedException {
        String credenciales = Base64.getEncoder()
                .encodeToString((usuario + ":" + password).getBytes(StandardCharsets.UTF_8));
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url + "/security/user/authenticate?raw=true"))
                .header("Authorization", "Basic " + credenciales)
                .timeout(TIEMPO_LIMITE)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
        if (respuesta.statusCode() != 200) {
            throw new IOException("Wazuh rechazó la autenticación (HTTP " + respuesta.statusCode() + ")");
        }
        token = respuesta.body().trim();
    }

    // GET con el token. Si expiró (401), se pide uno nuevo y se reintenta una vez.
    private String get(String ruta) throws IOException, InterruptedException {
        if (token == null) {
            autenticar();
        }
        HttpResponse<String> respuesta = cliente.send(peticionGet(ruta), HttpResponse.BodyHandlers.ofString());
        if (respuesta.statusCode() == 401) {
            autenticar();
            respuesta = cliente.send(peticionGet(ruta), HttpResponse.BodyHandlers.ofString());
        }
        if (respuesta.statusCode() != 200) {
            throw new IOException("Error consultando " + ruta + " (HTTP " + respuesta.statusCode() + ")");
        }
        return respuesta.body();
    }

    private HttpRequest peticionGet(String ruta) {
        return HttpRequest.newBuilder(URI.create(url + ruta))
                .header("Authorization", "Bearer " + token)
                .timeout(TIEMPO_LIMITE)
                .GET()
                .build();
    }

    // GET /agents, de 500 en 500 hasta tenerlos todos
    public List<Agente> consultarAgentes() throws IOException, InterruptedException {
        List<Agente> agentes = new ArrayList<>();
        int total = 1;

        while (agentes.size() < total) {
            String json = get("/agents?select=id,name,status&limit=500&offset=" + agentes.size());
            JsonObject data = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("data");
            total = data.get("total_affected_items").getAsInt();

            JsonArray items = data.getAsJsonArray("affected_items");
            if (items.isEmpty()) {
                break;
            }
            for (JsonElement e : items) {
                JsonObject a = e.getAsJsonObject();
                agentes.add(new Agente(a.get("id").getAsString(), a.get("name").getAsString(), a.get("status").getAsString()));
            }
        }
        return agentes;
    }
}
