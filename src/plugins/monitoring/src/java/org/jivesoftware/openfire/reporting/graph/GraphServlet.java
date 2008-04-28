/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.reporting.graph;

import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.openfire.reporting.stats.StatsViewer;
import com.lowagie.text.*;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.List;
import java.awt.geom.Rectangle2D;
import java.awt.*;
import java.awt.Font;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.stats.Statistic;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.ParamUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jfree.chart.JFreeChart;

/**
 *
 */
public class GraphServlet extends HttpServlet {
    private GraphEngine graphEngine;
    private StatsViewer statsViewer;

    public void init() throws ServletException {
        // load dependencies
        MonitoringPlugin plugin =
                (MonitoringPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("monitoring");
        this.graphEngine = (GraphEngine) plugin.getModule(GraphEngine.class);
        this.statsViewer = (StatsViewer)plugin.getModule(StatsViewer.class);
    }

    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // retrieve parameters
        String statisticKey = request.getParameter("stat");
        String timePeriod = request.getParameter("timeperiod");
        String graphcolor = request.getParameter("color");
        boolean sparkLines = request.getParameter("sparkline") != null;
        boolean pdfFormat = request.getParameter("pdf") != null;
        long[] dateRange = GraphEngine.parseTimePeriod(timePeriod);
        int width;
        int height;

        if (pdfFormat) {
            // PDF A4 page = 595 wide - (50px * 2 margins) = 495
            width = ParamUtils.getIntParameter(request, "width", 495);
            height = ParamUtils.getIntParameter(request, "height", 252);
            JFreeChart[] charts;
            Statistic[] stats;
            if (request.getParameter("pdf").equalsIgnoreCase("all")) {
                String[] statKeys = statsViewer.getAllHighLevelStatKeys();
                List<String> statList = Arrays.asList(statKeys);
                Collections.sort(statList, new Comparator<String>() {
                    public int compare(String stat1, String stat2) {
                        String statName1 = statsViewer.getStatistic(stat1)[0].getName();
                        String statName2 = statsViewer.getStatistic(stat2)[0].getName();
                        return statName1.toLowerCase().compareTo(statName2.toLowerCase());
                    }
                });
                charts = new JFreeChart[statList.size()];
                stats = new Statistic[statList.size()];
                int index = 0;
                for (String statName : statList) {
                    stats[index] = statsViewer.getStatistic(statName)[0];
                    charts[index] = graphEngine.generateChart(statName, width, height, graphcolor, dateRange[0], dateRange[1], (int)dateRange[2]);
                    index++;
                }
            } else {
                charts = new JFreeChart[] {graphEngine.generateChart(statisticKey, width, height, graphcolor, dateRange[0], dateRange[1], (int)dateRange[2])};
                stats = new Statistic[] {statsViewer.getStatistic(statisticKey)[0]};
            }
            writePDFContent(request, response, charts, stats, dateRange[0], dateRange[1], width, height);
        } else {
            byte[] chart;
            if (sparkLines) {
                width = ParamUtils.getIntParameter(request, "width", 200);
                height = ParamUtils.getIntParameter(request, "height", 50);
                chart = graphEngine.generateSparklinesGraph(statisticKey, width, height, graphcolor, dateRange[0], dateRange[1], (int)dateRange[2]);
            }
            else {
                width = ParamUtils.getIntParameter(request, "width", 590);
                height = ParamUtils.getIntParameter(request, "height", 300);
                chart = graphEngine.generateGraph(statisticKey, width, height, graphcolor, dateRange[0], dateRange[1], (int)dateRange[2]);
            }

            writeImageContent(response, chart, "image/png");
        }


    }

    private void writePDFContent(HttpServletRequest request, HttpServletResponse response, JFreeChart charts[], Statistic[] stats, long starttime, long endtime, int width, int height)
            throws IOException
    {

        try {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new PDFEventListener(request));
            document.open();



            int index = 0;
            int chapIndex = 0;
            for (Statistic stat : stats) {

                String serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
                String dateName = JiveGlobals.formatDate(new Date(starttime)) + " - " +
                        JiveGlobals.formatDate(new Date(endtime));
                Paragraph paragraph  = new Paragraph(serverName,
                    FontFactory.getFont(FontFactory.HELVETICA,
                    18, Font.BOLD));
                document.add(paragraph);
                paragraph = new Paragraph(dateName,
                    FontFactory.getFont(FontFactory.HELVETICA,
                    14, Font.PLAIN));
                document.add(paragraph);
                document.add(Chunk.NEWLINE);
                document.add(Chunk.NEWLINE);

                Paragraph chapterTitle = new Paragraph(++chapIndex + ". " + stat.getName(),
                    FontFactory.getFont(FontFactory.HELVETICA, 16,
                    Font.BOLD));

                document.add(chapterTitle);
                // total hack: no idea what tags people are going to use in the description
                // possibly recommend that we only use a <p> tag?
                String[] paragraphs = stat.getDescription().split("<p>");
                for (String s : paragraphs) {
                    Paragraph p = new Paragraph(s);
                    document.add(p);
                }
                document.add(Chunk.NEWLINE);

                PdfContentByte contentByte = writer.getDirectContent();
                PdfTemplate template = contentByte.createTemplate(width, height);
                Graphics2D graphs2D = template.createGraphics(width, height, new DefaultFontMapper());
                Rectangle2D rectangle2D = new Rectangle2D.Double(0, 0, width, height);
                charts[index++].draw(graphs2D, rectangle2D);
                graphs2D.dispose();
                float x = (document.getPageSize().width() / 2) - (width / 2);
                contentByte.addTemplate(template, x, writer.getVerticalPosition(true) - height);
                document.newPage();
            }

            document.close();

            // setting some response headers
            response.setHeader("Expires", "0");
            response.setHeader("Cache-Control", "must-revalidate, post-check=0, pre-check=0");
            response.setHeader("Pragma", "public");
            // setting the content type
            response.setContentType("application/pdf");
            // the contentlength is needed for MSIE!!!
            response.setContentLength(baos.size());
            // write ByteArrayOutputStream to the ServletOutputStream
            ServletOutputStream out = response.getOutputStream();
            baos.writeTo(out);
            out.flush();
        } catch (DocumentException e) {
            Log.error("error creating PDF document: " + e.getMessage());

        }
    }

    private static void writeImageContent(HttpServletResponse response, byte[] imageData, String contentType)
            throws IOException
    {
        ServletOutputStream os = response.getOutputStream();
        response.setContentType(contentType);
        os.write(imageData);
        os.flush();
        os.close();
    }

    class PDFEventListener extends PdfPageEventHelper {
        private HttpServletRequest request;
        public PDFEventListener(HttpServletRequest request) {
            this.request = request;
        }
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();

            try {
                cb.setColorStroke(new Color(156,156,156));
                cb.setLineWidth(2);
                cb.moveTo(document.leftMargin(), document.bottomMargin() + 32);
                cb.lineTo(document.getPageSize().width() - document.rightMargin(), document.bottomMargin() + 32);
                cb.stroke();

                Image gif = Image.getInstance("http://" + request.getServerName() +
                    ":" + request.getServerPort() + "/plugins/monitoring/images/pdf_generatedbyof.gif");
                cb.addImage(gif, 221, 0, 0, 28, (int)document.leftMargin(), (int)document.bottomMargin());

            } catch (Exception e) {
                Log.error("error drawing PDF footer: " + e.getMessage());
            }
            cb.saveState();
            
        }
    }


}
