package com.campusseguro.entity;

/**
 * Resultado de buscar en el directorio el usuario que menciona una alerta.
 * resultado: ENCONTRADO, NO_ENCONTRADO, SIN_USUARIO, ERROR_LDAP o IDENTIDAD_AMBIGUA.
 */
public class EnriquecimientoLdap {

    private String resultado;
    private String uid;          // identificador ya normalizado; null si SIN_USUARIO
    private String nombre;
    private String correo;
    private String departamento;

    public EnriquecimientoLdap(String resultado, String uid) {
        this.resultado = resultado;
        this.uid = uid;
    }

    public String getResultado() { return resultado; }

    public String getUid() { return uid; }

    public String getNombre() { return nombre; }

    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCorreo() { return correo; }

    public void setCorreo(String correo) { this.correo = correo; }

    public String getDepartamento() { return departamento; }

    public void setDepartamento(String departamento) { this.departamento = departamento; }
}
