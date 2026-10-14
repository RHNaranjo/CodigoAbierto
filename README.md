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

## Requisitos

- Java 21, NetBeans y Tomcat 11 registrado en NetBeans
- Wazuh manager con al menos un agente
- OpenLDAP de laboratorio

## Configuración

Copiar `campus.properties.example` a `~/CampusSeguro/campus.properties` y llenar los datos de la API de Wazuh. Ese archivo no se sube a GitHub; las variables de entorno con el mismo nombre tienen prioridad.

## Ejecutar

Abrir el proyecto en NetBeans y ejecutar (Run). Se abre http://localhost:8080/CampusSeguro.

- La base H2 se guarda en `~/CampusSeguro/` (cambiar con `-Dcampus.db=ruta`).
- Las bibliotecas están en `lib/`.
- Los certificados de Wazuh no se validan (`TrustAllSSLSocketFactory`); solo para laboratorio.

## Endpoints

| Método | Ruta              | Función                                   |
| ------ | ----------------- | ----------------------------------------- |
| GET    | `/api/estado`     | La base responde                          |
| GET    | `/api/edificios`  | Edificios                                 |
| GET    | `/api/agentes`    | Agentes, estado, edificio y si están desactualizados |
| PUT    | `/api/agentes`    | Asignar edificio a un agente              |

Asignar el agente `001` a Laboratorios (1 Biblioteca, 2 Laboratorios, 3 Administración):

```bash
curl -X PUT "http://localhost:8080/CampusSeguro/api/agentes?id=001&edificio=2"
```

Sin `edificio` el agente queda *Sin edificio*. La sincronización con Wazuh (cada 60 s) no cambia la asignación.

## Pasos

- [x] 1. Proyecto Web (Ant) con H2 y `/api/estado`.
- [x] 2. Tablas (edificio, agente, alerta, enriquecimiento_ldap) y carga de los tres edificios al iniciar.
- [x] 3. `WazuhApiClient` (`HttpClient` + token) y sincronización de agentes cada 60 s.
- [ ] 4. `POST /api/alertas` con Gson: validar clave, deduplicar, guardar PENDIENTE y responder 202.
- [ ] 5. Adaptador `custom-campus` (shell + curl) en el manager.
- [ ] 6. Procesamiento con hilos (2 hilos, cola de 100) y recuperación de pendientes.
- [ ] 7. LDAP por JNDI: usuarios de laboratorio (LDIF) y resultados (ENCONTRADO, NO_ENCONTRADO, etc.).
- [ ] 8. Dashboard: mapa Leaflet con GeoJSON, tabla de alertas y filtros.
- [ ] 9. Gráficas PNG: alertas por edificio y por hora.
- [ ] 10. Pruebas de aceptación e instrucciones de instalación.
