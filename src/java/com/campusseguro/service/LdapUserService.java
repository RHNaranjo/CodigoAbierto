package com.campusseguro.service;

import com.campusseguro.config.Config;
import com.campusseguro.entity.EnriquecimientoLdap;
import com.campusseguro.integration.TrustAllSSLSocketFactory;
import java.util.Hashtable;
import java.util.logging.Logger;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

/**
 * Busca por uid en el directorio (JNDI, como el proyecto LDAP) el usuario que
 * menciona una alerta. Cada consulta abre y cierra su propio contexto, así no
 * se comparte nada entre los hilos de procesamiento.
 *
 * Configuración: LDAP_URL, LDAP_BASE y, si el directorio no permite lectura
 * anónima, LDAP_USER y LDAP_PASSWORD (cuenta de solo lectura).
 */
public class LdapUserService {

    private static final Logger LOGGER = Logger.getLogger(LdapUserService.class.getName());
    private static final String TIEMPO_LIMITE = "5000"; // ms, conexión y lectura
    private static final String[] ATRIBUTOS = {"uid", "cn", "mail", "departmentNumber"};

    public static EnriquecimientoLdap buscar(String usuarioEvento) {
        String uid = normalizar(usuarioEvento);
        if (uid == null) {
            return new EnriquecimientoLdap("SIN_USUARIO", null);
        }
        String url = Config.get("LDAP_URL");
        if (url == null || url.isBlank()) {
            return new EnriquecimientoLdap("ERROR_LDAP", uid);
        }

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(entorno(url));
            SearchControls controles = new SearchControls();
            controles.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controles.setReturningAttributes(ATRIBUTOS);

            // {0} lo escapa JNDI: el texto de la alerta nunca se concatena al filtro
            String base = Config.get("LDAP_BASE");
            NamingEnumeration<SearchResult> resultados = ctx.search(
                    base == null ? "" : base, "(uid={0})", new Object[]{uid}, controles);

            if (!resultados.hasMore()) {
                return new EnriquecimientoLdap("NO_ENCONTRADO", uid);
            }
            Attributes atributos = resultados.next().getAttributes();
            if (resultados.hasMore()) {
                return new EnriquecimientoLdap("IDENTIDAD_AMBIGUA", uid);
            }

            EnriquecimientoLdap e = new EnriquecimientoLdap("ENCONTRADO", valor(atributos, "uid"));
            e.setNombre(valor(atributos, "cn"));
            e.setCorreo(valor(atributos, "mail"));
            e.setDepartamento(valor(atributos, "departmentNumber"));
            return e;

        } catch (NamingException ex) {
            LOGGER.warning("Error consultando LDAP: " + ex.getMessage());
            return new EnriquecimientoLdap("ERROR_LDAP", uid);
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ex) {
                    // ya no importa
                }
            }
        }
    }

    // Única regla explícita: DOMINIO\nombre -> nombre. Nada más se transforma.
    static String normalizar(String usuario) {
        if (usuario == null || usuario.isBlank()) {
            return null;
        }
        String uid = usuario.trim();
        int barra = uid.lastIndexOf('\\');
        if (barra >= 0) {
            uid = uid.substring(barra + 1);
        }
        return uid.isBlank() ? null : uid;
    }

    private static Hashtable<String, String> entorno(String url) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, url);
        env.put("com.sun.jndi.ldap.connect.timeout", TIEMPO_LIMITE);
        env.put("com.sun.jndi.ldap.read.timeout", TIEMPO_LIMITE);

        String usuario = Config.get("LDAP_USER");
        if (usuario != null && !usuario.isBlank()) {
            String password = Config.get("LDAP_PASSWORD");
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, usuario);
            env.put(Context.SECURITY_CREDENTIALS, password == null ? "" : password);
        }
        // Certificado autofirmado del laboratorio
        if (url.startsWith("ldaps:")) {
            env.put("java.naming.ldap.factory.socket", TrustAllSSLSocketFactory.class.getName());
        }
        return env;
    }

    private static String valor(Attributes atributos, String nombre) throws NamingException {
        Attribute a = atributos.get(nombre);
        return a == null ? null : (String) a.get();
    }
}
