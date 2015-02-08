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

package no.kantega.aksess.mojo;

import no.kantega.aksess.JettyStarter;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mojo that start the application in Jetty
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.RUNTIME, requiresDependencyCollection = ResolutionScope.RUNTIME)
public class RunMojo extends AbstractMojo {

    /**
     * Parameter specifying the webapp dir,
     * Default is ${basedir}/src/webapp for historical reasons.
     * ${basedir}/src/main/webapp will also be tried, if ${basedir}/src/webapp does not exits.
     * So this parameter is not necessary to specify if either of those paths are the actual path.
     */
    @Parameter(defaultValue = "${basedir}/src/webapp")
    private File srcDir;

    /**
     * Parameter to get the standard Maven webapp dir, ${basedir}/src/main/webapp
     */
    @Parameter(defaultValue = "${basedir}/src/main/webapp", readonly = true)
    private File stdM2SrcDir;

    /**
     * Where the exploded war is located. Default ${project.build.directory}/${project.build.finalName}
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}")
    private File webappDir;

    /**
     * Where to put kantega-dir, default ${project.build.directory}/kantega-dir
     */
    @Parameter(defaultValue = "${project.build.directory}/kantega-dir")
    private File kantegaDir;

    /**
     * Context path to start the application with, default /${project.artifactId}
     */
    @Parameter(defaultValue = "/${project.artifactId}")
    private String contextPath;

    /**
     * Project package
     */
    @Parameter(defaultValue = "${project.groupId}", readonly = true)
    private String projectPackage;

    /**
     * Workdir for jetty.
     */
    @Parameter(defaultValue = "${project.build.Directory}/aksessrun/jettywork")
    private File jettyWorkDir;


    /**
     * Location of aksess-webapp.conf, default ${basedir}/src/conf/aksess-webapp.conf
     */
    @Parameter(defaultValue = "${basedir}/src/conf/aksess-webapp.conf")
    private File aksessConfigFile;

    /**
     * Should a browser be attempted to be opened when application is started? Default true.
     */
    @Parameter(defaultValue = "true", property = "openBrowser")
    private boolean openBrowser;

    /**
     * The directory containing generated classes.
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File classesDirectory;

    /**
     * The directory containing Flyt CMS. If parameter is configured, web-artifact directory will de overlayed.
     * This enabling editing jsp and other resources during runtime.
     */
    @Parameter(property = "aksessHome")
    private File aksessHome;

    /**
     * Port to start application on. 8080 is default, will incrementally try new ports if 8080 is busy.
     */
    @Parameter(defaultValue = "8080")
    private int port;

    private JettyStarter starter;

    @Component
    private MavenProject project;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if(!aksessConfigFile.exists()) {
            throw new MojoExecutionException("aksessConfigFile does not exist: " + aksessConfigFile.getAbsolutePath());
        }

        File logbackConfig = new File(kantegaDir, "conf/logback.xml");
        System.setProperty("logback.configurationFile", logbackConfig.getAbsolutePath());

        getLog().info("Running Jetty");

        starter = new JettyStarter();

        if(System.getProperty("os.name").toLowerCase().contains("win")) {
            starter.addContextParam("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "false");
        }

        if(aksessHome != null) {
            File aksessSrc = getAksessHome();
            getLog().info("Using aksessHome " + aksessSrc.getAbsolutePath());
            starter.setAksessDir(aksessSrc);
        }

        List<File> dependencyFiles = new ArrayList<>();
        dependencyFiles.add(classesDirectory);
        dependencyFiles.addAll(getDependencyFiles());

        starter.setPort(port);
        starter.setContextPath(contextPath);
        starter.setSrcDir(getSrcDir());
        starter.addContextParam("kantega.appDir", kantegaDir.getAbsolutePath());

        starter.setDependencyFiles(dependencyFiles);
        jettyWorkDir.mkdirs();
        starter.setWorkDir(jettyWorkDir);

        configureStarter(starter);

        addRestartConsoleScanner();

        try {
            starter.start();
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private File getAksessHome() throws MojoExecutionException {
        File aksessSrc  = new File(aksessHome, "modules/webapp/src/main/resources/META-INF/resources");
        File legacyAksessSrc  = new File(aksessHome, "modules/webapp/src/resources/META-INF/resources");
        if(aksessSrc.exists()) {
            return aksessSrc;
        } else if (legacyAksessSrc.exists()) {
            return legacyAksessSrc;
        } else {
            getLog().error("aksessHome " + aksessSrc.getAbsolutePath() + " does not exist!");
            throw new MojoExecutionException("aksessHome specified, but neither " + aksessSrc.getAbsolutePath() +
                    " nor " + legacyAksessSrc.getAbsolutePath() + " exists!");
        }
    }

    private File getSrcDir() throws MojoExecutionException {
        if(srcDir.exists()){
            return srcDir;
        } else if (stdM2SrcDir.exists()){
            return stdM2SrcDir;
        } else {
            throw new MojoExecutionException("Neither src/webapp nor src/main/webapp exists!");
        }
    }

    private void addRestartConsoleScanner() {
        Thread t = new ConsoleScanner() {
            protected void restart() {
                try {
                    getLog().info("Restarting webapp on request");
                    starter.restart();
                } catch (Exception e) {
                    getLog().error("Error restarting webapp", e);
                }
            }
        };
        t.start();
    }

    protected void configureStarter(JettyStarter starter) {
        starter.setOpenBrowser(openBrowser);
    }

    abstract class ConsoleScanner extends Thread {

        @Override
        public void run() {
            while(true) {
                try {
                    checkInput();
                    Thread.sleep(100);
                } catch (IOException | InterruptedException e) {
                    getLog().error(e);
                }
            }
        }

        private void checkInput() throws IOException {

            char p = '0';

            while (System.in.available() > 0) {
                int inputByte = System.in.read();
                if (inputByte >= 0)
                {
                    char c = (char)inputByte;
                    if (c == '\n' && p == 'r') {
                        restart();
                    }
                    p = c;
                }
            }
        }

        private void doRestart() {
            restart();
            emptyBuffer();
        }
        protected abstract void
        restart();
    }

    private void emptyBuffer() {

        try
        {
            while (System.in.available() > 0)
            {
                // System.in.skip doesn't work properly. I don't know why
                long available = System.in.available();
                for (int i = 0; i < available; i++)
                {
                    if (System.in.read() == -1)
                    {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            getLog().error("Error emptying buffer", e);
        }
    }

    private List<File> getDependencyFiles () throws MojoExecutionException {
        List<File> dependencyFiles = new ArrayList<>();

        for (Artifact artifact : project.getArtifacts()) {
            // Include runtime and compile time libraries, and possibly test libs too
            if (artifact.getType().equals("war")) {
                continue;
            }

            if (Artifact.SCOPE_PROVIDED.equals(artifact.getScope()))
                continue; //never add dependencies of scope=provided to the webapp's classpath (see also <useProvidedScope> param)

            dependencyFiles.add(artifact.getFile());
            if (getLog().isDebugEnabled()) {
                getLog().debug("Adding artifact " + artifact.getFile().getName() + " with scope " + artifact.getScope() + " for WEB-INF/lib ");
            }
        }

        return dependencyFiles;
    }
}
