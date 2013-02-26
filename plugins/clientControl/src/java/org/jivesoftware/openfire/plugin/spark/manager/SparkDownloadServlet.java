/**
 * $RCSfile: ,v $
 * $Revision: 1.0 $
 * $Date: 2005/05/25 04:20:03 $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.plugin.spark.manager;

import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.util.JiveGlobals;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides support for downloading the  Jive Spark IM client.
 * (<a href="http://www.igniterealtime.org/projects/spark/index.jsp">Spark</a>).<p>
 * <p/>
 *
 * @author Derek DeMoro
 */
public class SparkDownloadServlet extends HttpServlet {

    @Override
	public void init(ServletConfig config) throws ServletException {
        super.init(config);
        AuthCheckFilter.addExclude("clientcontrol/getspark");
    }

    @Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Handle Version Request. Only handle windows and mac version at this time.
        final String clientBuild = request.getParameter("client");
        final String os = request.getParameter("os");

        if (clientBuild != null) {
            sendClientBuild(response, clientBuild);
        }
        else {
            File buildDir = new File(JiveGlobals.getHomeDirectory(), "enterprise/spark");
            if (!buildDir.exists()) {
                buildDir.mkdirs();
            }

            final List<String> fileList = new ArrayList<String>();

            File[] list = buildDir.listFiles();
            int no = list != null ? list.length : 0;
            for (int i = 0; i < no; i++) {
                File clientFile = list[i];
                if (clientFile.getName().endsWith(".exe") && "windows".equals(os)) {
                    fileList.add(clientFile.getName());
                }
                else if (clientFile.getName().endsWith(".dmg") && "mac".equals(os)) {
                    fileList.add(clientFile.getName());
                }
                else if(clientFile.getName().endsWith(".tar.gz") && "linux".equals(os)){
                    fileList.add(clientFile.getName());
                }
            }

            Collections.sort(fileList);

            if(fileList.size() > 0){
                int size = fileList.size();
                String fileName = fileList.get(size - 1);
                sendClientBuild(response, fileName);
            }
        }
    }

    private void sendClientBuild(HttpServletResponse resp, final String clientBuild) throws IOException {
        // Determine release location. All builds should be put into the document_root/releases directory
        // and be named appropriatly (ex. spark_1_0_0.exe, spark_1_0_1.dmg)
        File clientFile = new File(JiveGlobals.getHomeDirectory(), "enterprise/spark/" + clientBuild);

        // Set content size
        resp.setContentType("application/octet-stream");
        resp.setHeader("Content-Disposition", "attachment; filename=" + clientBuild);
        resp.setContentLength((int)clientFile.length());

        // Open the file and output streams
        FileInputStream in = new FileInputStream(clientFile);
        OutputStream out = resp.getOutputStream();

        // Copy the contents of the file to the output stream
        byte[] buf = new byte[1024];
        int count;
        while ((count = in.read(buf)) >= 0) {
            out.write(buf, 0, count);
        }
        in.close();
        out.close();
    }
}
