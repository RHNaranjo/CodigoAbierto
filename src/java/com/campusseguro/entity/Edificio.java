package com.campusseguro.entity;

public class Edificio {

    private int id;
    private String nombre;
    private String poligono; // id del polígono en el GeoJSON

    public Edificio(int id, String nombre, String poligono) {
        this.id = id;
        this.nombre = nombre;
        this.poligono = poligono;
    }

    public int getId() { return id; }

    public String getNombre() { return nombre; }

    public String getPoligono() { return poligono; }
}
