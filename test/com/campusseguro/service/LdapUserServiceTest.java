package com.campusseguro.service;

import com.campusseguro.entity.EnriquecimientoLdap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.After;
import org.junit.Test;

/**
 * Los casos ENCONTRADO y NO_ENCONTRADO se prueban a mano contra el OpenLDAP
 * de laboratorio (ldap/iniciar.sh); aquí solo lo que no necesita servidor.
 */
public class LdapUserServiceTest {

    @After
    public void limpiar() {
        System.clearProperty("LDAP_URL");
    }

    // DOMINIO\nombre -> nombre; el resto se deja igual
    @Test
    public void normaliza() {
        assertEquals("ana", LdapUserService.normalizar("CAMPUS\\ana"));
        assertEquals("ana", LdapUserService.normalizar(" ana "));
        assertEquals("ana@campus.local", LdapUserService.normalizar("ana@campus.local"));
        assertNull(LdapUserService.normalizar("   "));
        assertNull(LdapUserService.normalizar(null));
    }

    @Test
    public void sinUsuario() {
        assertEquals("SIN_USUARIO", LdapUserService.buscar(null).getResultado());
    }

    // Si el directorio no responde se registra ERROR_LDAP y se conserva el uid
    @Test
    public void ldapCaido() {
        System.setProperty("LDAP_URL", "ldap://localhost:1");
        EnriquecimientoLdap e = LdapUserService.buscar("CAMPUS\\ana");
        assertEquals("ERROR_LDAP", e.getResultado());
        assertEquals("ana", e.getUid());
    }
}
