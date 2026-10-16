# Demo de CampusSeguro

Formato: **presentación de Slides (6 diapositivas, 5 a 6 min) y después el dashboard en vivo (4 a 5 min)**. El profesor y el equipo no ven código ni terminal; el código solo aparece en la diapositiva 3. Mientras se muestra el dashboard, una terminal de fondo manda alertas simuladas y se ve cómo cambia la pantalla.

## Antes de la presentación (nadie mira)

1. **Empezar de cero** (si ya se ensayó, para que el mapa esté limpio):
   ```bash
   ./stop
   rm ~/CampusSeguro/campusseguro.mv.db
   ./start
   ```
   Los equipos de Wazuh vuelven a cargarse solos en unos segundos.
2. **Elegir tres equipos con nombre neutro.** Sus nombres salen en el dashboard, y algunos equipos reales llevan nombres de personas:
   ```bash
   ./simular
   ```
   Lista los equipos activos. Anotar tres ids (por ejemplo `A-29`, `B-34`, `B-16`): uno para Biblioteca, uno para Laboratorios y uno para Administración.
3. **Abrir el dashboard** http://localhost:8080/CampusSeguro en pantalla completa, y marcar **Modo demostración**. Todos los edificios deben verse en azul y con 0 alertas.
4. **Terminal de fondo:** abrir una Terminal en otro escritorio (o minimizada), ya ubicada en la carpeta del proyecto y con el comando escrito, sin ejecutar:
   ```bash
   cd ~/NetBeansProjects/CodigoAbierto
   ./simular ID_BIBLIOTECA ID_LABORATORIOS ID_ADMINISTRACION
   ```
5. Tener a la mano la presentación de Slides.

## Diapositivas (6)

**1. CampusSeguro: qué hace**
- Radar de seguridad para un campus: muestra en un mapa por edificios dónde hay más intentos fallidos de inicio de sesión.
- Convierte las alertas de Wazuh (monitoreo institucional) en información que se entiende de un vistazo.
- Frase: "Dónde hay más intentos fallidos de acceso, edificio por edificio".

**2. Cómo funciona** (diagrama de flujo)
Wazuh (equipos con agente) → CampusSeguro consulta cada 60 s → guarda la alerta → 2 hilos la procesan (asignan edificio, buscan la cuenta en LDAP) → base de datos → dashboard.
- Wazuh es institucional: solo se lee, no se modifica.
- Si LDAP falla, la alerta se muestra igual.

**3. Estructura del código** (única diapositiva con código; una línea por paquete)
- `config`: arranque, base de datos H2 y configuración.
- `controller`: servlets que responden al navegador (`/api/alertas`, `/api/agentes`, gráficas).
- `service`: lógica: sincronización con Wazuh, procesamiento con hilos, LDAP, conteos.
- `integration`: conexión con Wazuh (API e indexador).
- `entity` y `dto`: datos (edificio, agente, filtros).
- `web/`: dashboard (HTML, CSS, JavaScript, mapa en GeoJSON). `ldap/`: directorio de laboratorio. `test/`: pruebas automáticas.

**4. Herramientas utilizadas**
Java 21 con NetBeans y Ant · Tomcat 11 (servlets) · H2 (base de datos) · Hilos (`ThreadPoolExecutor`) · LDAP con JNDI y OpenLDAP · JFreeChart (gráficas) · Leaflet y GeoJSON (mapa) · Gson · Wazuh (API e indexador) · JUnit.

**5. El dashboard** (captura de pantalla con anotaciones)
- **Mapa:** cada edificio cambia de color según sus alertas en el periodo: azul 0, dorado 1 a 4, guinda 5 o más. Es volumen de alertas, no una medida de riesgo.
- **Cifras:** alertas del periodo, equipos registrados y activos, alertas sin edificio.
- **Panel del edificio:** al hacer clic, sus equipos y eventos recientes.
- **Gráficas:** alertas por edificio y por hora.
- **Tabla:** cada alerta con la cuenta mencionada y el resultado del directorio. Esa cuenta no es necesariamente la responsable.
- **Filtros** (edificio, periodo, nivel, modo demostración) y actualización cada 15 s.

**6. Lo que verán ahora**
- Demostración en vivo: un script de fondo manda alertas **simuladas** (marcadas como tales y fuera de los conteos normales; solo cuentan con el modo demostración).
- Decisiones: Wazuh no se modifica; la cuenta de una alerta no se atribuye como responsable; los datos de prueba son ficticios.

## Dashboard en vivo con simulación (4 a 5 min)

Cambiar al dashboard y explicar en 30 segundos el mapa, las cifras y los filtros (todo en 0). Después, **sin que se vea**, cambiar a la Terminal de fondo y ejecutar el comando de simulación; volver al dashboard. Cada fase espera 15 s (el tiempo de refresco), así que la pantalla cambia sola.

| Minuto | Fase de la simulación | Qué se ve en el dashboard | Qué decir |
|---|---|---|---|
| 0:00 | **1.** Biblioteca: 2 alertas | Biblioteca pasa a **dorado**; las cifras suben | "Llegaron dos alertas; el equipo está asignado a la Biblioteca y el mapa lo refleja." |
| 0:20 | **2.** Laboratorios: 6 alertas | Laboratorios pasa a **guinda** al cruzar 5; la gráfica por edificio sube | "Con cinco o más alertas el edificio se pinta en guinda." Hacer clic en Laboratorios: se filtran panel y tabla. |
| 1:00 | | En la tabla, **Directorio**: ENCONTRADO (Ana López, Marta Ruiz), IDENTIDAD_AMBIGUA (`luis`), NO_ENCONTRADO (`pedro`) | "Cada cuenta se busca en LDAP. `luis` existe dos veces, así que no se adivina; `pedro` no existe." |
| 1:15 | **3.** Administración: 1 alerta con `CAMPUS\ana` | Administración pasa a **dorado**; la cuenta sale como `CAMPUS\ana` y el directorio la reconoce como `ana` | "El dominio se quita con una regla explícita." |
| 1:30 | **4.** Alerta sin cuenta | Aparece sin cuenta ni directorio (SIN_USUARIO) | "Una alerta sin usuario se muestra igual." |
| 1:45 | **5.** La misma alerta enviada dos veces | Biblioteca sube de 2 a **3**, no a 4 | "Un reenvío no duplica ni cuenta dos veces." |
| 2:00 | **6.** Lote de 20 alertas | Administración sube a **22** y pasa a guinda en segundos | "Dos hilos procesan el lote con una cola limitada; la aplicación no se satura." |
| 2:30 | Terminó | — | Quitar **Modo demostración**: todo vuelve a 0, porque las simuladas no cuentan en los indicadores normales. |

Para cerrar: cambiar a **Última hora** y **Nivel mínimo 10** para mostrar que los filtros recalculan mapa, gráficas y tabla.

## Si algo falla

- **El mapa no cambia:** la pantalla se refresca cada 15 s; esperar o recargar la página (confirmar que **Modo demostración** siga marcado).
- **Dice "Wazuh sin responder":** la conexión a Wazuh no está disponible; la simulación funciona igual, porque no depende de Wazuh.
- **`./simular` dice que la app no responde:** correr `./start`.
- **Repetir la simulación sin reiniciar:** se puede (cada ejecución usa ids nuevos), pero los conteos se acumulan; para volver a cero, repetir los pasos de "Empezar de cero".

## Al terminar

```bash
./stop
```

Los datos quedan en `~/CampusSeguro/campusseguro.mv.db`.

## Extras (solo para ensayar; no usar con público)

- **LDAP caído:** `kill $(cat ~/CampusSeguro/ldap/slapd.pid)` y mandar otra simulación: las alertas nuevas muestran ERROR_LDAP pero se ven igual. Se levanta con `./ldap/iniciar.sh`.
- **Alerta real:** un login fallido en un equipo propio con agente del manager (por ejemplo, `ssh usuario_inexistente@localhost` varias veces); aparece en 1 a 2 minutos con origen REAL.
