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
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @goal run
 * @execute phase="test"
 * @requiresDependencyResolution runtime
 *
 */
public class RunMojo extends AbstractMojo {

    /**
     * @parameter expression="${basedir}/src/webapp"
     * @readonly
     */
    private File srcDir;

    /**
     * @parameter expression="${basedir}/src/main/webapp"
     * @readonly
     */
    private File stdM2SrcDir;

    /**
     * @parameter expression="${project.build.directory}/${project.build.finalName}"
     */
    private File webappDir;

    /**
     * @parameter expression="${project.build.directory}/kantega-dir"
     */
    private File kantegaDir;

    /**
     * @parameter expression="/${project.artifactId}"
     */
    private String contextPath;

    /**
     * @parameter expression="${project.groupId}"
     * @readonly
     */
    private String projectPackage;

    /**
     * @parameter default-value="${project.build.Directory}/aksessrun/jettywork"
     */
    private File jettyWorkDir;


    /**
     * @parameter default-value="${basedir}/src/conf/aksess-webapp.conf"
     */
    private File aksessConfigFile;

    /**
     * Path to the module containing all java files. E.g. ../core
     * @parameter
     * @readonly
     */
    private File coreModulePath;

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;


    /** @component */
    protected org.apache.maven.artifact.factory.ArtifactFactory artifactFactory;

    /** @component */
    protected org.apache.maven.artifact.resolver.ArtifactResolver resolver;

    /**@parameter expression="${localRepository}" */
    protected org.apache.maven.artifact.repository.ArtifactRepository localRepository;

    /** @parameter expression="${project.remoteArtifactRepositories}" */
    protected java.util.List remoteRepositories;

    /** @component */
    protected ArtifactMetadataSource artifactMetadataSource;


    /**
     * The directory containing generated classes.
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     * @readonly
     */
    private File classesDirectory;

    /**
     * The directory containing generated classes.
     *
     * @parameter expression="${aksessHome}"
     */
    private File aksessHome;

    /** @component */
    private MavenProjectBuilder mavenProjectBuilder;

    /**
     * Exclude the files matching the given pattern from runtime classpath.
     * @parameter
     */
    private List<String> excludes;

    /**
     * @parameter expression="${port}" default-value="8080"
     */
    private int port;
    private JettyStarter starter;

    /**
     * The artifacts for the project.
     *
     * @parameter expression="${project.artifacts}"
     * @readonly
     */
    protected Set<Artifact> projectArtifacts;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if(!aksessConfigFile.exists()) {
            throw new MojoExecutionException("aksessConfigFile does not exist: " + aksessConfigFile.getAbsolutePath());
        }

        File logbackConfig = new File(kantegaDir, "conf/logback.xml");
        System.setProperty("logback.configurationFile", logbackConfig.getAbsolutePath());

        getLog().info("Running Jetty");

        starter = new JettyStarter();

        starter.addContextParam("no.kantega.publishing.setup.SetupServlet.CONFIG_SOURCE", aksessConfigFile.getAbsolutePath());

        if(System.getProperty("os.name").toLowerCase().contains("win")) {
            starter.addContextParam("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "false");
        }

        if(aksessHome != null) {
            File aksessSrc  = new File(aksessHome, "modules/webapp/src/resources/META-INF/resources");
            if(aksessSrc.exists()) {
                getLog().info("Using aksessHome " + aksessSrc.getAbsolutePath());
                starter.setAksessDir(aksessSrc);
            } else {
                getLog().warn("aksessHome " + aksessSrc.getAbsolutePath() + " does not exist!");
            }
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

    private File getSrcDir() {
        if(srcDir.exists()){
            return srcDir;
        } else if (stdM2SrcDir.exists()){
            return stdM2SrcDir;
        } else {
            throw new IllegalStateException("Neither src/webapp nor src/main/webapp exists!");
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
        starter.setOpenBrowser(true);
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

    /**
     * @return
     */
    private List<File> getDependencyFiles ()
    {
        List<File> dependencyFiles = new ArrayList<>();
        for (Artifact artifact : projectArtifacts) {
            // Include runtime and compile time libraries, and possibly test libs too
            if (artifact.getType().equals("war")) {
                continue;
            }

            if (Artifact.SCOPE_PROVIDED.equals(artifact.getScope()))
                continue; //never add dependencies of scope=provided to the webapp's classpath (see also <useProvidedScope> param)

            dependencyFiles.add(artifact.getFile());
            getLog().debug("Adding artifact " + artifact.getFile().getName() + " with scope " + artifact.getScope() + " for WEB-INF/lib ");
        }

        return dependencyFiles;
    }
}
