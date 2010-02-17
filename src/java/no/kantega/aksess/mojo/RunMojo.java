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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.versioning.VersionRange;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.util.Scanner;
import no.kantega.aksess.JettyStarter;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @goal run
 * @execute phase="test"
 * @requiresDependencyResolution runtime
 *
 */
public class RunMojo extends AbstractMojo {

    /**
     * @parameter expression="${basedir}/src/webapp"
     */
    private File srcDir;

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
     * @parameter default-value="${project.build.Directory}/aksessrun/jettywork"
     */
    private File jettyWorkDir;

    /**
     * @parameter default-value="${project.build.Directory}/aksessrun/wars"
     */
    private File warsWorkDir;

    /**
     * @parameter default-value="${basedir}/src/conf/aksess-webapp.conf"
     */
    private File aksessConfigFile;

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;


    /** @component */
    private org.apache.maven.artifact.factory.ArtifactFactory artifactFactory;

    /** @component */
    private org.apache.maven.artifact.resolver.ArtifactResolver resolver;

    /**@parameter expression="${localRepository}" */
    private org.apache.maven.artifact.repository.ArtifactRepository localRepository;

    /** @parameter expression="${project.remoteArtifactRepositories}" */
    private java.util.List remoteRepositories;

    /** @component */
    private ArtifactMetadataSource artifactMetadataSource;


    /**
     * The zip unarchiver.
     *
     * @component role="org.codehaus.plexus.archiver.UnArchiver" roleHint="zip"
     */
    private UnArchiver unArchiver;

    /**
     * @parameter
     * @required
     */
    private String aksessVersion;

    /**
     * @parameter expression="${project.build.directory}/web.xml""
     */
    private File mergedWebXml;

    /**
     * @parameter
     */
    private List<Overlay> overlays;

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
     * @parameter expression="${port}" default-value="8080"
     */
    private int port;


    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Running Jetty");

        List<Artifact> wars = new ArrayList<Artifact>();

        final Artifact aksessWarArtifact;
        try {
            aksessWarArtifact = artifactFactory.createDependencyArtifact("org.kantega.openaksess", "openaksess-webapp", VersionRange.createFromVersion(aksessVersion), "war", null, "compile");
            resolver.resolve(aksessWarArtifact, remoteRepositories, localRepository);
            wars.add(aksessWarArtifact);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }


        for(Iterator i = project.getDependencyArtifacts().iterator(); i.hasNext(); ) {
            Artifact a = (Artifact) i.next();
            if(a.getType().equals("war")) {
                wars.add(a);
            }
        }

        final JettyStarter starter = new JettyStarter();
        if(!aksessConfigFile.exists()) {
            throw new MojoExecutionException("aksessConfigFile does not exist: " + aksessConfigFile.getAbsolutePath());
        }
        starter.addContextParam("no.kantega.publishing.setup.SetupServlet.CONFIG_SOURCE", aksessConfigFile.getAbsolutePath());

        if(System.getProperty("os.name").toLowerCase().contains("win")) {
            starter.addContextParam(DefaultServlet.class.getName() + ".useFileMappedBuffer", "false");
        }

        if(aksessHome != null) {
            File aksessSrc  = new File(aksessHome, "modules/webapp/src/webapp");
            if(aksessSrc.exists()) {
                starter.getAdditionalBases().add(aksessSrc.getAbsolutePath());
            }
        }




        for(Artifact artifact : wars) {
            File dir = unpackArtifact(artifact);
            starter.getAdditionalBases().add(dir.getAbsolutePath());
        }



        List<File> dependencyFiles = new ArrayList<File>();
        dependencyFiles.add(classesDirectory);


        try {

            Set<String> dependencyIds = new HashSet<String>();
            {
                final MavenProject aksessWarProject = mavenProjectBuilder.buildFromRepository(aksessWarArtifact, remoteRepositories, localRepository);


                Set<Artifact> artifacts = new HashSet<Artifact>();
                artifacts.addAll(aksessWarProject.createArtifacts(artifactFactory, null, null));


                final ArtifactResolutionResult result = resolver.resolveTransitively(artifacts,
                        aksessWarProject.getArtifact(),
                        aksessWarProject.getManagedVersionMap(),
                        localRepository,
                        remoteRepositories,
                        artifactMetadataSource, new ScopeArtifactFilter( Artifact.SCOPE_RUNTIME ));

                for(Iterator i = result.getArtifacts().iterator(); i.hasNext(); ) {
                    Artifact artifact = (Artifact) i.next();
                    if (artifact.getType().equals("jar") && (!Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) && (!Artifact.SCOPE_TEST.equals( artifact.getScope())))  {
                        dependencyFiles.add(artifact.getFile());
                        dependencyIds.add(artifact.getDependencyConflictId());
                    }
                }
            }
            {

                for(Iterator i = project.getArtifacts().iterator(); i.hasNext(); ) {
                    Artifact artifact = (Artifact) i.next();
                    if (artifact.getType().equals("jar") && (!Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) && (!Artifact.SCOPE_TEST.equals( artifact.getScope())))  {
                        if(!dependencyIds.contains(artifact.getDependencyConflictId())) {
                            dependencyFiles.add(artifact.getFile());
                        }

                    }
                }
            }

        } catch (ProjectBuildingException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (InvalidDependencyVersionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        starter.setPort(port);
        starter.setContextPath(contextPath);
        starter.setSrcDir(srcDir);
        starter.setWebXml(mergedWebXml);
        starter.addContextParam("kantega.appDir", kantegaDir.getAbsolutePath());

        starter.setDependencyFiles(dependencyFiles);
        jettyWorkDir.mkdirs();
        starter.setWorkDir(jettyWorkDir);
        starter.setOpenBrowser(true);

        Scanner scanner = new Scanner();
        scanner.setReportExistingFilesOnStartup(false);
        scanner.setScanDirs(dependencyFiles);
        scanner.addListener(new Scanner.BulkListener() {
            public void filesChanged(List filenames) throws Exception {
                getLog().info("Restarting webapp because the following dependencies have changed: " + filenames);
                starter.restart();
            }
        });

        scanner.setScanInterval(5);
        scanner.start();

        {
            Scanner warScanner = new Scanner();
            warScanner.setReportExistingFilesOnStartup(false);
            warScanner.setScanDirs(Collections.singletonList(aksessWarArtifact.getFile()));
            warScanner.addListener(new Scanner.BulkListener() {
                public void filesChanged(List filenames) throws Exception {
                    getLog().info("Unpacking changed OpenAksess web artifact: " + filenames);
                    for(Iterator i = project.getDependencyArtifacts().iterator(); i.hasNext(); ) {
                        Artifact a = (Artifact) i.next();
                        if(a.getType().equals("war")) {
                            waitForUnpack(a);
                            unpackArtifact(a);
                        }
                    }
                    waitForUnpack(aksessWarArtifact);
                    unpackArtifact(aksessWarArtifact);
                    starter.restart();
                }
            });

            warScanner.setScanInterval(5);
            warScanner.start();
        }


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

        try {
            starter.start();
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }


    }

    private void waitForUnpack(Artifact a) {
        long last = a.getFile().lastModified();
        for(int i = 0; i < 10; i++) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if(last == a.getFile().lastModified()) {
                break;
            } else {
                last = a.getFile().lastModified();
            }
        }
    }

    private File unpackArtifact(Artifact artifact) throws MojoExecutionException {
        File dir = new File(warsWorkDir, artifact.getFile().getName());
        if(!dir.exists() ||  (artifact.getFile().lastModified() > dir.lastModified())) {
            dir.mkdirs();
            dir.setLastModified(System.currentTimeMillis());
            unArchiver.setSourceFile(artifact.getFile());
            unArchiver.setDestDirectory(dir);
            final IncludeExcludeFileSelector selector = new IncludeExcludeFileSelector();
            if(("org.kantega.openaksess".equals(artifact.getGroupId()) && "openaksess-webapp".equals(artifact.getArtifactId())) || artifact == project.getArtifact()) {
                selector.setExcludes(new String[] {"WEB-INF/lib/**"});
            }
            if(overlays != null) {
                for(Overlay overlay : overlays) {
                    if(artifact.getGroupId().equals(overlay.getGroupId()) && artifact.getArtifactId().equals(overlay.getArtifactId())) {
                        if(overlay.getIncludes() != null) {
                            selector.setIncludes(overlay.getIncludes().toArray(new String[overlay.getIncludes().size()]));
                        }
                        break;
                    }
                }
            }

            unArchiver.setFileSelectors(new FileSelector[] {selector});

            try {
                getLog().info("Unpacking " + artifact.getFile().getName() + " into " + dir.getAbsolutePath());
                unArchiver.extract();
            } catch (ArchiverException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
        return dir;
    }

    abstract class ConsoleScanner extends Thread {

        @Override
        public void run() {
            while(true) {
                try {
                    checkInput();
                    Thread.sleep(100);
                } catch (IOException e) {
                    getLog().error(e);
                } catch (InterruptedException e) {
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
        protected abstract void restart();
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
}
