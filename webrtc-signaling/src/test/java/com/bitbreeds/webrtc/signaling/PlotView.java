package com.bitbreeds.webrtc.signaling;/*
 * Copyright (c) Jonas Waage 30/06/2020
 */

import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.FastScatterPlot;
import org.jfree.chart.ui.ApplicationFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public class PlotView extends ApplicationFrame {

    private final JFreeChart chart;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public PlotView(FastScatterPlot plot) throws HeadlessException {
        super("wat");
        chart = new JFreeChart("Fast Scatter Plot", plot);

        chart.getRenderingHints().put
                (RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        final ChartPanel panel = new ChartPanel(chart, true);
        panel.setPreferredSize(new java.awt.Dimension(500, 270));
        panel.setMinimumDrawHeight(10);
        panel.setMaximumDrawHeight(2000);
        panel.setMinimumDrawWidth(20);
        panel.setMaximumDrawWidth(2000);

        setContentPane(panel);
    }

    public void saveChart(File file) {
        try {
            ChartUtils.saveChartAsPNG(file, chart, 500, 300);
        } catch (IOException e) {
            logger.error("Failed to store chart",e);
        }
    }
}
