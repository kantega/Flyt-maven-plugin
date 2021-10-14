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


import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.annotations.ServletContainerInitializersStarter;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.maven.plugin.JettyWebAppContext;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.util.resource.JarResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.Configuration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;


public class JettyStarter {

    private File srcDir;
    private File webappDir;

    private String contextPath;
    private File webXml;
    private File aksessDir;
    private Map<String, String> props = new HashMap<>();
    private File workDir;

    private List<File> dependencyFiles = emptyList();
    private boolean openBrowser;
    private JettyWebAppContext context;
    private int port = 8080;
    private Server server;
    private boolean joinServer = true;
    private boolean throwUnavailableOnStartupException = false;

    public static void main(String[] args) throws Exception {
        final JettyStarter js = new JettyStarter();

        for (String arg : args) {
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
        System.setProperty("development", "true");

        context = new JettyWebAppContext();

        context.getServerClasspathPattern()
            .add(
                "-org.apache.commons.logging.",
                "-org.apache.jasper.",
                "-org.apache.tomcat.",
                "-org.apache.el.",
                "org.apache.",
                "org.slf4j."
            );

        if(webXml != null) {
            context.setDescriptor(webXml.getAbsolutePath());
        }
        List<String> bases = new ArrayList<>();
        bases.add(srcDir.getAbsolutePath());
        if(aksessDir != null) {
            bases.add(aksessDir.getAbsolutePath());
        }
        if(webappDir != null) {
            bases.add(webappDir.getAbsolutePath());
        }

        context.getInitParams().putAll(props);

        if(workDir != null) {
            context.setTempDirectory(workDir);
        }
        System.out.println("Starting with resource bases: " + bases);
        if(bases.size() == 1 && new File(bases.get(0)).isFile()) {
            context.setWar(bases.get(0));
        } else {
            context.setBaseResource(new ResourceCollection(getResources(bases)));
        }
        context.setContextPath(contextPath);

        context.setWebInfLib(dependencyFiles);
        context.setThrowUnavailableOnStartupException(throwUnavailableOnStartupException);

        int firstport = port;
        while (port < firstport+10) {
            try {
                new ServerSocket(port).close();
                break;
            } catch (java.net.BindException be) {

                int nextPort = port+1;
                System.out.println("Error starting server on port "+port+", trying port " + nextPort);
                port++;
            }
        }

        context.setAttribute("org.eclipse.jetty.containerInitializers", jspInitializers());
        context.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());
        context.addBean(new ServletContainerInitializersStarter(context), true);

        server = new Server(port);

        server.addBean(new Configuration.ClassList(
            new String[]{
                "org.eclipse.jetty.maven.plugin.MavenWebInfConfiguration",
                "org.eclipse.jetty.webapp.WebXmlConfiguration",
                "org.eclipse.jetty.webapp.MetaInfConfiguration",
                "org.eclipse.jetty.webapp.FragmentConfiguration",
                "org.eclipse.jetty.webapp.JettyWebXmlConfiguration"
            }
        ));

        HandlerCollection collection = new HandlerCollection();
        collection.setHandlers(new Handler[] {new ContextPathHandler(), context});
        server.setHandler(collection);
        server.start();

        if(openBrowser) {
            openUrl("http://localhost:" + port + contextPath);
        }

        if(joinServer) {
            server.join();
        }
    }

    private static List<ContainerInitializer> jspInitializers() {
        JettyJasperInitializer sci = new JettyJasperInitializer();
        ContainerInitializer initializer = new ContainerInitializer(sci, null);
        List<ContainerInitializer> initializers = new ArrayList<>();
        initializers.add(initializer);
        return initializers;
    }
    public void setJoinServer(boolean joinServer) {
        this.joinServer = joinServer;
    }

    @SuppressWarnings("unchecked")
    private  void openUrl(String url) {
        String osName = System.getProperty("os.name");
        try {
            if (osName.startsWith("Mac OS")) {
                Class fileMgr = Class.forName("com.apple.eio.FileManager");
                Method openURL = fileMgr.getDeclaredMethod("openURL", String.class);
                openURL.invoke(null, url);
            }
            else if (osName.startsWith("Windows"))
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            else { //assume Unix or Linux
                Process xdgOpen = Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                if (xdgOpen.waitFor() > 0) {
                    throw new Exception(String.format("Could not open browser. xdg-open returned %d. See xdg-open manpage for details.", xdgOpen.exitValue()));
                }
            }
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage());
        }
    }
    private Resource[] getResources(List<String> bases) throws IOException {
        Resource[] resources = new Resource[bases.size()];
        for(int i  = 0; i < bases.size(); i++) {
            String resourceRef = bases.get(i);
            File file = new File(resourceRef);
            if(file.exists()) {
                if(file.isDirectory()) {
                    resources[i] = Resource.newResource(resourceRef);
                } else {
                    File extractDir = new File(workDir, file.getName());
                    final JarResource warResource = (JarResource) Resource.newResource(file.toURI().toURL());
                    warResource.copyTo(extractDir);
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

    public void setDependencyFiles(List<File> dependencyFiles) {
        this.dependencyFiles = dependencyFiles;
    }

    public void setOpenBrowser(boolean openBrowser) {
        this.openBrowser = openBrowser;
    }

    public void setThrowUnavailableOnStartupException(boolean throwUnavailableOnStartupException) {
        this.throwUnavailableOnStartupException = throwUnavailableOnStartupException;
    }

    public void restart() throws Exception {
        context.stop();
        context.start();
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void stop() throws Exception {
        server.stop();
    }

    public int getPort() {
        return port;
    }

    public String getContextPath() {
        return contextPath;
    }

    private class ContextPathHandler extends AbstractHandler {
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            if("/oa-maven-plugin-context-path".equals(target)) {
                response.setContentType("text/plain;charset=utf-8");
                response.setContentLength(contextPath.getBytes().length);
                response.getOutputStream().write(contextPath.getBytes());
                baseRequest.setHandled(true);
            }
        }
    }
}
