package com.campusseguro.entity;

public class Agente {

    private String idWazuh;
    private String nombre;
    private String estado;
    private Integer edificioId;           // null = Sin edificio
    private String ultimaSincronizacion;

    public Agente(String idWazuh, String nombre, String estado) {
        this.idWazuh = idWazuh;
        this.nombre = nombre;
        this.estado = estado;
    }

    public String getIdWazuh() { return idWazuh; }

    public String getNombre() { return nombre; }

    public String getEstado() { return estado; }

    public Integer getEdificioId() { return edificioId; }

    public void setEdificioId(Integer edificioId) { this.edificioId = edificioId; }

    public String getUltimaSincronizacion() { return ultimaSincronizacion; }

    public void setUltimaSincronizacion(String ultimaSincronizacion) { this.ultimaSincronizacion = ultimaSincronizacion; }
}
