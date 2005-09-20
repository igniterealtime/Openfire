/**
 * 
 */
package org.jivesoftware.messenger.plugin.meter;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.imageio.ImageIO;
import javax.management.ObjectName;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.jrobin.core.RrdDb;
import org.jrobin.core.RrdDbPool;
import org.jrobin.graph.RrdGraph;
import org.jrobin.graph.RrdGraphDef;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public class GraphServlet extends HttpServlet {

    
    /** The serialVersionUID. */
    private static final long serialVersionUID = 1L;


    /**
     * @param request The Request.
     * @param response The Response.
     * @throws ServletException Throw if unable to process the request due to a Servlet related problem.
     * @throws IOException Thrown if unable to process the request due to an IO related problem.
     * @see javax.servlet.http.HttpServlet#service(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @SuppressWarnings("unused")
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("image/png");
        ServletOutputStream out = response.getOutputStream();
        response.addHeader("Cache-Control", "no-cache");
        createPNGGraph(request, out);
    }
    
    
    /** The colors. */
    private static final Color[] colors = new Color[]{new Color(102, 204, 204),
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
    
    /** The converter. */
    private static final Converter converter = new ColorConverter();

    
    /**
     * @param request The servlet request.
     * @param out The output stream.
     * @throws IOException Thrown if an IO exception occurs.
     */
    private void createPNGGraph(HttpServletRequest request, OutputStream out) throws IOException {
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
        
        String error = "An error has occured.";
        
        try {
            ConvertUtils.register(converter, Color.class);
            
            String store = request.getParameter("store");
            
            String objectName = request.getParameter("oname");
            RrdGraphDef def = new RrdGraphDef();
            
            BeanUtilsBean.getInstance().populate(def, request.getParameterMap());
            
            String[] lines = request.getParameterValues("lines");
            for(String line : lines) {
                String[] field = line.split("::");
                def.line(field[0], parseColor(field[1]), field[2]);
            }
            
            
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
        } catch (Exception e) {
            error += " " + e.getLocalizedMessage();
        } finally {
            ConvertUtils.deregister(Color.class);
        }
        
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = (Graphics2D) bufferedImage.getGraphics();
        g2d.setBackground(Color.white);
//        g2d.setBackground(new Color(0,0,0,255));
//        GradientPaint gpaint = new GradientPaint(0, height / 2, Color.white, width, height / 2, Color.gray);
//        Paint originalPaint = g2d.getPaint();
//        g2d.setPaint(gpaint);
        g2d.fillRect(0,0,width,height);

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

    
    /**
     * @author Noah Campbell
     * @version 1.0
     */
    private static class ColorConverter implements Converter {

        /**
         * @see org.apache.commons.beanutils.Converter#convert(java.lang.Class, java.lang.Object)
         */
        public Object convert(@SuppressWarnings("unused") Class cls, Object input) {
            if(input instanceof String) {
                String s = (String)input;
                return parseColor(s);
            }
            
            return colors[0];
        }        
    }
    
    
    /**
     * Parse a string of format #RRBBGGAA where RR, BB, GG, AA are hex values
     * ranging between 0 (00) and 255 (FF).  AA is optional and can be excluded
     * from the #RRBBGG string.
     * 
     * @param s
     * @return color
     */
    private static Color parseColor(String s) {
        try {
            if( s.startsWith("#") ) {
                int r = Integer.valueOf(s.substring(1, 3), 16);
                int g = Integer.valueOf(s.substring(3, 5), 16);
                int b = Integer.valueOf(s.substring(5, 7), 16);
                int a = 255;
                if(s.length() > 7) {
                    a = Integer.valueOf(s.substring(7, 9), 16);
                }
                
                return new Color(r, g, b, a);
            }
        } catch (RuntimeException e) {
            
        }
        return colors[0];
    }
}
