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

import no.kantega.aksess.MergeWebXml;
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
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.util.DefaultArchivedFileSet;
import org.codehaus.plexus.components.io.fileselectors.FileInfo;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.util.FileUtils;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @goal package
 * @phase package
 * @requiresDependencyResolution runtime
 */
public class PackageMojo extends AbstractMojo {

    /**
     * The directory for the generated WAR.
     *
     * @parameter expression="${project.build.directory}"
     * @required
     */
    private String outputDirectory;

    /**
     * The name of the generated WAR.
     *
     * @parameter expression="${project.build.directory}/${project.build.finalName}.war"
     * @required
     */
    private File warFile;

    /**
     * The name of the modified WAR.
     *
     * @parameter expression="${project.build.directory}/${project.build.finalName}.aksess.war"
     * @required
     */
    private File destFile;


    /**
     * The War archiver.
     *
     * @component role="org.codehaus.plexus.archiver.Archiver" roleHint="jar"
     */
    private JarArchiver jarArchiver;

    /**
     * @component
     */
    private MavenProjectHelper projectHelper;


    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The directory containing generated classes.
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     * @readonly
     */
    private File classesDirectory;
    

/**
     * The directory where the webapp is built.
     *
     * @parameter expression="${project.build.directory}/${project.build.finalName}"
     * @required
     */
    private File webappDirectory;

    /**
     * Single directory for extra files to include in the WAR.
     *
     * @parameter expression="${basedir}/src/main/webapp"
     * @required
     */
    private File warSourceDirectory;

    /**
     * Directory to unpack dependent WARs into if needed
     *
     * @parameter expression="${project.build.directory}/war/work"
     * @required
     */
    private File workDirectory;


    /**
     * @parameter expression="${project.build.directory}/web.xml""
     */
    private File mergedWebXml;

    /**
     * @parameter expression="${basedir}/src/webapp/WEB-INF/web.xml";
     */
    private File projectWebXml;


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
     * @parameter
     * @required
     */
    private String aksessVersion;
    private static final String WEB_XML = "WEB-INF/web.xml";

    /** @component */
    private MavenProjectBuilder mavenProjectBuilder;


    public void execute() throws MojoExecutionException, MojoFailureException {

        getLog().info("Make new war from " + warFile);

        File work = new File(workDirectory, "aksess-war");
        work.mkdirs();




        try {
            final Artifact aksessWarArtifact = artifactFactory.createDependencyArtifact("org.kantega.openaksess", "openaksess-webapp", VersionRange.createFromVersion(aksessVersion), "war", null, "compile");
            resolver.resolve(aksessWarArtifact, remoteRepositories, localRepository);

            final File aksessFile = aksessWarArtifact.getFile();

            mergeWebXml(aksessFile);

            jarArchiver.addFile(mergedWebXml, WEB_XML);


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
                            jarArchiver.addFile(artifact.getFile(), "WEB-INF/lib/" + artifact.getFile().getName());
                            dependencyIds.add(artifact.getDependencyConflictId());
                        }
                    }

                }
                {

                    for(Iterator i = project.getArtifacts().iterator(); i.hasNext(); ) {
                        Artifact artifact = (Artifact) i.next();
                        if (artifact.getType().equals("jar") && (!Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) && (!Artifact.SCOPE_TEST.equals( artifact.getScope())))  {
                            if(!dependencyIds.contains(artifact.getDependencyConflictId())) {
                                jarArchiver.addFile(artifact.getFile(), "WEB-INF/lib/" + artifact.getFile().getName());
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



            final Set<String> paths = new HashSet<String>();

            final FileSelector filter = new FileSelector() {

                public boolean isSelected(FileInfo fileInfo) throws IOException {
                    if (paths.contains(fileInfo.getName())) {
                        return false;
                    } else {
                        paths.add(fileInfo.getName());
                        return true;
                    }
                }
            };

            jarArchiver.setDestFile(destFile);

            {
                final DefaultArchivedFileSet aksessFileset = new DefaultArchivedFileSet();
                aksessFileset.setArchive(aksessWarArtifact.getFile());
                aksessFileset.setExcludes(new String[] {WEB_XML, "WEB-INF/lib/*"});
                aksessFileset.setFileSelectors(new FileSelector[] {filter});

                jarArchiver.addArchivedFileSet(aksessFileset);
            }
            {
                final DefaultArchivedFileSet warFileSet = new DefaultArchivedFileSet();
                warFileSet.setArchive(warFile);
                warFileSet.setExcludes(new String[] {WEB_XML, "WEB-INF/lib/*"});
                warFileSet.setFileSelectors(new FileSelector[] {filter});

                jarArchiver.addArchivedFileSet(warFileSet);
            }


            jarArchiver.createArchive();

            FileUtils.copyFile(destFile, warFile);
            destFile.delete();
            

        } catch (ArchiverException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (TransformerException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (ParserConfigurationException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (SAXException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

    }

    private void mergeWebXml(File aksessFile) throws TransformerException, IOException, ParserConfigurationException, SAXException {
        if(!mergedWebXml.exists() ||
                aksessFile.lastModified() > mergedWebXml.lastModified() ||
                projectWebXml.lastModified() > mergedWebXml.lastModified()) {
            MergeWebXml.main(new String[] {
                    "jar:file:" + aksessFile.getAbsolutePath() +"!/WEB-INF/web.xml",
                    mergedWebXml.getAbsolutePath(),
                    projectWebXml.getAbsolutePath()

            });
        }
    }
}
