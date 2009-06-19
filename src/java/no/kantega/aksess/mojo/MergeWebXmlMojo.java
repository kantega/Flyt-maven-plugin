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
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import no.kantega.aksess.MergeWebXml;

import javax.xml.transform.TransformerException;
import javax.xml.parsers.ParserConfigurationException;

/**
 * @goal mergewebxml
 * @phase process-resources
 */
public class MergeWebXmlMojo extends AbstractMojo {

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

    public void execute() throws MojoExecutionException, MojoFailureException {

        getLog().info("Merging web.xml from Aksess with the project's web.xml");
        try {
            final Artifact aksessArifact = artifactFactory.createDependencyArtifact("org.kantega.openaksess", "openaksess-webapp", VersionRange.createFromVersion(aksessVersion), "war", null, "compile");
            resolver.resolve(aksessArifact, remoteRepositories, localRepository);

            final File aksessFile = aksessArifact.getFile();

            if(!mergedWebXml.exists() ||
                    aksessFile.lastModified() > mergedWebXml.lastModified() ||
                    projectWebXml.lastModified() > mergedWebXml.lastModified()) {
                MergeWebXml.main(new String[] {
                        "jar:file:" + aksessFile.getAbsolutePath() +"!/WEB-INF/web.xml",
                        mergedWebXml.getAbsolutePath(),
                        projectWebXml.getAbsolutePath()

                });
            }

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
}