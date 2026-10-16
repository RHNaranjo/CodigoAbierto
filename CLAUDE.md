# CampusSeguro

Proyecto unificador de Código Abierto. Instrucciones en `CampusSeguro.md`; avance y cambios acordados en `README.md`.

## Reglas

- Código simple: sin complejidad innecesaria, sin patrones rimbombantes, sin sobre-ingeniería.
- Imitar el estilo de los proyectos en `~/NetBeansProjects` (ProcesaEventos, LDAP, Hilos, Graficas, etc.) y usarlos como base.
- README, comentarios y documentación: concisos, en español.
- Credenciales siempre por variables de entorno, nunca en el repositorio.
- Wazuh es institucional: no se le puede hacer ningún cambio (usuarios, permisos, integraciones, `ossec.conf`). Solo se consulta con las credenciales que ya existen.

## Estructura

- NetBeans *Java with Ant → Web Application* en Tomcat 11, igual que ProcesaEventos. Sin Maven ni Spring.
- Fuentes en `src/java`, pruebas en `test` (JUnit 4, clases terminadas en `Test`), web en `web/`.
- Cada jar nuevo va en `lib/`, se declara como `file.reference.X.jar=lib/X.jar` en `nbproject/project.properties`, se agrega a `javac.classpath` y a `web-module-libraries` de `nbproject/project.xml`.
- `nbproject/build-impl.xml` lo genera NetBeans; no editarlo a mano.
