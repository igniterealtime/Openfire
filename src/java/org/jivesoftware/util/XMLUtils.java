package org.jivesoftware.util;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;


public class XMLUtils {

    /**
     * Handle URL
     */
    final public static Object getObject(URL url) throws Exception {
        InputStream stream = url.openStream();
        XMLDecoder d = new XMLDecoder(stream);
        Object result = d.readObject();
        d.close();
        stream.close();
        return result;
    }

    final public static void writeObject(Object document, URL url) throws Exception {
        final OutputStream out = openOutputStream(url);
        XMLEncoder e = new XMLEncoder(out);
        e.writeObject(document);
        e.close();
    }

    /**
     * Handle Output and InputStreams
     */

    final public static Object getObject(InputStream stream) throws Exception {
        XMLDecoder d = new XMLDecoder(stream);
        Object result = d.readObject();
        d.close();
        stream.close();
        return result;
    }

    final public static Object getObject(String objectStr) throws Exception {
        final ByteArrayInputStream stream = new ByteArrayInputStream(objectStr.getBytes("UTF-8"));

        XMLDecoder d = new XMLDecoder(stream);
        Object result = d.readObject();
        d.close();
        stream.close();
        return result;
    }

    final public static void writeObject(Object obj, OutputStream stream) throws Exception {
        XMLEncoder e = new XMLEncoder(stream);
        e.writeObject(obj);
        e.close();
    }

    /**
     * Handle File handling.
     */
    final public static Object getObject(File file) throws Exception {
        XMLDecoder d = new XMLDecoder(new BufferedInputStream(new FileInputStream(file)));
        Object result = d.readObject();
        d.close();
        return result;
    }

    final public static void writeObject(Object obj, File file) throws Exception {
        XMLEncoder e = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(file)));
        e.writeObject(obj);
        e.close();
    }

    final public static String toDocument(Object obj) throws Exception {
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        final StringWriter stringWriter = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stringWriter);
        XMLEncoder e = new XMLEncoder(s);
        e.writeObject(obj);
        e.close();
        String returnStr = s.toString();

        s.flush();
        s.close();
        printWriter.flush();
        printWriter.close();

        return returnStr;

    }

    public static String toString(Document xmlDocument) {
        try {
            final StringWriter stringWriter = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(stringWriter);
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer();
            DOMSource source = new DOMSource(xmlDocument);
            StreamResult result = new StreamResult(printWriter);
            transformer.transform(source, result);
            stringWriter.close();
            return stringWriter.toString();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }


    public static OutputStream openOutputStream(URL url)
            throws IOException {
        final String path = url.getPath();
        try {
            return new FileOutputStream(path);
        }
        catch (FileNotFoundException e) {
            final File file = url2File(url);
            final File dir = file.getParentFile();
            dir.mkdirs();
            return new FileOutputStream(path);
        }
    }

    private static final File url2File(URL url) {
        final String path = url.getPath();
        final File file = new File(path);
        return file;
    }

    public final static String getEncodedObject(Object o) {
        try {
            String value = toDocument(o);
            value = URLEncoder.encode(value, "UTF-8");
            return value;
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public final static Object getDecodedObject(String object) {
        try {
            object = URLDecoder.decode(object, "UTF-8");
            return getObject(object);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

}
