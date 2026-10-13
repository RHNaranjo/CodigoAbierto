# CampusSeguro: radar de seguridad por edificios

## 1. Objetivo del proyecto

Desarrollar una aplicación web sencilla con backend en **Java** que permita visualizar las alertas de seguridad de un campus mediante un mapa temático por edificios. La aplicación deberá recibir alertas mediante una integración de Wazuh, consultar la API del servidor Wazuh, enriquecer los eventos asociados a usuarios mediante LDAP y presentar estadísticas con JFreeChart.

El procesamiento de alertas y las consultas a servicios externos deberán ejecutarse mediante hilos administrados por la aplicación.

**NOTA:** ES INDISPENSABLE QUE EL CÓDIGO SEA LO MÁS SENCILLO POSIBLE, SIN REDUNDANCIA NI ALGORITMOS RIMBOMBANTES, Y QUE UTILICE LOS CÓDIGOS DE LA CARBETA NetbeansProjects COMO BASE. NO APLICAR PATRONES DE DISEÑO DEMASIADO COMPLEJOS QUE NO APORTEN BENEFICIOS REALES. EVITAR LA SOBRE INGENIERÍA.

## 2. Alcance obligatorio

Implementar un prototipo con:

- Tres edificios: Biblioteca, Laboratorios y Administración.
- Al menos un agente Wazuh conectado y asignado a uno de los edificios.
- Una tabla de asignación de agentes a edificios; no se utilizará geolocalización de direcciones IP.
- Un tipo inicial de alerta: fallos de autenticación.
- Un directorio LDAP de laboratorio con al menos tres usuarios ficticios.
- Un mapa temático, una tabla de alertas y dos gráficas.
- Almacenamiento persistente de alertas recibidas.
- Procesamiento asíncrono y sincronización periódica de agentes.

Para verificar el mapa completo se podrán cargar eventos de prueba en los otros edificios. Estos deberán identificarse como simulados y distinguirse de las alertas recibidas realmente desde Wazuh.

Quedan fuera del alcance inicial el bloqueo automático de cuentas, las respuestas activas, la geolocalización pública y la consulta de históricos al indexador.

## 3. Tecnologías y herramientas

| Componente           | Herramienta seleccionada                              | Uso en el proyecto                                            |
| -------------------- | ----------------------------------------------------- | ------------------------------------------------------------- |
| Lenguaje del backend | Java 21                                               | Lógica de negocio, consultas HTTP, LDAP y concurrencia        |
| Framework web        | Spring Boot, en una versión compatible con Java 21    | Controladores REST, configuración e inyección de dependencias |
| Construcción         | Maven                                                 | Gestión de dependencias, pruebas y empaquetado                |
| Interfaz web         | Thymeleaf, HTML, CSS y JavaScript                     | Dashboard servido por el backend                              |
| Mapa                 | Leaflet y un archivo GeoJSON                          | Polígonos de edificios y colores temáticos                    |
| Gráficas             | JFreeChart                                            | Generación de gráficas PNG desde Java                         |
| Persistencia         | H2 en modo archivo y Spring Data JPA                  | Almacenamiento del prototipo                                  |
| Directorio           | OpenLDAP de laboratorio y Spring LDAP                 | Búsquedas de usuarios mediante LdapTemplate                   |
| Seguridad de eventos | Wazuh manager y agente                                | Detección y envío de alertas                                  |
| Cliente HTTP         | java.net.http.HttpClient                              | Autenticación y consultas a la API Wazuh                      |
| Concurrencia         | ThreadPoolTaskExecutor y planificación con @Scheduled | Procesamiento de eventos y sincronización periódica           |
| Pruebas              | JUnit, Spring Boot Test y curl o Postman              | Verificación de servicios y endpoints                         |
| Control de versiones | Git                                                   | Historial del proyecto                                        |

El backend deberá estar implementado en Java. JavaScript se limitará a las funciones del navegador y al mapa. Leaflet utiliza GeoJSON para construir mapas coropléticos; JFreeChart permite escribir gráficas PNG en un flujo de salida. [web:22][web:13]

La integración en el manager utilizará un adaptador mínimo de shell con curl. Este adaptador únicamente reenviará el JSON; la lógica del proyecto permanecerá en Java.

## 4. Arquitectura y flujo de datos

```text
Equipo con agente Wazuh
        |
        v
Wazuh manager -- integración custom-campus --> POST /api/alertas
                                                |
                                                v
                                    Validación y persistencia inicial
                                                |
                                                v
                                     Cola de procesamiento Java
                                                |
                              +-----------------+----------------+
                              |                                  |
                              v                                  v
                    Asignación de edificio              Consulta LDAP si aplica
                              |                                  |
                              +-----------------+----------------+
                                                |
                                                v
                                        Actualización en H2
                                                |
                              +-----------------+----------------+
                              |                 |                |
                              v                 v                v
                         Mapa Leaflet      Tabla de alertas   JFreeChart

Tarea periódica Java --> API Wazuh --> agentes y estados --> H2
```

Separar los dos mecanismos de comunicación:

1. Consultar la API del servidor Wazuh para sincronizar agentes y estados.
2. Recibir nuevas alertas mediante la integración personalizada.

No presentar la consulta de agentes como una consulta del histórico de alertas. Si se añade ese histórico posteriormente, se deberá implementar por separado la API del indexador, que permite consultar los índices de alertas. [web:21][web:24]

## 5. Organización del backend

Crear los siguientes paquetes:

```text
com.campusseguro
├── config
├── controller
├── dto
├── entity
├── repository
├── service
└── integration
```

Implementar, como mínimo, los siguientes componentes:

- WazuhApiClient: autenticación, renovación del token y consulta de agentes.
- AlertController: recepción y validación de alertas.
- AlertProcessingService: normalización y procesamiento asíncrono.
- LdapUserService: consulta del directorio y normalización del identificador.
- BuildingService: asociación entre agente y edificio.
- DashboardService: conteos y filtros temporales.
- ChartService: creación de gráficas con JFreeChart.
- AgentSyncJob: sincronización periódica de agentes.

## 6. Consulta de la API Wazuh

Configurar mediante variables de entorno:

```properties
WAZUH_API_URL=https://SERVIDOR_WAZUH:55000
WAZUH_API_USER=usuario_de_consulta
WAZUH_API_PASSWORD=secreto
```

Obtener el token con:

```http
POST /security/user/authenticate?raw=true
Authorization: Basic <credenciales codificadas>
```

Consultar los agentes con:

```http
GET /agents
Authorization: Bearer <token>
```

La API utiliza JWT para las solicitudes autenticadas. Implementar la renovación del token cuando expire y manejar las respuestas de autenticación fallida. [web:36][web:37]

Sincronizar agentes cada 60 segundos. Guardar su identificador como texto, su nombre, estado y fecha de última sincronización. Implementar paginación cuando la consulta lo requiera.

No sobrescribir la asignación manual del edificio durante la sincronización. Si Wazuh no está disponible, conservar los datos previos e indicar que están desactualizados.

## 7. Integración personalizada de alertas

En el servidor que ejecuta el manager, crear:

```text
/var/ossec/integrations/custom-campus
```

Añadir dentro del bloque ossec_config existente de /var/ossec/etc/ossec.conf:

```xml
<integration>
  <name>custom-campus</name>
  <hook_url>https://SERVIDOR_CAMPUS/api/alertas</hook_url>
  <api_key>SECRETO_DE_INTEGRACION</api_key>
  <group>authentication_failed</group>
  <alert_format>json</alert_format>
</integration>
```

El grupo authentication_failed es un valor inicial de laboratorio: antes de utilizarlo, comprobar que figure en rule.groups de una alerta real de la instalación. Si no coincide, sustituirlo por el grupo real o filtrar por los identificadores de regla observados. Los filtros mal definidos pueden impedir el envío de alertas. [web:6]

El adaptador deberá:

1. Leer el archivo de alerta recibido como primer argumento.
2. Obtener la clave de integración del segundo argumento.
3. Obtener la URL del tercer argumento.
4. Enviar el JSON con HTTP POST, Content-Type: application/json y X-Integration-Key.
5. Configurar tiempos límite y registrar errores sin imprimir secretos.
6. Devolver un código de salida distinto de cero si falla el envío.

Wazuh exige que el nombre de la integración personalizada comience con custom-, que el ejecutable coincida con el nombre configurado y que tenga permisos apropiados. La documentación describe los tres argumentos anteriores. [web:6]

Aplicar en el manager Linux:

```bash
sudo chmod 750 /var/ossec/integrations/custom-campus
sudo chown root:wazuh /var/ossec/integrations/custom-campus
sudo systemctl restart wazuh-manager
```

Estos cambios se realizan en el manager, no en el equipo que solo ejecuta el agente. Adaptar el reinicio al método de despliegue si se utiliza Docker. [web:6]

## 8. Recepción y procesamiento de alertas

Implementar POST /api/alertas para recibir el JSON original de Wazuh.

Extraer, cuando estén disponibles:

- id y timestamp.
- agent.id y agent.name.
- rule.id, rule.level y rule.description.
- Identificador de usuario, según el formato real del evento.

Inspeccionar una alerta real antes de programar la extracción del usuario. No asumir que todos los sistemas operativos o decodificadores usan el mismo campo. Wazuh recomienda revisar las alertas para conocer su estructura antes de desarrollar la integración. [web:6]

Validar la clave de integración y los campos mínimos. Guardar primero la alerta con estado PENDIENTE y después solicitar su procesamiento asíncrono. Devolver 202 Accepted solo cuando la recepción haya quedado persistida.

Utilizar el identificador de alerta y el identificador del manager como clave de deduplicación. Un reenvío de la misma alerta no deberá incrementar los conteos.

Si el agente no tiene edificio asignado, conservar el evento bajo la categoría Sin edificio; no descartarlo.

## 9. Consulta LDAP

Configurar un directorio de laboratorio con usuarios ficticios y un atributo uid como identificador de búsqueda. Consultar únicamente:

- uid: identificador.
- cn: nombre.
- mail: correo.
- departmentNumber: departamento, si está definido.

La búsqueda solo deberá ejecutarse cuando la alerta contenga una identidad utilizable. Usar filtros construidos con escape de valores mediante la biblioteca LDAP; no concatenar directamente el texto recibido en un filtro.

Java puede acceder a LDAP mediante JNDI; en este proyecto Spring LDAP encapsulará esa comunicación. [web:23][web:28]

Registrar por separado los resultados:

- ENCONTRADO.
- NO_ENCONTRADO.
- SIN_USUARIO.
- ERROR_LDAP.
- IDENTIDAD_AMBIGUA.

Normalizar formatos como DOMINIO\usuario solamente mediante reglas explícitas. No consultar cuentas locales como si necesariamente fueran cuentas del directorio.

La cuenta mencionada en una alerta no deberá presentarse como responsable confirmado del evento.

## 10. Hilos y tareas periódicas

Configurar un ThreadPoolTaskExecutor con dos hilos de trabajo y una cola limitada de 100 tareas. Procesar mediante @Async en un servicio separado del controlador.

Ejecutar cada 60 segundos la sincronización de agentes mediante @Scheduled. Implementar otra tarea para recuperar alertas PENDIENTES que no pudieron entrar en la cola o quedaron pendientes tras un reinicio.

No crear un hilo nuevo por cada evento. No compartir contextos LDAP mutables entre trabajadores. Definir tiempos límite para HTTP y LDAP, registrar errores y evitar que la indisponibilidad de LDAP impida mostrar la alerta básica.

Probar el procesamiento con un lote de 20 alertas, incluyendo eventos repetidos y eventos sin usuario.

## 11. Modelo de datos

| Entidad             | Campos mínimos                                                                                                                                            |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Edificio            | id, nombre, identificador del polígono GeoJSON                                                                                                            |
| Agente              | idWazuh, nombre, estado, edificioId, ultimaSincronizacion                                                                                                 |
| Alerta              | idInterno, managerId, idWazuh, fechaEvento, fechaRecepcion, agenteId, edificioId, reglaId, nivel, descripcion, usuarioEvento, estadoProcesamiento, origen |
| EnriquecimientoLDAP | alertaId, resultado, uid, nombre, correo, departamento, fechaConsulta                                                                                     |

Permitir valores nulos cuando un evento no contenga usuario o edificio. Guardar la asignación de edificio de cada alerta al procesarla para que una reasignación futura del agente no modifique inadvertidamente el mapa histórico.

Usar origen REAL o SIMULADO. Los datos simulados se excluirán de los indicadores normales, salvo que el usuario active el modo demostración.

## 12. Mapa temático e interfaz

Crear un GeoJSON con tres polígonos de edificios. Para mantener el prototipo sencillo, se permite un campus ficticio con geometrías previamente definidas.

Utilizar Leaflet para mostrar los polígonos, su leyenda y ventanas informativas. El color dependerá del número de alertas únicas recibidas para cada edificio durante las últimas 24 horas. [web:22]

| Número de alertas | Color    |
| ----------------- | -------- |
| 0                 | Verde    |
| 1 a 4             | Amarillo |
| 5 o más           | Rojo     |

Estos umbrales son una regla visual del proyecto, no una clasificación oficial de riesgo de Wazuh. El mapa representa volumen de alertas, no una evaluación concluyente de seguridad.

Al seleccionar un edificio, mostrar su nombre, equipos registrados, equipos activos, conteo de alertas y eventos recientes.

Actualizar la interfaz cada 15 segundos mediante fetch. Incluir filtros por edificio, periodo y nivel de alerta, además de una indicación de última actualización.

## 13. Gráficas y endpoints

Generar con JFreeChart:

1. Barras: alertas por edificio durante el periodo seleccionado.
2. Línea: alertas por hora durante las últimas 24 horas.

Devolver las imágenes con Content-Type: image/png y utilizar ChartUtils.writeChartAsPNG. No abrir ventanas Swing en el servidor. [web:13]

Implementar los endpoints:

| Método | Ruta                               | Función                              |
| ------ | ---------------------------------- | ------------------------------------ |
| GET    | /                                  | Dashboard                            |
| POST   | /api/alertas                       | Recepción protegida de alertas Wazuh |
| GET    | /api/alertas                       | Listado paginado y filtrado          |
| GET    | /api/edificios/resumen             | Conteos para el mapa                 |
| GET    | /api/agentes                       | Agentes, estados y edificios         |
| GET    | /mapa/campus.geojson               | Geometría de edificios               |
| GET    | /graficas/alertas-por-edificio.png | Gráfica de barras                    |
| GET    | /graficas/alertas-por-hora.png     | Gráfica temporal                     |

## 14. Seguridad y tratamiento de errores

- Mantener credenciales fuera del repositorio.
- Usar credenciales Wazuh de privilegios mínimos y una cuenta LDAP de solo lectura.
- Validar certificados TLS; no desactivar su verificación como solución permanente.
- Usar LDAPS o StartTLS para LDAP fuera de un laboratorio aislado.
- Proteger el dashboard con autenticación y el webhook con su clave de integración.
- Limitar el tamaño del JSON recibido y validar entradas.
- No exponer H2 Console ni el directorio LDAP a Internet.
- No registrar contraseñas, claves, tokens ni JSON completos con información personal.
- Usar usuarios y eventos ficticios en las demostraciones públicas.

## 15. Orden de implementación

1. Crear el proyecto Maven y configurar Spring Boot y H2.
2. Crear las entidades y cargar los tres edificios.
3. Consultar la API Wazuh y asignar los agentes.
4. Implementar la recepción persistente de alertas.
5. Configurar y probar custom-campus en el manager.
6. Incorporar el procesamiento mediante hilos.
7. Configurar LDAP y probar los estados de búsqueda.
8. Construir el mapa y la tabla de alertas.
9. Generar las dos gráficas.
10. Ejecutar las pruebas de aceptación y documentar la instalación.

## 16. Pruebas de aceptación

- La aplicación obtiene agentes reales mediante la API Wazuh.
- Una alerta real llega mediante custom-campus y queda persistida.
- La alerta se asigna al edificio correcto según el agente.
- Una identidad de laboratorio se consulta en LDAP y se muestra sin atribuir responsabilidad.
- Un evento sin usuario se procesa sin fallar.
- Si LDAP no responde, la alerta sigue visible y registra ERROR_LDAP.
- Un reenvío no duplica alertas ni conteos.
- El mapa aplica la leyenda definida al periodo indicado.
- Las dos gráficas coinciden con los datos persistidos.
- Un reinicio conserva los datos y permite recuperar tareas pendientes.
- Una clave de integración incorrecta produce una respuesta de acceso denegado.
- Se demuestra procesamiento concurrente sin crecimiento ilimitado de hilos o cola.

## 17. Entregables

Entregar el código fuente, el adaptador custom-campus, una plantilla de configuración sin secretos, el GeoJSON, datos LDAP ficticios de laboratorio, pruebas automatizadas y un README con instrucciones de instalación y ejecución.

Incluir evidencia de una consulta real a la API Wazuh, una alerta real recibida, una consulta LDAP, el mapa actualizado y las gráficas. Identificar claramente cualquier evidencia basada en datos simulados.
