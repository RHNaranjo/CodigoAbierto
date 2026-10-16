// Dashboard de CampusSeguro: lee la API cada 15 segundos y pinta el mapa,
// las cifras, las gráficas y la tabla. Los cálculos los hace el servidor.

var COLORES = ["#002D72", "#BA9357", "#8A1538"]; // 0, 1 a 4, 5 o más
var TAMANO = 20;

var filtros = document.getElementById("filtros");
var pagina = 0;
var resumen = null;
var capas = {};   // polígono del mapa por id de edificio

// Mapa: plano del campus (coordenadas propias, sin mapa de fondo)
var mapa = L.map("mapa", {
    crs: L.CRS.Simple,
    zoomSnap: 0, minZoom: -5,   // el plano llena el recuadro a cualquier tamaño
    zoomControl: false, dragging: false, scrollWheelZoom: false,
    doubleClickZoom: false, boxZoom: false, keyboard: false, touchZoom: false,
    attributionControl: false
});
mapa.fitBounds([[0, 0], [80, 122]]);
window.addEventListener("resize", function () {
    mapa.invalidateSize();
    mapa.fitBounds([[0, 0], [80, 122]]);
});

function nivelColor(alertas) {
    return alertas === 0 ? 0 : (alertas < 5 ? 1 : 2);
}

// Texto seguro para meter en HTML (las descripciones vienen de Wazuh)
function esc(texto) {
    var div = document.createElement("div");
    div.textContent = texto == null ? "" : texto;
    return div.innerHTML;
}

// "2026-10-16 12:10:46.123" -> "16/10 12:10"
function fecha(texto) {
    if (!texto) return "";
    return texto.substring(8, 10) + "/" + texto.substring(5, 7) + " " + texto.substring(11, 16);
}

function parametros() {
    var p = new URLSearchParams();
    p.set("edificio", filtros.edificio.value);
    p.set("horas", filtros.horas.value);
    p.set("nivel", filtros.nivel.value);
    p.set("demo", filtros.demo.checked);
    return p;
}

function api(ruta, extra) {
    var p = parametros();
    for (var k in extra) p.set(k, extra[k]);
    return fetch(ruta + "?" + p).then(function (r) { return r.json(); });
}

// Polígonos del GeoJSON; el id del edificio se une por el campo "poligono"
fetch("mapa/campus.geojson").then(function (r) { return r.json(); }).then(function (geo) {
    L.geoJSON(geo, {
        style: { color: "#fff", weight: 2, fillColor: "#ccc", fillOpacity: 1 },
        onEachFeature: function (f, capa) {
            capa.bindTooltip(f.properties.nombre, { permanent: true, direction: "center", className: "etiqueta" });
            capa.on("click", function () {
                filtros.edificio.value = capa.edificioId;
                cambiarFiltro();
            });
            capas[f.properties.poligono] = capa;
        }
    }).addTo(mapa);
    actualizar();
});

function pintarMapa() {
    resumen.edificios.forEach(function (e) {
        var capa = capas[e.poligono];
        if (!capa) return;
        capa.edificioId = e.id;
        var elegido = filtros.edificio.value === String(e.id);
        capa.setStyle({
            fillColor: COLORES[nivelColor(e.alertas)],
            color: elegido ? "#BA9357" : "#fff",
            weight: elegido ? 5 : 2
        });
    });
}

function pintarCifras() {
    var total = resumen.sinEdificio;
    resumen.edificios.forEach(function (e) { total += e.alertas; });
    document.getElementById("c-alertas").textContent = total;
    document.getElementById("c-equipos").textContent = resumen.equipos;
    document.getElementById("c-activos").textContent = resumen.activos;
    document.getElementById("c-sin").textContent = resumen.sinEdificio;

    var estado = "Actualizado " + resumen.actualizado.substring(11);
    if (resumen.agentesDesactualizados || resumen.alertasDesactualizadas) {
        estado += '<span class="aviso">Wazuh sin responder: datos desactualizados</span>';
    }
    document.getElementById("estado").innerHTML = estado;
}

// Panel derecho: el edificio elegido o todo el campus
function pintarDetalle() {
    var id = filtros.edificio.value;
    var nombre = "Todo el campus", equipos = resumen.equipos, activos = resumen.activos;
    var alertas = document.getElementById("c-alertas").textContent;

    resumen.edificios.forEach(function (e) {
        if (String(e.id) === id) {
            nombre = e.nombre; equipos = e.equipos; activos = e.activos; alertas = e.alertas;
        }
    });
    if (id === "0") {
        nombre = "Sin edificio"; equipos = "–"; activos = "–"; alertas = resumen.sinEdificio;
    }
    document.getElementById("d-nombre").textContent = nombre;
    document.getElementById("d-equipos").textContent = equipos;
    document.getElementById("d-activos").textContent = activos;
    document.getElementById("d-alertas").textContent = alertas;

    api("api/alertas", { pagina: 0, tamano: 5 }).then(function (r) {
        var html = "";
        r.alertas.forEach(function (a) {
            html += "<li>" + esc(a.descripcion || "Regla " + a.reglaId)
                  + "<small>" + fecha(a.fechaEvento || a.fechaRecepcion) + " · " + esc(a.agente || a.agenteId || "sin equipo") + "</small></li>";
        });
        document.getElementById("d-eventos").innerHTML = html || '<li class="nota">Sin eventos en el periodo.</li>';
    });
}

function pintarGraficas() {
    var p = parametros();
    p.set("t", Date.now()); // evita que el navegador muestre la imagen anterior
    document.getElementById("g-edificio").src = "graficas/alertas-por-edificio.png?" + p;
    document.getElementById("g-hora").src = "graficas/alertas-por-hora.png?" + p;
}

function pintarTabla() {
    api("api/alertas", { pagina: pagina, tamano: TAMANO }).then(function (r) {
        var html = "";
        r.alertas.forEach(function (a) {
            var ldap = a.ldap ? a.ldap.resultado + (a.ldap.nombre ? " · " + a.ldap.nombre : "") : "";
            html += "<tr>"
                + "<td>" + fecha(a.fechaEvento || a.fechaRecepcion) + "</td>"
                + "<td>" + esc(a.edificio || (a.estadoProcesamiento === "PROCESADA" ? "Sin edificio" : "Pendiente")) + "</td>"
                + "<td>" + esc(a.agente || a.agenteId) + "</td>"
                + "<td>" + esc(a.reglaId) + "</td>"
                + '<td class="' + (a.nivel >= 10 ? "alto" : "") + '">' + esc(a.nivel) + "</td>"
                + "<td>" + esc(a.descripcion) + (a.origen === "SIMULADO" ? '<span class="simulada">SIMULADA</span>' : "") + "</td>"
                + "<td>" + esc(a.usuarioEvento) + "</td>"
                + "<td>" + esc(ldap) + "</td>"
                + "</tr>";
        });
        document.querySelector("#tabla tbody").innerHTML =
            html || '<tr><td colspan="8" class="vacio">No hay alertas con estos filtros.</td></tr>';

        var desde = r.total === 0 ? 0 : pagina * TAMANO + 1;
        var hasta = Math.min((pagina + 1) * TAMANO, r.total);
        document.getElementById("rango").textContent = desde + "–" + hasta + " de " + r.total;
        document.getElementById("anterior").disabled = pagina === 0;
        document.getElementById("siguiente").disabled = hasta >= r.total;
    });
}

function actualizar() {
    api("api/edificios/resumen", {}).then(function (r) {
        resumen = r;
        pintarMapa();
        pintarCifras();
        pintarDetalle();
    });
    pintarGraficas();
    pintarTabla();
}

function cambiarFiltro() {
    pagina = 0;
    actualizar();
}

filtros.addEventListener("change", cambiarFiltro);
document.getElementById("anterior").addEventListener("click", function () { pagina--; pintarTabla(); });
document.getElementById("siguiente").addEventListener("click", function () { pagina++; pintarTabla(); });
setInterval(actualizar, 15000);
