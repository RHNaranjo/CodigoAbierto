# CampusSeguro

Radar de seguridad por edificios: recibe alertas de Wazuh, las enriquece con LDAP y las muestra en un mapa (Leaflet) y gráficas (JFreeChart). Instrucciones originales en `CampusSeguro.md`.

## Cambios respecto a las instrucciones

Para usar *Java with Ant* y seguir el estilo de los proyectos de NetBeans (ProcesaEventos, LDAP, Hilos, Graficas):

| Instrucciones            | Este proyecto                                   |
| ------------------------ | ----------------------------------------------- |
| Maven + Spring Boot      | Ant, Web Application en Tomcat 11 con servlets  |
| Spring Data JPA          | JDBC con H2 en modo archivo                     |
| Spring LDAP              | JNDI                                            |
| @Async / @Scheduled      | `ThreadPoolExecutor` / `ScheduledExecutorService` |
| Thymeleaf                | HTML + JavaScript estático                      |
| Integración `custom-campus` en el manager | Consulta al indexador (OpenSearch) cada 60 s |
| Paquete `repository`     | No se usa: los servicios hacen el JDBC. `dto` solo tiene `Filtro` |
| Mapa: verde, amarillo, rojo | Azul (0), dorado (1 a 4), guinda (5 o más), colores del proyecto |

Wazuh es institucional: no se le hace ningún cambio (usuarios, permisos ni integraciones). Por eso las alertas se **consultan** al indexador en lugar de recibirse con `custom-campus`, y solo se usan credenciales que ya existen.

## Requisitos

- Java 21, NetBeans y Tomcat 11 (`brew install tomcat`)
- Acceso de lectura a Wazuh: API (puerto 55000) e indexador (puerto 9200)
- OpenLDAP de laboratorio: `brew install openldap` (macOS)

## Cómo correrlo

1. **Abrir el proyecto** en NetBeans (File → Open Project → carpeta `CodigoAbierto`). Si pide servidor, elegir Tomcat 11.
2. **Conseguir las credenciales de Wazuh** que ya existan (no crear usuarios en Wazuh). Si las de la API también sirven en el indexador (puerto 9200), basta con ellas.
3. **Crear la configuración** (no se sube a GitHub):

   ```bash
   mkdir -p ~/CampusSeguro
   cp campus.properties.example ~/CampusSeguro/campus.properties
   ```

   Editar `~/CampusSeguro/campus.properties` con las URL, usuario, contraseña y la clave de `POST /api/alertas`. Las variables de entorno con el mismo nombre tienen prioridad.
4. **Levantar el LDAP de laboratorio** (cada vez que se reinicie la máquina): `./ldap/iniciar.sh`. Queda en `ldap://localhost:3890` con los usuarios ficticios de `ldap/usuarios.ldif`: `ana` y `marta` (ENCONTRADO), `luis` repetido en dos unidades (IDENTIDAD_AMBIGUA); cualquier otro da NO_ENCONTRADO.
5. **Ejecutar**: clic derecho en el proyecto → Run. Se abre http://localhost:8080/CampusSeguro. La configuración solo se lee al arrancar: si se cambia, volver a ejecutar.
6. **Comprobar** en el navegador:
   - `/api/estado`: la base responde (crea `~/CampusSeguro/campusseguro.mv.db`).
   - `/api/edificios`: los tres edificios.
   - `/api/agentes`: los agentes de Wazuh (aparecen tras la primera sincronización, unos segundos; `desactualizado: true` indica que Wazuh no respondió).
   - `/api/alertas`: las alertas guardadas (`origen` REAL, del indexador; o SIMULADO, enviadas por POST), con su edificio y resultado LDAP.
7. **Asignar edificios** a los agentes (ver Endpoints).

**Sin NetBeans:** `./start` levanta el LDAP, compila y arranca un Tomcat propio en `~/CampusSeguro/tomcat` (http://localhost:8080/CampusSeguro); `./stop` detiene todo. No correr al mismo tiempo que el Tomcat de NetBeans (mismo puerto y misma base). Para una demostración guiada, ver [DEMO.md](DEMO.md).

Sin `WAZUH_API_URL` u `OPENSEARCH_URL` la app arranca, pero no sincroniza agentes o alertas; sin `LDAP_URL` las alertas quedan con `ERROR_LDAP` (se avisa en la consola de Tomcat).

Enviar una alerta simulada:

```bash
curl -X POST http://localhost:8080/CampusSeguro/api/alertas \
  -H "X-API-Key: LA_CLAVE" \
  -d '{"id":"s1","manager":{"name":"wazuh"},"agent":{"id":"001"},"rule":{"id":"5710","level":5,"description":"Prueba"},"data":{"srcuser":"CAMPUS\\ana"}}'
```

### Cómo se procesa una alerta

1. `AlertSyncJob` (cada 60 s) o `POST /api/alertas` la guardan como `PENDIENTE` (si ya existía, no se duplica).
2. Se manda a `AlertProcessingService`: 2 hilos y una cola de 100. Si la cola está llena, sigue `PENDIENTE`.
3. Cada hilo copia a la alerta el edificio del agente (sin asignación: *Sin edificio*), busca el usuario en LDAP y la marca `PROCESADA`.
4. Al arrancar y cada 60 s se vuelven a encolar las `PENDIENTE` (reinicios o cola llena).

LDAP (`LdapUserService`, JNDI): busca por `uid` con filtro escapado (`(uid={0})`), lee `uid`, `cn`, `mail` y `departmentNumber`, con tiempo límite de 5 s. Única normalización: `DOMINIO\usuario` → `usuario`. Resultados: `ENCONTRADO`, `NO_ENCONTRADO`, `SIN_USUARIO`, `ERROR_LDAP`, `IDENTIDAD_AMBIGUA`. Si LDAP falla, la alerta se muestra igual. La cuenta de la alerta no es la responsable confirmada del evento.

### Pruebas

Clic derecho en el proyecto → Test (JUnit).

### Notas

- La base H2 está en `~/CampusSeguro/` (cambiar con `-Dcampus.db=ruta`).
- Las bibliotecas están en `lib/`.
- Los certificados de Wazuh no se validan (`TrustAllSSLSocketFactory`); solo para laboratorio.

## Endpoints

| Método | Ruta              | Función                                   |
| ------ | ----------------- | ----------------------------------------- |
| GET    | `/api/estado`     | La base responde                          |
| GET    | `/api/edificios`  | Edificios                                 |
| GET    | `/api/agentes`    | Agentes, estado, edificio y si están desactualizados |
| PUT    | `/api/agentes`    | Asignar edificio a un agente              |
| GET    | `/api/alertas`    | Alertas con edificio y resultado LDAP, filtradas y por páginas |
| GET    | `/api/edificios/resumen` | Conteos por edificio para el mapa y estado de Wazuh |
| GET    | `/mapa/campus.geojson` | Polígonos de los edificios |
| GET    | `/graficas/alertas-por-edificio.png` | Barras del periodo |
| GET    | `/graficas/alertas-por-hora.png` | Línea de las últimas 24 h |
| POST   | `/api/alertas`    | Guardar una alerta **simulada** (cabecera `X-API-Key`, máx. 100 KB) |

Filtros de `GET /api/alertas`, del resumen y de las gráficas: `horas` (periodo, 24 por defecto), `nivel` (mínimo), `demo=true` (incluye simuladas), `edificio` (1, 2, 3; 0 = Sin edificio). La lista acepta además `pagina` y `tamano` (20 por defecto).

El dashboard (`/`) los usa todos y se actualiza cada 15 s. Leaflet está en `web/lib` para que funcione sin internet.

Asignar el agente `001` a Laboratorios (1 Biblioteca, 2 Laboratorios, 3 Administración):

```bash
curl -X PUT "http://localhost:8080/CampusSeguro/api/agentes?id=001&edificio=2"
```

Sin `edificio` el agente queda *Sin edificio*. La sincronización con Wazuh (cada 60 s) no cambia la asignación.

## Pasos

- [x] 1. Proyecto Web (Ant) con H2 y `/api/estado`.
- [x] 2. Tablas (edificio, agente, alerta, enriquecimiento_ldap) y carga de los tres edificios al iniciar.
- [x] 3. `WazuhApiClient` (`HttpClient` + token) y sincronización de agentes cada 60 s.
- [x] 4. `/api/alertas`: `POST` (clave, deduplicación, PENDIENTE, 202) y `GET`.
- [x] 5. En lugar de `custom-campus`: `AlertSyncJob` consulta el indexador cada 60 s.
- [x] 5b. Ajustes a los pasos 4 y 5.
- [x] 6. Procesamiento con hilos (2 hilos, cola de 100): asignar edificio, marcar procesada y recuperar pendientes.
- [x] 7. LDAP por JNDI con un OpenLDAP local de laboratorio (LDIF con 3 usuarios) y sus resultados (ENCONTRADO, NO_ENCONTRADO, etc.).
- [x] 8. Dashboard: mapa Leaflet con GeoJSON, tabla de alertas (con paginación y filtros) y modo demostración.
- [x] 9. Gráficas PNG con JFreeChart: alertas por edificio y por hora.
- [ ] 10. Pruebas de aceptación, evidencia e instrucciones de instalación.

### Pendiente

- Proteger con autenticación el dashboard y `PUT /api/agentes` (sección 14 de las instrucciones).

### Por verificar con datos reales

- ~~Que el grupo `authentication_failed` aparezca en `rule.groups`~~: verificado con una consulta directa al indexador (hay alertas con ese grupo); falta ver una llegar a la app.
- Qué campo trae el usuario (`data.srcuser`, `data.dstuser` o `data.win.eventdata.targetUserName`); se ajusta en `AlertService.CAMPOS_USUARIO`.
- Si la app está apagada más de 5 minutos, las alertas de ese lapso no se recuperan (la consulta mira solo los últimos 5 min).
