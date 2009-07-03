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
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.versioning.VersionRange;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import no.kantega.aksess.JettyStarter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Map;

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


    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Running Jetty");

        List<Artifact> wars = new ArrayList<Artifact>();

        try {
            final Artifact aksessArifact = artifactFactory.createDependencyArtifact("org.kantega.openaksess", "openaksess-webapp", VersionRange.createFromVersion(aksessVersion), "war", null, "compile");
            resolver.resolve(aksessArifact, remoteRepositories, localRepository);
            wars.add(aksessArifact);
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

        JettyStarter starter = new JettyStarter();

        if(aksessHome != null) {
            File aksessSrc  = new File(aksessHome, "modules/webapp/src/webapp");
            if(aksessSrc.exists()) {
                starter.getAdditinalBases().add(aksessSrc.getAbsolutePath());
            }
        }

        for(Artifact artifact : wars) {
            File dir = new File(warsWorkDir, artifact.getFile().getName());
            if(!dir.exists() ||  (artifact.getFile().lastModified() > dir.lastModified())) {
                dir.mkdirs();
                dir.setLastModified(System.currentTimeMillis());
                unArchiver.setSourceFile(artifact.getFile());
                unArchiver.setDestDirectory(dir);
                final IncludeExcludeFileSelector selector = new IncludeExcludeFileSelector();
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
            starter.getAdditinalBases().add(dir.getAbsolutePath());
        }


        List<File> dependencyFiles = new ArrayList<File>();
        dependencyFiles.add(classesDirectory);
        for(Iterator i = project.getArtifacts().iterator(); i.hasNext(); ) {
            Artifact artifact = (Artifact) i.next();
            if (artifact.getType().equals("jar") && (!Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) && (!Artifact.SCOPE_TEST.equals( artifact.getScope())))  {
                dependencyFiles.add(artifact.getFile());
            }
        }

        starter.setContextPath(contextPath);
        starter.setSrcDir(srcDir);
        starter.setWebXml(mergedWebXml);
        starter.addContextParam("kantega.appDir", kantegaDir.getAbsolutePath());

        starter.setDependencyFiles(dependencyFiles);
        jettyWorkDir.mkdirs();
        starter.setWorkDir(jettyWorkDir);
        starter.setOpenBrowser(true);
        try {
            starter.start();
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
