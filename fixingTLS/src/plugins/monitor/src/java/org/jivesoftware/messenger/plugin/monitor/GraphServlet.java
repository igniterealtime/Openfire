/**
 * 
 */
package org.jivesoftware.messenger.plugin.monitor;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.imageio.ImageIO;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jrobin.core.RrdDb;
import org.jrobin.core.RrdDbPool;
import org.jrobin.core.RrdException;
import org.jrobin.graph.RrdGraph;
import org.jrobin.graph.RrdGraphDef;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public class GraphServlet extends HttpServlet {

    /**
     * @see javax.servlet.http.HttpServlet#service(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("image/png");
        ServletOutputStream out = response.getOutputStream();
        response.addHeader("Cache-Control", "no-cache");
        try {
            createPNGGraph(request, out);
        } catch (RrdException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
    
    Color[] colors = new Color[]{new Color(102, 204, 204),
            new Color(102,153,204),
            new Color(102,102,204),
            new Color(153,102,204),
            new Color(102,204,153),
            new Color( 61,184,184),
            new Color( 46,138,138),
            new Color(204,102,204),
            new Color(102,204,102),
            new Color(138, 46, 46),
            new Color(184, 61, 61),
            new Color(204,102,153),
            new Color(153,204,102),
            new Color(204,204,102),
            new Color(204,153,102),
            new Color(204,102,102)};

    
    /**
     * @param request
     * @return
     * @throws RrdException 
     */
    private void createPNGGraph(HttpServletRequest request, OutputStream out) throws IOException, RrdException {
        int width;
        int height;
        
        try {
            int w = Integer.parseInt(request.getParameter("width"));
            width = w <= 800 ? w : 800;
        } catch (NumberFormatException e) {
            width = 400;
        }
        
        try {
            int h = Integer.parseInt(request.getParameter("height"));
            height = h <= 600 ? h : 600;
        } catch (NumberFormatException e) {
            height = 300;
        }
        
        try {
            String title = request.getParameter("title");
            String store = request.getParameter("store");
            
            String objectName = request.getParameter("oname");
            Boolean aa = Boolean.parseBoolean(request.getParameter("aa"));
            
            RrdGraphDef def = new RrdGraphDef();
            def.setAntiAliasing(aa);
            if(title != null)
                def.setTitle(title);
            
            RrdDbPool pool = RrdDbPool.getInstance();
            RrdManager manager = RrdManager.listStores().get(store);
            String filename = manager.getFileName(new ObjectName(objectName));
            
            RrdDb db = pool.requestRrdDb(filename);
            String ref = "input";
            int count = 0;
            for(String ds : db.getDsNames()) {
                String id = ref + count++;
                def.datasource(id, filename, ds, "AVERAGE");
                def.line(id, colors[count], ds);
            }
            
            RrdGraph graph = new RrdGraph(def);
            byte[] output;
            if(request.getParameter("width") == null) {
                output = graph.getPNGBytes();
            } else {
                output = graph.getPNGBytes(width, height);
            }
            
            /**
             * write out the byte array to the client.
             */
            ByteArrayInputStream bis = new ByteArrayInputStream(output);
            while(bis.available() > 0) {
                out.write(bis.read());
            }
        } catch (MalformedObjectNameException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NumberFormatException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (NullPointerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (RrdException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = (Graphics2D) bufferedImage.getGraphics();
        g2d.setBackground(Color.white);
//        g2d.setBackground(new Color(0,0,0,255));
//        GradientPaint gpaint = new GradientPaint(0, height / 2, Color.white, width, height / 2, Color.gray);
//        Paint originalPaint = g2d.getPaint();
//        g2d.setPaint(gpaint);
        g2d.fillRect(0,0,width,height);
        String error = "An error has occured.";
        int errorWidth = g2d.getFontMetrics().stringWidth(error);
//        g2d.setPaint(originalPaint);
        
        g2d.setColor(Color.black);
        g2d.drawString(error, width / 2 - errorWidth / 2, height / 2);
        g2d.dispose();
        ImageIO.write(bufferedImage, "png", out);
    }


    /**
     * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
     */
    @Override
    public void init(ServletConfig arg0) throws ServletException {
        // TODO Auto-generated method stub
        super.init(arg0);
    }

}
