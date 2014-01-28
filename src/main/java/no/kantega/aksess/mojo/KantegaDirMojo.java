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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import java.io.*;

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

    /** @parameter expression="${project.remoteArtifactRepositories}" */
    private java.util.List remoteRepositories;

    /** @component */
    private ArtifactMetadataSource artifactMetadataSource;

    /**
     * @parameter expression="${basedir}/src/install"
     */
    private File installDir;


    /**
     * @parameter expression="${basedir}/src/conf/logback.xml"
     */
    private File logConfigFile;

    /**
     * @parameter expression="${basedir}/src/conf/aksess-webapp.conf"
     */
    private File webappConf;

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

        try {
            // Extract Aksess's install jar
            final File confFile = new File(kantegaDir, "conf/aksess.conf");
            FileUtils.copyFile(webappConf, confFile);

            // Copy anything from src/install
            if(installDir.exists()) {
                FileUtils.copyDirectory(installDir, kantegaDir);
            }


            File logConfDest = new File(kantegaDir, "conf/logback.xml");
            logConfDest.getParentFile().mkdirs();
            if(!logConfigFile.exists()) {
                getLog().info("Using logback.xml from aksess plugin");
                try(InputStream is = getClass().getResourceAsStream("/logback.xml");
                    OutputStream os = new FileOutputStream(logConfDest)){
                    IOUtils.copy(is, os);
                }
            }

            System.setProperty("kantega.dir", kantegaDir.getAbsolutePath());
            System.setProperty("logback.configurationFile", logConfDest.getAbsolutePath());

        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }
}
