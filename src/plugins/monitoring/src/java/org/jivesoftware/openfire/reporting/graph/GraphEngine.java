/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.reporting.graph;

import org.jivesoftware.openfire.reporting.stats.StatsViewer;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.axis.*;
import org.jfree.chart.encoders.KeypointPNGEncoderAdapter;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.time.*;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.IntervalXYDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.util.Rotation;
import org.jivesoftware.openfire.stats.Statistic;
import org.jivesoftware.util.JiveGlobals;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.NumberFormat;

/**
 * Builds graphs off of statistics tracked in the <i>StatsEngine</i>.
 *
 * @author Alexander Wenckus
 * @see StatsViewer
 */
public class GraphEngine {
    private StatsViewer statsViewer;

    private static final long YEAR = 31104000000L;

    private static final long MONTH = 2592000000L;

    private static final long WEEK = 604800000L;

    private static final long DAY = 86400000L;

    private TickUnits tickUnits;
    private Locale oldLocale;

    /**
     * Default constructor used by the plugin container to construct the graph engine.
     *
     * @param statsViewer The viewer provides an mechanism to view the data being tracked by the <i>StatsEngine</i>.
     */
    public GraphEngine(StatsViewer statsViewer) {
        this.statsViewer = statsViewer;
    }

    /**
     * Creates a graph in PNG format.  The PNG graph is encoded by the KeypointPNGEncoderAdapter
     * so that the resulting PNG is encoded with alpha transparency.
     *
     * @param key
     * @param width
     * @param height
     * @param startTime
     * @param endTime
     * @param dataPoints
     * @return
     * @throws IOException
     */
    public byte[] generateGraph(String key, int width, int height, String color, long startTime, long endTime,
                                int dataPoints) throws IOException
    {

        JFreeChart chart = generateChart(key, width, height, color, startTime, endTime,dataPoints);
        KeypointPNGEncoderAdapter encoder = new KeypointPNGEncoderAdapter();
        encoder.setEncodingAlpha(true);
        return encoder.encode(chart.createBufferedImage(width, height, BufferedImage.BITMASK, null));
    }

    /**
     * Creates a chart.
     *
     * @param key
     * @param width
     * @param height
     * @param startTime
     * @param endTime
     * @param dataPoints
     * @return
     * @throws IOException
     */
    public JFreeChart generateChart(String key, int width, int height, String color, long startTime, long endTime,
                                    int dataPoints) throws IOException
    {
        Statistic[] def = statsViewer.getStatistic(key);
        if (def == null) {
            return null;
        }

        XYDataset data = populateData(key, def, startTime, endTime, dataPoints);
        if (data == null) {
            return null;
        }
        JFreeChart chart;
        switch(def[0].getStatType()) {
            case count:
                chart = createTimeBarChart(null, color, def[0].getUnits(), data);
                break;
            default:
                chart = createTimeAreaChart(null, color, def[0].getUnits(), data);
        }

        return chart;
    }



    /**
     * Generates a Sparkline type graph. Sparkline graphs
     * are "intense, simple, wordlike graphics" so named by Edward Tufte. The big
     * difference between the graph produced by this method compared to the
     * graph produced by the <code>generateGraph</code> method is that this one
     * produces graphs with no x-axis and no y-axis and is usually smaller in size.
     * @param key
     * @param width
     * @param height
     * @param startTime
     * @param endTime
     * @param dataPoints
     * @return
     * @throws IOException
     */
    public byte[] generateSparklinesGraph(String key, int width, int height, String color, long startTime,
                                          long endTime, int dataPoints) throws IOException
    {
        Statistic[] def = statsViewer.getStatistic(key);
        if (def == null) {
            return null;
        }

        JFreeChart chart;
        switch (def[0].getStatType()) {
            case count:
                chart = generateSparklineBarGraph(key, color, def, startTime, endTime, dataPoints);
                break;
            default:
                chart = generateSparklineAreaChart(key, color, def, startTime, endTime, dataPoints);
        }

        KeypointPNGEncoderAdapter encoder = new KeypointPNGEncoderAdapter();
        encoder.setEncodingAlpha(true);
        return encoder.encode(chart.createBufferedImage(width, height, BufferedImage.BITMASK, null));
    }

    private XYDataset populateData(String key, Statistic[] def, long startTime, long endTime,
                                   int dataPoints)
    {
        double[][] values = statsViewer.getData(key, startTime, endTime, dataPoints);
        long timePeriod = endTime - startTime;
        TimeSeries[] series = new TimeSeries[values.length];
        TimeSeriesCollection dataSet = new TimeSeriesCollection();

        for (int d = 0; d < values.length; d++) {
            series[d] = new TimeSeries(def[d].getName(), getTimePeriodClass(timePeriod));
            Statistic.Type type = def[d].getStatType();

            long interval = timePeriod / values[d].length;
            for (int i = 0; i < values[d].length; i++) {
                series[d].addOrUpdate(
                        getTimePeriod(timePeriod, new Date(startTime + (i * interval)),
                                JiveGlobals.getTimeZone()), cleanData(type, values[d][i]));
            }
            dataSet.addSeries(series[d]);
        }
        return dataSet;
    }

    private Class<? extends RegularTimePeriod> getTimePeriodClass(long timePeriod) {
        if (timePeriod > 86400000) {
            return Day.class;
        } else if (timePeriod > 3600000) {
            return Hour.class;
        } else {
            return Minute.class;
        }
    }

    private RegularTimePeriod getTimePeriod(long timePeriod, Date date, TimeZone zone) {
        if (timePeriod > 86400000) {
            return new Day(date, zone);
        } else if (timePeriod > 3600000) {
            return new Hour(date, zone);
        } else {
            return new Minute(date, zone);
        }
    }


    /**
     * Round up a defined value.
     *
     * @param type  the type of Statistic.
     * @param value the value.
     * @return the rounded up value.
     */
    private double cleanData(Statistic.Type type, double value) {
        if(type == Statistic.Type.count) {
            return Math.round(value);
        }
        return value;
    }

    /**
     * Generates a generic Time Area Chart.
     *
     * @param title      the title of the Chart.
     * @param valueLabel the Y Axis label.
     * @param data       the data to populate with.
     * @return the generated Chart.
     */
    private JFreeChart createTimeAreaChart(String title, String color, String valueLabel, XYDataset data) {
        PlotOrientation orientation = PlotOrientation.VERTICAL;

        DateAxis xAxis = generateTimeAxis();

        NumberAxis yAxis = new NumberAxis(valueLabel);

        NumberFormat formatter = NumberFormat.getNumberInstance(JiveGlobals.getLocale());
        formatter.setMaximumFractionDigits(2);
        formatter.setMinimumFractionDigits(0);
        yAxis.setNumberFormatOverride(formatter);

        XYAreaRenderer renderer = new XYAreaRenderer(XYAreaRenderer.AREA);
        renderer.setOutline(true);

        return createChart(title, data, xAxis, yAxis, orientation, renderer,
                GraphDefinition.getDefinition(color));
    }

    /**
     * Generates a generic Time Bar Chart.
     *
     * @param title      the title of the Chart.
     * @param valueLabel the X Axis Label.
     * @param data       the data to populate with.
     * @return the generated Chart.
     */
    private JFreeChart createTimeBarChart(String title, String color, String valueLabel, XYDataset data) {
        PlotOrientation orientation = PlotOrientation.VERTICAL;
        DateAxis xAxis = generateTimeAxis();

        NumberAxis yAxis = new NumberAxis(valueLabel);
        NumberFormat formatter = NumberFormat.getNumberInstance(JiveGlobals.getLocale());
        formatter.setMaximumFractionDigits(2);
        formatter.setMinimumFractionDigits(0);
        yAxis.setNumberFormatOverride(formatter);
        yAxis.setAutoRangeIncludesZero(true);

        return createChart(title, data, xAxis, yAxis, orientation, new XYBarRenderer(),
                GraphDefinition.getDefinition(color));
    }

    /**
     * Generates a Chart.
     *
     * @param title        the title of the chart.
     * @param data         the data to use in the chart.
     * @param xAxis        the variables to use on the xAxis.
     * @param yAxis        the variables to use on the yAxis.
     * @param orientation  the orientation
     * @param itemRenderer the type of renderer to use.
     * @return the generated chart.
     */
    private JFreeChart createChart(String title, XYDataset data, ValueAxis xAxis, ValueAxis yAxis,
                                   PlotOrientation orientation, XYItemRenderer itemRenderer, GraphDefinition def)
    {
        int seriesCount = data.getSeriesCount();
        for(int i = 0; i < seriesCount; i++) {
            itemRenderer.setSeriesPaint(i, def.getInlineColor(i));
            itemRenderer.setSeriesOutlinePaint(i, def.getOutlineColor(i));
        }

        XYPlot plot = new XYPlot(data, xAxis, yAxis, null);
        plot.setOrientation(orientation);
        plot.setRenderer(itemRenderer);

        return createChart(title, plot);
    }

    private JFreeChart createChart(String title, XYPlot plot) {
        JFreeChart chart = new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, plot, false);
        chart.setBackgroundPaint(Color.white);
        return chart;
    }

    /**
     * Generates a simple Time Axis.
     *
     * @return the generated Time Axis.
     */
    private DateAxis generateTimeAxis() {
        DateAxis xAxis = new DateAxis("");
        xAxis.setLowerMargin(0.05);
        xAxis.setUpperMargin(0.02);
        xAxis.setLabel(null);
        xAxis.setTickLabelsVisible(true);
        xAxis.setTickMarksVisible(true);

        xAxis.setAxisLineVisible(true);
        xAxis.setNegativeArrowVisible(false);
        xAxis.setPositiveArrowVisible(false);
        xAxis.setVisible(true);
        xAxis.setTickMarkPosition(DateTickMarkPosition.MIDDLE);
        Locale locale = JiveGlobals.getLocale();
        // If the tick units have not yet been setup or the locale has changed
        if(tickUnits == null || !locale.equals(oldLocale)) {
            tickUnits = createTickUnits(locale, JiveGlobals.getTimeZone());
            oldLocale = locale;
        }
        xAxis.setStandardTickUnits(tickUnits);

        return xAxis;
    }

    private TickUnits createTickUnits(Locale locale, TimeZone zone) {
        TickUnits units = new TickUnits();

        // date formatters
        DateFormat f1 = new SimpleDateFormat("HH:mm:ss.SSS", locale);
        DateFormat f2 = new SimpleDateFormat("HH:mm:ss", locale);
        DateFormat f3 = DateFormat.getTimeInstance(DateFormat.SHORT, locale);
        DateFormat f4 = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                DateFormat.SHORT, locale);
        DateFormat f5 = new SimpleDateFormat("d-MMM", locale);
        DateFormat f6 = new SimpleDateFormat("MMM-yyyy", locale);
        DateFormat f7 = new SimpleDateFormat("yyyy", locale);

        // NOTE: timezone not needed on date formatters because dates have already been converted
        // to the appropriate timezone by the respective RegularTimePeriod (Minute, Hour, Day, etc)
        // see:
        //   http://www.jfree.org/jfreechart/api/gjdoc/org/jfree/data/time/Hour.html#Hour:Date:TimeZone
        //
        // If you do use a timezone on the formatters and the Jive TimeZone has been set to something
        // other than the system timezone, time specific charts will show incorrect values.
        /*
        f1.setTimeZone(zone);
        f2.setTimeZone(zone);
        f3.setTimeZone(zone);
        f4.setTimeZone(zone);
        f5.setTimeZone(zone);
        f6.setTimeZone(zone);
        f7.setTimeZone(zone);
        */

        // milliseconds
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 1, f1));
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 5, DateTickUnit.MILLISECOND, 1, f1));
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 10, DateTickUnit.MILLISECOND, 1, f1));
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 25, DateTickUnit.MILLISECOND, 5, f1));
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 50, DateTickUnit.MILLISECOND, 10, f1));
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 100, DateTickUnit.MILLISECOND, 10, f1));
        units.add(new DateTickUnit( DateTickUnit.MILLISECOND, 250, DateTickUnit.MILLISECOND, 10, f1));
        units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 500, DateTickUnit.MILLISECOND, 50, f1));

        // seconds
        units.add(new DateTickUnit(DateTickUnit.SECOND, 1, DateTickUnit.MILLISECOND, 50, f2));
        units.add(new DateTickUnit(DateTickUnit.SECOND, 5, DateTickUnit.SECOND, 1, f2));
        units.add(new DateTickUnit(DateTickUnit.SECOND, 10, DateTickUnit.SECOND, 1, f2));
        units.add(new DateTickUnit(DateTickUnit.SECOND, 30, DateTickUnit.SECOND, 5, f2));

        // minutes
        units.add(new DateTickUnit(DateTickUnit.MINUTE, 1, DateTickUnit.SECOND, 5, f3));
        units.add( new DateTickUnit(DateTickUnit.MINUTE, 2, DateTickUnit.SECOND, 10, f3));
        units.add(new DateTickUnit(DateTickUnit.MINUTE, 5, DateTickUnit.MINUTE, 1, f3));
        units.add(new DateTickUnit(DateTickUnit.MINUTE, 10, DateTickUnit.MINUTE, 1, f3));
        units.add(new DateTickUnit(DateTickUnit.MINUTE, 15, DateTickUnit.MINUTE, 5, f3));
        units.add(new DateTickUnit(DateTickUnit.MINUTE, 20, DateTickUnit.MINUTE, 5, f3));
        units.add(new DateTickUnit(DateTickUnit.MINUTE, 30, DateTickUnit.MINUTE, 5, f3));

        // hours
        units.add(new DateTickUnit(DateTickUnit.HOUR, 1, DateTickUnit.MINUTE, 5, f3));
        units.add(new DateTickUnit(DateTickUnit.HOUR, 2, DateTickUnit.MINUTE, 10, f3));
        units.add(new DateTickUnit(DateTickUnit.HOUR, 4, DateTickUnit.MINUTE, 30, f3) );
        units.add(new DateTickUnit(DateTickUnit.HOUR, 6, DateTickUnit.HOUR, 1, f3));
        units.add(new DateTickUnit(DateTickUnit.HOUR, 12, DateTickUnit.HOUR, 1, f4));

        // days
        units.add(new DateTickUnit(DateTickUnit.DAY, 1, DateTickUnit.HOUR, 1, f5));
        units.add(new DateTickUnit(DateTickUnit.DAY, 2, DateTickUnit.HOUR, 1, f5));
        units.add(new DateTickUnit(DateTickUnit.DAY, 7, DateTickUnit.DAY, 1, f5));
        units.add(new DateTickUnit(DateTickUnit.DAY, 15, DateTickUnit.DAY, 1, f5));

        // months
        units.add(new DateTickUnit(DateTickUnit.MONTH, 1, DateTickUnit.DAY, 1, f6));
        units.add(new DateTickUnit(DateTickUnit.MONTH, 2, DateTickUnit.DAY, 1, f6));
        units.add(new DateTickUnit(DateTickUnit.MONTH, 3, DateTickUnit.MONTH, 1, f6));
        units.add(new DateTickUnit(DateTickUnit.MONTH, 4, DateTickUnit.MONTH, 1, f6));
        units.add(new DateTickUnit(DateTickUnit.MONTH, 6, DateTickUnit.MONTH, 1, f6));

        // years
        units.add(new DateTickUnit(DateTickUnit.YEAR, 1, DateTickUnit.MONTH, 1, f7));
        units.add(new DateTickUnit(DateTickUnit.YEAR, 2, DateTickUnit.MONTH, 3, f7));
        units.add(new DateTickUnit(DateTickUnit.YEAR, 5, DateTickUnit.YEAR, 1, f7));
        units.add(new DateTickUnit(DateTickUnit.YEAR, 10, DateTickUnit.YEAR, 1, f7));
        units.add(new DateTickUnit(DateTickUnit.YEAR, 25, DateTickUnit.YEAR, 5, f7));
        units.add(new DateTickUnit(DateTickUnit.YEAR, 50, DateTickUnit.YEAR, 10, f7));
        units.add(new DateTickUnit(DateTickUnit.YEAR, 100, DateTickUnit.YEAR, 20, f7));

        return units;
    }

    /**
     * Generates a SparkLine Time Area Chart.
     * @param key
     * @param stats
     * @param startTime
     * @param endTime
     * @return chart
     */
    private JFreeChart generateSparklineAreaChart(String key, String color, Statistic [] stats, long startTime, long endTime, int dataPoints) {
        Color backgroundColor = getBackgroundColor();

        XYDataset dataset = populateData(key, stats, startTime, endTime, dataPoints);

        JFreeChart chart = ChartFactory.createXYAreaChart(
                null, // chart title
                null, // xaxis label
                null, // yaxis label
                dataset, // data
                PlotOrientation.VERTICAL,
                false, // include legend
                false, // tooltips?
                false // URLs?
        );

        chart.setBackgroundPaint(backgroundColor);
        chart.setBorderVisible(false);
        chart.setBorderPaint(null);

        XYPlot plot = (XYPlot)chart.getPlot();
        plot.setForegroundAlpha(1.0f);
        plot.setDomainGridlinesVisible(false);
        plot.setDomainCrosshairVisible(false);
        plot.setRangeCrosshairVisible(false);
        plot.setBackgroundPaint(backgroundColor);
        plot.setRangeGridlinesVisible(false);

        GraphDefinition graphDef = GraphDefinition.getDefinition(color);
        Color plotColor = graphDef.getInlineColor(0);
        plot.getRenderer().setSeriesPaint(0, plotColor);
        plot.getRenderer().setBaseItemLabelsVisible(false);
        plot.getRenderer().setBaseOutlinePaint(backgroundColor);
        plot.setOutlineStroke(null);
        plot.setDomainGridlinePaint(null);

        NumberAxis xAxis = (NumberAxis)chart.getXYPlot().getDomainAxis();

        xAxis.setLabel(null);
        xAxis.setTickLabelsVisible(true);
        xAxis.setTickMarksVisible(true);
        xAxis.setAxisLineVisible(false);
        xAxis.setNegativeArrowVisible(false);
        xAxis.setPositiveArrowVisible(false);
        xAxis.setVisible(false);

        NumberAxis yAxis = (NumberAxis)chart.getXYPlot().getRangeAxis();
        yAxis.setTickLabelsVisible(false);
        yAxis.setTickMarksVisible(false);
        yAxis.setAxisLineVisible(false);
        yAxis.setNegativeArrowVisible(false);
        yAxis.setPositiveArrowVisible(false);
        yAxis.setVisible(false);

        return chart;
    }

    /**
     * Creates a Pie Chart based on map.
     *
     * @return the Pie Chart generated.
     */
    public JFreeChart getPieChart(Map<String, Double> pieValues) {
        DefaultPieDataset dataset = new DefaultPieDataset();

        for (String key : pieValues.keySet()) {
            dataset.setValue(key, pieValues.get(key));
        }

        JFreeChart chart = ChartFactory.createPieChart3D(
                null,  // chart title
                dataset, // data
                true,    // include legend
                true,
                false
        );

        chart.setBackgroundPaint(Color.white);
        chart.setBorderVisible(false);
        chart.setBorderPaint(null);


        PiePlot plot = (PiePlot)chart.getPlot();
        plot.setSectionOutlinesVisible(false);
        plot.setLabelFont(new Font("SansSerif", Font.BOLD, 12));
        plot.setNoDataMessage("No data available");
        plot.setCircular(true);
        plot.setLabelGap(0.02);
        plot.setOutlinePaint(null);
        plot.setLabelLinksVisible(false);

        plot.setLabelGenerator(null);

        plot.setLegendLabelGenerator(new StandardPieSectionLabelGenerator("{0}"));


        plot.setStartAngle(270);
        plot.setDirection(Rotation.ANTICLOCKWISE);
        plot.setForegroundAlpha(0.60f);
        plot.setInteriorGap(0.33);

        return chart;
    }

    /**
     * Generates a Sparkline Bar Graph.
     *
     * @param def the key of the statistic object.
     * @return the generated chart.
     */
    public JFreeChart generateSparklineBarGraph(String key, String color, Statistic [] def, long startTime,
                                                long endTime, int dataPoints)
    {
        Color backgroundColor = getBackgroundColor();

        IntervalXYDataset dataset = (IntervalXYDataset) populateData(key, def, startTime, endTime, dataPoints);
        JFreeChart chart = ChartFactory.createXYBarChart(
                null, // chart title
                null, // domain axis label
                true,
                null, // range axis label
                dataset, // data
                PlotOrientation.VERTICAL,
                false, // include legend
                false, // tooltips?
                false // URLs?
        );

        chart.setBackgroundPaint(backgroundColor);
        chart.setBorderVisible(false);
        chart.setBorderPaint(null);

        XYPlot plot = (XYPlot)chart.getPlot();
        plot.setDomainGridlinesVisible(false);
        plot.setDomainCrosshairVisible(false);
        plot.setRangeCrosshairVisible(false);
        plot.setBackgroundPaint(backgroundColor);
        plot.setRangeGridlinesVisible(false);


        GraphDefinition graphDef = GraphDefinition.getDefinition(color);
        Color plotColor = graphDef.getInlineColor(0);
        plot.getRenderer().setSeriesPaint(0, plotColor);
        plot.getRenderer().setBaseItemLabelsVisible(false);
        plot.getRenderer().setBaseOutlinePaint(backgroundColor);
        plot.setOutlineStroke(null);
        plot.setDomainGridlinePaint(null);

        ValueAxis xAxis = chart.getXYPlot().getDomainAxis();

        xAxis.setLabel(null);
        xAxis.setTickLabelsVisible(true);
        xAxis.setTickMarksVisible(true);
        xAxis.setAxisLineVisible(false);
        xAxis.setNegativeArrowVisible(false);
        xAxis.setPositiveArrowVisible(false);
        xAxis.setVisible(false);

        ValueAxis yAxis = chart.getXYPlot().getRangeAxis();
        yAxis.setTickLabelsVisible(false);
        yAxis.setTickMarksVisible(false);
        yAxis.setAxisLineVisible(false);
        yAxis.setNegativeArrowVisible(false);
        yAxis.setPositiveArrowVisible(false);
        yAxis.setVisible(false);

        return chart;
    }

    /**
     * Takes a hexidecimel color value and returns its color equivelent.
     *
     * @param hexColor The hex color to be parsed
     * @return The java color object
     */
    private static Color getColor(String hexColor) {
        return new Color(Integer.valueOf(hexColor.substring(0, 2), 16),
                Integer.valueOf(hexColor.substring(2, 4), 16),
                Integer.valueOf(hexColor.substring(4, 6), 16));
    }

    /**
     * Returns a color that can be used as a background.
     * @return Color
     */
    private static Color getBackgroundColor() {
        return new Color(255,255,255);
    }

    public static long[] parseTimePeriod(String timeperiod) {

        if (null == timeperiod)
            timeperiod = "last60minutes";

        Date fromDate = null;
        Date toDate = null;
        long dataPoints = 60;

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        // Reset the day fields so we're at the beginning of the day.
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        // Compute "this week" by resetting the day of the week to the first day of the week
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        Date thisWeekStart = cal.getTime();
        Date thisWeekEnd = now;
        // Compute last week - start with the end boundary which is 1 millisecond before the start of this week
        cal.add(Calendar.MILLISECOND, -1);
        Date lastWeekEnd = cal.getTime();
        // Add that millisecond back, subtract 7 days for the start boundary of "last week"
        cal.add(Calendar.MILLISECOND, 1);
        cal.add(Calendar.DAY_OF_YEAR, -7);
        Date lastWeekStart = cal.getTime();
        // Reset the time
        cal.setTime(now);
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        // Reset to the 1st day of the month, make the the start boundary for "this month"
        cal.set(Calendar.DAY_OF_MONTH, cal.getMinimum(Calendar.DAY_OF_MONTH));
        Date thisMonthStart = cal.getTime();
        Date thisMonthEnd = now;
        // Compute last month
        cal.add(Calendar.MILLISECOND, -1);
        Date lastMonthEnd = cal.getTime();
        cal.add(Calendar.MILLISECOND, 1);
        cal.add(Calendar.MONTH, -1);
        Date lastMonthStart = cal.getTime();
        // Compute last 3 months
        cal.setTime(now);
        cal.add(Calendar.MONTH, -2);
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date last3MonthsStart = cal.getTime();
        Date last3MonthsEnd = now;
        // Compute last 7 days:
        cal.setTime(now);
        cal.add(Calendar.DAY_OF_YEAR, -6);
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date last7DaysStart = cal.getTime();
        Date last7DaysEnd = now;
        // Compute last 60 minutes;
        cal.setTime(now);
        cal.add(Calendar.MINUTE, -60);
        Date last60MinutesStart = cal.getTime();
        Date last60MinutesEnd = now;
        // Compute last 24 hours;
        cal.setTime(now);
        cal.add(Calendar.HOUR, -23);
        Date last24HoursStart = cal.getTime();
        Date last24HoursEnd = now;
        // Done, reset the cal internal date to now
        cal.setTime(now);

        if ("thisweek".equals(timeperiod)) {
            fromDate = thisWeekStart;
            toDate = thisWeekEnd;
            dataPoints = 7;
        } else if ("last7days".equals(timeperiod)) {
            fromDate = last7DaysStart;
            toDate = last7DaysEnd;
            dataPoints = 7;
        } else if ("lastweek".equals(timeperiod)) {
            fromDate = lastWeekStart;
            toDate = lastWeekEnd;
            dataPoints = 7;
        } else if ("thismonth".equals(timeperiod)) {
            fromDate = thisMonthStart;
            toDate = thisMonthEnd;
            dataPoints = 30;
        } else if ("lastmonth".equals(timeperiod)) {
            fromDate = lastMonthStart;
            toDate = lastMonthEnd;
            dataPoints = 30;
        } else if ("last3months".equals(timeperiod)) {
            fromDate = last3MonthsStart;
            toDate = last3MonthsEnd;
            dataPoints = (long)Math.ceil((toDate.getTime() - fromDate.getTime()) / WEEK);
        } else if ("last60minutes".equals(timeperiod)) {
            fromDate = last60MinutesStart;
            toDate = last60MinutesEnd;
            dataPoints = 60;
        } else if ("last24hours".equals(timeperiod)) {
            fromDate = last24HoursStart;
            toDate = last24HoursEnd;
            dataPoints = 48;
        } else {
            String[] dates = timeperiod.split("to");
            if (dates.length > 0) {
                DateFormat formDateFormatter = new SimpleDateFormat("MM/dd/yy");
                String fromDateParam = dates[0];
                String toDateParam = dates[1];
                if (fromDateParam != null) {
                    try {
                        fromDate = formDateFormatter.parse(fromDateParam);
                    }
                    catch (Exception e) {
                        // ignore formatting exception
                    }
                }
                if (toDateParam != null) {
                    try {
                        toDate = formDateFormatter.parse(toDateParam);
                        // Make this date be the end of the day (so it's the day *inclusive*, not *exclusive*)
                        Calendar adjusted = Calendar.getInstance();
                        adjusted.setTime(toDate);
                        adjusted.set(Calendar.HOUR_OF_DAY, 23);
                        adjusted.set(Calendar.MINUTE, 59);
                        adjusted.set(Calendar.SECOND, 59);
                        adjusted.set(Calendar.MILLISECOND, 999);
                        toDate = adjusted.getTime();
                    }
                    catch (Exception e) {
                        // ignore formatting exception
                    }
                }
                dataPoints = discoverDataPoints(fromDate, toDate);
            }
        }

        // default to last 60 minutes
        if (null == fromDate && null==toDate) {
            return new long[] {last60MinutesStart.getTime(), last60MinutesEnd.getTime(), dataPoints};
        } else if (null == fromDate) {
            return new long[] {0, toDate.getTime(), dataPoints};
        } else if (null == toDate) {
            return new long[] {fromDate.getTime(), now.getTime(), dataPoints};
        } else {
            return new long[] {fromDate.getTime(), toDate.getTime(), dataPoints};
        }
    }

    private static int discoverDataPoints(Date fromDate, Date toDate) {
        long delta = toDate.getTime() - fromDate.getTime();
        if(delta > YEAR) {
            return (int)(delta / MONTH);
        }
        else if (delta > 2 * MONTH) {
            return (int)(delta / WEEK);
        }
        else {
            return (int)(delta / DAY);
        }
    }

    public static class GraphDefinition {
        public static final GraphDefinition standard_light;
        public static final GraphDefinition standard_dark;
        private Color[] inlineColors;
        private Color[] outlineColors;

        static {
            standard_light = new GraphDefinition(
                    new Color[]{new Color(246, 171, 77), getColor("B1C3D9")},
                    new Color[]{new Color(217, 126, 12), getColor("17518C")}
                    );

            standard_dark = new GraphDefinition(
                    new Color[]{new Color(116, 128, 141), getColor("74808D")},
                    new Color[]{new Color(116, 128, 141), getColor("74808D")}
                    );
        }

        public static GraphDefinition getDefinition(String colorscheme) {
            GraphDefinition graphDef = GraphDefinition.standard_light;
            if (colorscheme != null && colorscheme.equalsIgnoreCase("dark")) {
                graphDef = GraphDefinition.standard_dark;
            }
            return graphDef;
        }

        public GraphDefinition(Color[] inlineColors, Color[] outlineColors) {
            this.inlineColors = inlineColors;
            this.outlineColors = outlineColors;
        }

        public Color getInlineColor(int index) {
            return inlineColors[index];
        }

        public Color[] getInlineColors() {
            return inlineColors;
        }

        public Color getOutlineColor(int index) {
            return outlineColors[index];
        }

        public Color[] getOutlineColors() {
            return outlineColors;
        }
    }
}
