/*
 * Copyright 2009 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.kantega.aksess;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.webapp.WebAppContext;
import org.mortbay.resource.ResourceCollection;
import org.mortbay.resource.Resource;
import org.mortbay.resource.JarResource;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.net.MalformedURLException;
import java.net.URL;
import java.lang.reflect.Method;

/**
 * Created by IntelliJ IDEA.
 * User: bjorsnos
 * Date: Jan 13, 2009
 * Time: 2:58:58 PM
 * To change this template use File | Settings | File Templates.
 */
public class JettyStarter {

    private File srcDir;
    private File webappDir;

    private String contextPath;
    private File webXml;
    private File aksessDir;
    private Map props = new HashMap();
    private File workDir;
    private List additinalBases = new ArrayList();
    private List<File> dependencyFiles;
    private boolean openBrowser;
    private WebAppContext context;

    public static void main(String[] args) throws Exception {
        final JettyStarter js = new JettyStarter();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            System.out.println("arg: " + arg);
        }
        if(args.length != 4) {
            usage();
        }

        final File srcDir = new File(args[0]);
        if(!srcDir.exists()) {
            usage("srcDir " + srcDir.getAbsoluteFile() + " doesn't exist");
        }
        js.setSrcDir(srcDir);

        final File webappDir = new File(args[1]);
        if(!webappDir.exists()) {
            usage("webappDir " + srcDir.getAbsoluteFile() + " doesn't exist");
        }
        js.setWebappDir(webappDir);

        final File webXml = new File(args[2]);
        if(!webXml.exists()) {
            usage("webXml file " + webXml.getAbsoluteFile() + " doesn't exist");
        }

        js.setWebXml(webXml);

        String aksessHome = System.getProperty("aksess.home");

        if(aksessHome != null) {
            File aksessHomeFile = new File(aksessHome);
            if(aksessHomeFile.isDirectory() && aksessHomeFile.exists()) {
                System.out.println("Using aksess dir " + aksessHomeFile);
                js.setAksessDir(aksessHomeFile);
            }

        }


        js.setContextPath(args[3]);
        js.start();
    }

    private static void usage() {
        usage(null);
    }

    private static void usage(String message) {
        if(message != null) {
            System.out.println(message);
        }
        System.out.println("Usage: " + JettyStarter.class.getName() + " <srcDir> <webappDir> <web.xml> <contextPath>");
        System.exit(1);
    }

    public void start() throws Exception {
        Server server = new Server(8080);

        context = new WebAppContext();
        context.setDescriptor(webXml.getAbsolutePath());
        List bases = new ArrayList();
        bases.add(srcDir.getAbsolutePath());
        if(aksessDir != null) {
            bases.add(aksessDir.getAbsolutePath());
        }
        if(webappDir != null) {
            bases.add(webappDir.getAbsolutePath());
        }
        bases.addAll(additinalBases);

        context.getInitParams().putAll(props);

        if(workDir != null) {
            context.setTempDirectory(workDir);
        }
        System.out.println("Starting with resource bases: " + bases);
        context.setBaseResource(new ResourceCollection(getResources(bases)));
        context.setContextPath(contextPath);

        if(dependencyFiles != null) {
            String extra = "";
            for(File file : dependencyFiles) {
                extra += file.getAbsolutePath() +";";
            }
            context.setExtraClasspath(extra);
        }
        server.setHandler(context);
        
        server.start();
        if(openBrowser) {
            openUrl("http://localhost:8080" + contextPath);
        }
        server.join();

    }

    private  void openUrl(String url) {
        String osName = System.getProperty("os.name");
        try {
            if (osName.startsWith("Mac OS")) {
                Class fileMgr = Class.forName("com.apple.eio.FileManager");
                Method openURL = fileMgr.getDeclaredMethod("openURL",
                        new Class[] {String.class});
                openURL.invoke(null, new Object[] {url});
            }
            else if (osName.startsWith("Windows"))
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            else { //assume Unix or Linux
                String[] browsers = {
                        "firefox", "opera", "konqueror", "epiphany", "mozilla", "netscape" };
                String browser = null;
                for (int count = 0; count < browsers.length && browser == null; count++)
                    if (Runtime.getRuntime().exec(
                            new String[] {"which", browsers[count]}).waitFor() == 0)
                        browser = browsers[count];
                if (browser == null)
                    throw new Exception("Could not find web browser");
                else
                    Runtime.getRuntime().exec(new String[] {browser, url});
            }
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage());
        }
    }
    private Resource[] getResources(List bases) throws IOException {
        Resource[] resources = new Resource[bases.size()];
        for(int i  = 0; i < bases.size(); i++) {
            String resourceRef = (String) bases.get(i);
            File file = new File(resourceRef);
            if(file.exists()) {
                if(file.isDirectory()) {
                    resources[i] = Resource.newResource(resourceRef);
                } else {
                    File extractDir = new File(workDir, file.getName());
                    final Resource warResource = Resource.newResource(file.toURL());
                    JarResource.extract(warResource, extractDir, false);
                    resources[i] = Resource.newResource(extractDir.getAbsolutePath());
                }
            }
        }
        return resources;
    }

    public void setSrcDir(File srcDir) {
        this.srcDir = srcDir;
    }

    public void setWebappDir(File webappDir) {
        this.webappDir = webappDir;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public void setWebXml(File webXml) {
        this.webXml = webXml;
    }

    public void setAksessDir(File aksessDir) {
        this.aksessDir = aksessDir;
    }

    public void addContextParam(String name, String value) {
        props.put(name, value);
    }

    public void setWorkDir(File workDir) {
        this.workDir = workDir;
    }

    public List getAdditinalBases() {
        return additinalBases;
    }

    public void setDependencyFiles(List<File> dependencyFiles) {
        this.dependencyFiles = dependencyFiles;
    }

    public void setOpenBrowser(boolean openBrowser) {
        this.openBrowser = openBrowser;
    }

    public void restart() throws Exception {
        context.stop();
        context.start();
    }
}
