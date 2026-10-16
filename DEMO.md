# Demo de CampusSeguro

Guion de unos 15 minutos para mostrar cómo se conectan Wazuh, LDAP, los hilos y el dashboard. Requisitos previos y configuración en el [README](README.md).

> En demostraciones públicas usar el **modo demostración** con datos simulados: los nombres de los equipos reales de Wazuh pueden incluir nombres de personas.

## 0. Preparar (antes de la demo)

```bash
./start
```

Abrir http://localhost:8080/CampusSeguro. En la Terminal, guardar la clave de `POST /api/alertas` para los comandos siguientes:

```bash
CLAVE=$(grep '^CAMPUS_ALERTAS_API_KEY=' ~/CampusSeguro/campus.properties | cut -d= -f2-)
URL=http://localhost:8080/CampusSeguro
```

## 1. Wazuh: agentes (API del manager, puerto 55000)

- Qué es: Wazuh es el SIEM institucional. Cada equipo tiene un **agente** que manda sus eventos al **manager**.
- La app consulta la API **solo para leer** cada 60 s (`AgentSyncJob`): nombre, estado y última sincronización. No se cambia nada en Wazuh.
- Mostrar `$URL/api/agentes`: los agentes reales y `"desactualizado": false`.
- En el dashboard: *Equipos registrados* y *Equipos activos*.

Asignar dos o tres agentes activos a edificios (tomar los `idWazuh` de la lista; 1 Biblioteca, 2 Laboratorios, 3 Administración):

```bash
curl -X PUT "$URL/api/agentes?id=015&edificio=1"
curl -X PUT "$URL/api/agentes?id=018&edificio=2"
curl -X PUT "$URL/api/agentes?id=027&edificio=3"
```

La asignación es manual (tabla agente → edificio); no se usa la IP.

## 2. Wazuh: alertas (indexador, puerto 9200)

- Las alertas se guardan en el **indexador** (OpenSearch). Cada 60 s `AlertSyncJob` pide las de fallos de autenticación (`rule.groups = authentication_failed`) de los últimos 5 minutos.
- Como Wazuh es institucional no se instala nada en el manager: la app consulta, no recibe.
- Para una **alerta real**: en un equipo propio que tenga agente de ese manager, fallar un inicio de sesión (por ejemplo, en Linux `ssh usuario_que_no_existe@localhost` varias veces). Aparece en la tabla en 1 a 2 minutos con origen REAL. Hacerlo solo en equipos propios.

## 3. Alertas simuladas y LDAP

Activar **Modo demostración** en el dashboard. Mandar alertas simuladas con distintos usuarios (cambiar `AGENTE` por uno asignado en el paso 1):

```bash
AGENTE=018
for u in ana luis pedro; do
  curl -s -X POST $URL/api/alertas -H "X-API-Key: $CLAVE" \
    -d '{"id":"demo-'$u'","manager":{"name":"wazuh"},"agent":{"id":"'$AGENTE'"},"rule":{"id":"5710","level":5,"description":"sshd: usuario inexistente"},"data":{"srcuser":"'$u'"}}'
  echo
done
```

En la tabla, columna **Directorio**:

| Cuenta | Resultado | Por qué |
|---|---|---|
| `ana` | ENCONTRADO · Ana López | Existe una vez en el LDAP |
| `luis` | IDENTIDAD_AMBIGUA | Hay dos `luis` en distintas unidades |
| `pedro` | NO_ENCONTRADO | No existe en el directorio |

Más casos:

- **Sin usuario:** quitar `"data"` del JSON → `SIN_USUARIO`.
- **Reenvío:** repetir el mismo comando → responde *ya estaba registrada* y los conteos no cambian.
- **LDAP caído:** `kill $(cat ~/CampusSeguro/ldap/slapd.pid)`, mandar otra alerta (otro `id`) → `ERROR_LDAP`, pero la alerta se ve igual. Volver a levantarlo con `./ldap/iniciar.sh`.
- **Clave incorrecta:** cambiar `$CLAVE` por otro texto → `401 No autorizado`.

El directorio de laboratorio se puede consultar directo:

```bash
$(brew --prefix openldap)/bin/ldapsearch -x -H ldap://localhost:3890 -b dc=campus,dc=local "(uid=luis)" cn mail
```

## 4. Hilos

- Cada alerta se guarda como `PENDIENTE` y se manda a una cola de 100 con **2 hilos** (`AlertProcessingService`). Cada hilo copia el edificio del agente, consulta LDAP y la marca `PROCESADA`.
- Mandar un lote para ver que no se crean más hilos:

```bash
for i in $(seq 1 20); do
  curl -s -o /dev/null -X POST $URL/api/alertas -H "X-API-Key: $CLAVE" \
    -d '{"id":"lote-'$i'","manager":{"name":"wazuh"},"agent":{"id":"'$AGENTE'"},"rule":{"id":"5503","level":10,"description":"PAM: fallo de autenticación"},"data":{"srcuser":"marta"}}'
done
```

- Si se apaga la app con alertas pendientes (`./stop`, `./start`), al arrancar las retoma.

## 5. Dashboard

- **Mapa:** color por número de alertas en el periodo (azul 0, dorado 1 a 4, guinda 5 o más). Es volumen, no riesgo.
- **Clic en un edificio:** el panel muestra equipos registrados, activos, alertas y eventos recientes; la tabla y la gráfica por hora se filtran.
- **Filtros:** edificio, periodo, nivel mínimo y modo demostración (sin él, las simuladas no cuentan).
- **Gráficas (JFreeChart, PNG):** alertas por edificio y por hora.
- **Encabezado:** hora de la última actualización (cada 15 s); aviso si Wazuh no responde.

## 6. Terminar

```bash
./stop
```

Los datos quedan en `~/CampusSeguro/campusseguro.mv.db`. Para empezar una demo desde cero: `./stop` y borrar ese archivo.
