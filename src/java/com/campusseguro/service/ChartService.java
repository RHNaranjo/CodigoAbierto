package com.campusseguro.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.Hour;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

/**
 * Gráficas PNG con JFreeChart (como el proyecto Graficas), en colores lisos:
 * sin sombras, degradados ni bordes.
 *
 * @author rodrigonaranjo
 */
public class ChartService {

    public static final Color AZUL = new Color(0x002D72);
    public static final Color GUINDA = new Color(0x8A1538);
    private static final Color LINEA = new Color(0xE3E3E3);
    private static final Color TEXTO = new Color(0x555555);
    private static final Font LETRA = new Font("SansSerif", Font.PLAIN, 11);

    // Barras: alertas por edificio en el periodo
    @SuppressWarnings("unchecked")
    public static JFreeChart porEdificio(Map<String, Object> resumen) {
        DefaultCategoryDataset datos = new DefaultCategoryDataset();
        for (Map<String, Object> e : (List<Map<String, Object>>) resumen.get("edificios")) {
            datos.addValue((Integer) e.get("alertas"), "Alertas", (String) e.get("nombre"));
        }
        datos.addValue((Integer) resumen.get("sinEdificio"), "Alertas", "Sin edificio");

        JFreeChart chart = ChartFactory.createBarChart(null, null, null, datos,
                PlotOrientation.VERTICAL, false, false, false);
        CategoryPlot plot = chart.getCategoryPlot();
        estilo(chart);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(LINEA);
        plot.getDomainAxis().setTickLabelFont(LETRA);
        plot.getDomainAxis().setTickLabelPaint(TEXTO);
        plot.getDomainAxis().setAxisLinePaint(LINEA);
        plot.getDomainAxis().setTickMarksVisible(false);
        enteros((NumberAxis) plot.getRangeAxis());

        BarRenderer barras = (BarRenderer) plot.getRenderer();
        barras.setBarPainter(new StandardBarPainter()); // sin degradado
        barras.setShadowVisible(false);
        barras.setSeriesPaint(0, AZUL);
        barras.setMaximumBarWidth(0.12);
        return chart;
    }

    // Línea: alertas por hora en las últimas 24 horas
    public static JFreeChart porHora(List<Timestamp> fechas) {
        TimeSeries serie = new TimeSeries("Alertas");
        Hour hora = new Hour(new Date());
        for (int i = 0; i < 24; i++) { // las 24 horas, aunque tengan cero
            serie.add(hora, 0);
            hora = (Hour) hora.previous();
        }
        for (Timestamp t : fechas) {
            Hour h = new Hour(t);
            if (serie.getDataItem(h) != null) {
                serie.update(h, serie.getValue(h).intValue() + 1);
            }
        }

        JFreeChart chart = ChartFactory.createTimeSeriesChart(null, null, null,
                new TimeSeriesCollection(serie), false, false, false);
        XYPlot plot = chart.getXYPlot();
        estilo(chart);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinePaint(LINEA);

        DateAxis eje = (DateAxis) plot.getDomainAxis();
        eje.setDateFormatOverride(new SimpleDateFormat("HH:mm"));
        eje.setTickLabelFont(LETRA);
        eje.setTickLabelPaint(TEXTO);
        eje.setAxisLinePaint(LINEA);
        enteros((NumberAxis) plot.getRangeAxis());

        XYLineAndShapeRenderer linea = (XYLineAndShapeRenderer) plot.getRenderer();
        linea.setSeriesPaint(0, GUINDA);
        linea.setSeriesStroke(0, new BasicStroke(2f));
        return chart;
    }

    private static void estilo(JFreeChart chart) {
        chart.setBackgroundPaint(Color.WHITE);
        chart.setBorderVisible(false);
        chart.setAntiAlias(true);
    }

    // Eje vertical: solo números enteros, sin línea
    private static void enteros(NumberAxis eje) {
        eje.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        eje.setTickLabelFont(LETRA);
        eje.setTickLabelPaint(TEXTO);
        eje.setAxisLineVisible(false);
        eje.setTickMarksVisible(false);
        eje.setLowerBound(0);
        eje.setAutoRangeMinimumSize(5); // con pocas alertas la escala llega al menos a 5
    }
}
