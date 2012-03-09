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
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.project.MavenProject;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FileUtils;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.components.io.fileselectors.FileInfo;

import java.io.*;
import java.util.Properties;

/**
 * @goal kantegadir
 * @phase process-resources
 * @requiresProject

 */
public class KantegaDirMojo extends AbstractMojo {

    /**
     * @parameter expression="${project.build.directory}/kantega-dir"
     */
    private File kantegaDir;

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

    /**
     * @parameter expression="${basedir}/src/install"
     */
    private File installDir;


    /**
     * @parameter expression="${basedir}/src/conf/log4j.xml"
     */
    private File log4j;

    /**
     * @parameter expression="${basedir}/src/conf/aksess-webapp.conf"
     */
    private File webappConf;


    /**
     * @parameter expression="${basedir}/src/templates"
     */
    private File templatesDirectory;

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;



    public void execute() throws MojoExecutionException, MojoFailureException {
        kantegaDir.mkdirs();
        getLog().info("Copy files from openaksess-install " + aksessVersion);


        try {
            // Extract Aksess's install jar
            final File confFile = new File(kantegaDir, "conf/aksess.conf");
            FileUtils.copyFile(webappConf, confFile);

            // Copy anything from src/install
            if(installDir.exists()) {
                FileUtils.copyDirectory(installDir, kantegaDir);
            }


            // Log4j
            if(log4j.exists()) {
                File log4jDest = new File(kantegaDir, "conf/log4j.xml");
                log4jDest.getParentFile().mkdirs();
                FileUtils.copyFile(log4j, log4jDest);
            }

            // We no longer accept templates in src/templates
            if(templatesDirectory.exists() ) {
                final FileFilter filter = new FileFilter() {
                    public boolean accept(File file) {
                        return file.isFile() && file.getName().endsWith(".xml");
                    }
                };
                if(templatesDirectory.listFiles(filter).length > 0)  {
                    throw new MojoFailureException("Content templates are no longer allowed in src/templates, please more them to WEB-INF/templates/content and then delete the src/templates directory");
                }
            }

        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }
}
