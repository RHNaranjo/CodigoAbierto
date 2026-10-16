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

Wazuh es institucional: no se le hace ningún cambio (usuarios, permisos ni integraciones). Por eso las alertas se **consultan** al indexador en lugar de recibirse con `custom-campus`, y solo se usan credenciales que ya existen.

## Requisitos

- Java 21, NetBeans y Tomcat 11 registrado en NetBeans
- Acceso de lectura a Wazuh: API (puerto 55000) e indexador (puerto 9200)
- OpenLDAP de laboratorio

## Cómo correrlo

1. **Abrir el proyecto** en NetBeans (File → Open Project → carpeta `CodigoAbierto`). Si pide servidor, elegir Tomcat 11.
2. **Conseguir las credenciales de Wazuh** que ya existan (no crear usuarios en Wazuh): una para la API (puerto 55000) y otra para el indexador (puerto 9200). El usuario del dashboard es del indexador.
3. **Crear la configuración** (no se sube a GitHub):

   ```bash
   mkdir -p ~/CampusSeguro
   cp campus.properties.example ~/CampusSeguro/campus.properties
   ```

   Editar `~/CampusSeguro/campus.properties` con las URL, usuario, contraseña y la clave de `POST /api/alertas`. Las variables de entorno con el mismo nombre tienen prioridad.
4. **Ejecutar**: clic derecho en el proyecto → Run. Se abre http://localhost:8080/CampusSeguro. La configuración solo se lee al arrancar: si se cambia, volver a ejecutar.
5. **Comprobar** en el navegador:
   - `/api/estado`: la base responde (crea `~/CampusSeguro/campusseguro.mv.db`).
   - `/api/edificios`: los tres edificios.
   - `/api/agentes`: los agentes de Wazuh (aparecen tras la primera sincronización, unos segundos; `desactualizado: true` indica que Wazuh no respondió).
   - `/api/alertas`: las alertas guardadas (`origen` REAL, del indexador; o SIMULADO, enviadas por POST).
6. **Asignar edificios** a los agentes (ver Endpoints).

Sin `WAZUH_API_URL` o sin `OPENSEARCH_URL`, `OPENSEARCH_USER` y `OPENSEARCH_PASSWORD`, la app arranca, pero no sincroniza agentes o alertas (se avisa en la consola de Tomcat).

Enviar una alerta simulada:

```bash
curl -X POST http://localhost:8080/CampusSeguro/api/alertas \
  -H "X-API-Key: LA_CLAVE" \
  -d '{"id":"s1","manager":{"name":"wazuh"},"agent":{"id":"001"},"rule":{"id":"5710","level":5,"description":"Prueba"},"data":{"srcuser":"ana"}}'
```

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
| GET    | `/api/alertas`    | Alertas guardadas                         |
| POST   | `/api/alertas`    | Guardar una alerta **simulada** (cabecera `X-API-Key`, máx. 100 KB) |

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
- [ ] 6. Procesamiento con hilos (2 hilos, cola de 100): asignar edificio, marcar procesada y recuperar pendientes.
- [ ] 7. LDAP por JNDI con un OpenLDAP local de laboratorio (LDIF con 3 usuarios) y sus resultados (ENCONTRADO, NO_ENCONTRADO, etc.).
- [ ] 8. Dashboard: mapa Leaflet con GeoJSON, tabla de alertas (con paginación y filtros) y alertas simuladas.
- [ ] 9. Gráficas PNG: alertas por edificio y por hora.
- [ ] 10. Pruebas de aceptación e instrucciones de instalación.

### Por verificar con datos reales

- Que el grupo `authentication_failed` aparezca en `rule.groups` de las alertas del indexador (si no, cambiar `ALERTAS_GRUPO`).
- Qué campo trae el usuario (`data.srcuser`, `data.dstuser` o `data.win.eventdata.targetUserName`); se ajusta en `AlertService.CAMPOS_USUARIO`.
- Si la app está apagada más de 5 minutos, las alertas de ese lapso no se recuperan (la consulta mira solo los últimos 5 min).
