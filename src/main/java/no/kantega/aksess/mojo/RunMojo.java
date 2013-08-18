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
import no.kantega.aksess.MakeAksessTemplateConfig;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.Arrays.asList;

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
     * @parameter expression="${project.groupId}"
     * @readonly
     */
    private String projectPackage;

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
     * Exclude the files matching the given pattern from runtime classpath.
     * @parameter
     */
    private List<String> excludes;

    /**
     * @parameter expression="${port}" default-value="8080"
     */
    private int port;
    private JettyStarter starter;

    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Running Jetty");

        List<Artifact> wars = new ArrayList<>();

        final Artifact aksessWarArtifact;
        try {
            aksessWarArtifact = artifactFactory.createDependencyArtifact("org.kantega.openaksess", "openaksess-webapp", VersionRange.createFromVersion(aksessVersion), "war", null, "compile");
            resolver.resolve(aksessWarArtifact, remoteRepositories, localRepository);
            wars.add(aksessWarArtifact);
        } catch (ArtifactResolutionException | ArtifactNotFoundException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }


        for (Object o : project.getDependencyArtifacts()) {
            Artifact a = (Artifact) o;
            if (a.getType().equals("war")) {
                wars.add(a);
            }
        }

        starter = new JettyStarter();
        if(!aksessConfigFile.exists()) {
            throw new MojoExecutionException("aksessConfigFile does not exist: " + aksessConfigFile.getAbsolutePath());
        }
        starter.addContextParam("no.kantega.publishing.setup.SetupServlet.CONFIG_SOURCE", aksessConfigFile.getAbsolutePath());

        if(System.getProperty("os.name").toLowerCase().contains("win")) {
            starter.addContextParam("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "false");
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

        List<File> dependencyFiles = new ArrayList<>();
        dependencyFiles.add(classesDirectory);

        try {

            Set<String> dependencyIds = new HashSet<>();
            {
                final MavenProject aksessWarProject = mavenProjectBuilder.buildFromRepository(aksessWarArtifact, remoteRepositories, localRepository);


                Set<Artifact> artifacts = new HashSet<>();
                artifacts.addAll(aksessWarProject.createArtifacts(artifactFactory, null, null));


                final ArtifactResolutionResult result = resolver.resolveTransitively(artifacts,
                        aksessWarProject.getArtifact(),
                        aksessWarProject.getManagedVersionMap(),
                        localRepository,
                        remoteRepositories,
                        artifactMetadataSource, new ScopeArtifactFilter( Artifact.SCOPE_RUNTIME ));

                for (Object o : result.getArtifacts()) {
                    Artifact artifact = (Artifact) o;
                    if (isJar(artifact) && isNotScopeProvided(artifact) && isNotScopeTest(artifact)) {
                        dependencyFiles.add(artifact.getFile());
                        dependencyIds.add(artifact.getDependencyConflictId());
                    }
                }
            }
            {

                for (Object o : project.getArtifacts()) {
                    Artifact artifact = (Artifact) o;
                    if (isJar(artifact) && isNotScopeProvided(artifact) && isNotScopeTest(artifact)) {
                        if (!dependencyIds.contains(artifact.getDependencyConflictId())) {
                            dependencyFiles.add(artifact.getFile());
                        }

                    }
                }
            }

        } catch (ProjectBuildingException | InvalidDependencyVersionException | ArtifactResolutionException | ArtifactNotFoundException e) {
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

        configureStarter(starter);

        addRestartConsoleScanner();
        addAksessTemplateConfigChangeListener();

        try {
            starter.start();
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private boolean isNotScopeTest(Artifact artifact) {
        return (!Artifact.SCOPE_TEST.equals(artifact.getScope()));
    }

    private boolean isNotScopeProvided(Artifact artifact) {
        return (!Artifact.SCOPE_PROVIDED.equals(artifact.getScope()));
    }

    private boolean isJar(Artifact artifact) {
        return artifact.getType().equals("jar");
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

    private void addAksessTemplateConfigChangeListener() {
        if(coreModulePath != null && projectPackage != null){
            getLog().info("Watching aksess-templateconfig.xml");
            new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        Path templateConfigDir = Paths.get(srcDir.getAbsolutePath(), "/WEB-INF");
                        File generatedSourcesDir = new File(coreModulePath, "/target/generated-sources/aksess");
                        File targetClassFile = new File(coreModulePath, "target/classes/");


                        WatchService watcher = FileSystems.getDefault().newWatchService();
                        WatchKey watchKey = templateConfigDir.register(watcher, ENTRY_MODIFY);
                        loopOverWatchKeys(templateConfigDir, generatedSourcesDir, targetClassFile, watcher);
                    } catch (Exception e) {
                        getLog().error(e);
                    }
                }

                private void loopOverWatchKeys(Path templateConfigDir, File generatedSourcesDir, File targetClassFile, WatchService watcher) {
                    for (;;) {
                        try {
                            WatchKey key = watcher.take();
                            for (WatchEvent<?> event : key.pollEvents()) {
                                WatchEvent<Path> ev = (WatchEvent<Path>)event;
                                Path filename = ev.context();
                                handleModifiedFile(templateConfigDir, generatedSourcesDir, targetClassFile, filename);
                            }
                            boolean valid = key.reset();
                            if (!valid) {
                                break;
                            }
                        } catch (Exception e) {
                            getLog().error(e);
                        }
                    }
                }

                private void handleModifiedFile(Path templateConfigDir, File generatedSourcesDir, File targetClassFile, Path filename) throws MojoExecutionException {
                    if(filename.getFileName().toString().equals("aksess-templateconfig.xml")){
                        getLog().info("aksess-templateconfig.xml changed, recompiling to " + targetClassFile);

                        Path templateConfig = templateConfigDir.resolve(filename);
                        File aksessTemplateConfigSources = MakeAksessTemplateConfig.createAksessTemplateConfigSources(
                                templateConfig.toFile(), projectPackage, generatedSourcesDir);

                        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                        MyDiagnosticListener listener = new MyDiagnosticListener();
                        StandardJavaFileManager fileManager  = compiler.getStandardFileManager(listener, null, null);
                        Iterable<? extends JavaFileObject> fileObjects =  fileManager.getJavaFileObjects(
                                aksessTemplateConfigSources);

                        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, listener,
                                asList("-d", targetClassFile.getAbsolutePath()), null, fileObjects);

                        boolean success  = task.call();
                        if(!success){
                            getLog().error("Compilation failed!");
                        } else {
                            getLog().info("Compilation success!");
                        }
                    }
                }
            }).start();
        }
    }

    protected void configureStarter(JettyStarter starter) {
        starter.setOpenBrowser(true);
    }

    private File unpackArtifact(Artifact artifact) throws MojoExecutionException {
        File dir = new File(warsWorkDir, artifact.getFile().getName());
        if(!dir.exists() ||  (artifact.getFile().lastModified() > dir.lastModified())) {
            dir.mkdirs();
            dir.setLastModified(System.currentTimeMillis());
            unArchiver.setSourceFile(artifact.getFile());
            unArchiver.setDestDirectory(dir);
            final IncludeExcludeFileSelector selector = new IncludeExcludeFileSelector();
            if(("org.kantega.openaksess".equals(artifact.getGroupId()) && "openaksess-webapp".equals(artifact.getArtifactId())) || artifact.equals(project.getArtifact())) {
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
            List<String> excludes = selector.getExcludes() == null ? new ArrayList<String>() :  new ArrayList<>(asList(selector.getExcludes()));
            if(this.excludes != null) excludes.addAll(this.excludes);
            getLog().info("Excluding from unpacking: " + excludes);
            selector.setExcludes(excludes.toArray(new String[excludes.size()]));
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

    protected JettyStarter getJettyStarter() {
        return starter;
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

    private class MyDiagnosticListener implements DiagnosticListener<JavaFileObject> {
        public void report(Diagnostic diagnostic) {
            getLog().error("Code -> " +  diagnostic.getCode());
            getLog().error("Column Number -> " + diagnostic.getColumnNumber());
            getLog().error("End Position -> " + diagnostic.getEndPosition());
            getLog().error("Kind -> " + diagnostic.getKind());
            getLog().error("Line Number -> " + diagnostic.getLineNumber());
            getLog().error("Message -> "+ diagnostic.getMessage(Locale.ENGLISH));
            getLog().error("Position -> " + diagnostic.getPosition());
            getLog().error("Source -> " + diagnostic.getSource());
            getLog().error("Start Position -> " + diagnostic.getStartPosition());
        }
    }
}
