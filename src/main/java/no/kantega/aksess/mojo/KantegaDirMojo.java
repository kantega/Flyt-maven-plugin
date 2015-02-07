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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.*;

/**
 * Mojo for populating kantega-dir
 */
@Mojo(name = "kantegadir", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresProject = true)
public class KantegaDirMojo extends AbstractMojo {

    /**
     * Where should kantega-dir be created. Default ${project.build.directory}/kantega-dir
     */
    @Parameter(defaultValue = "${project.build.directory}/kantega-dir")
    private File kantegaDir;

    /**
     * location of install-dir, containing files that should be copied.
     */
    @Parameter(defaultValue = "${basedir}/src/install")
    private File installDir;


    /**
     * Location of log config file, default ${basedir}/src/conf/logback.xml
     */
    @Parameter(defaultValue = "${basedir}/src/conf/logback.xml")
    private File logConfigFile;

    /**
     * Location of aksess-webapp.conf, default ${basedir}/src/conf/aksess-webapp.conf
     */
    @Parameter(defaultValue = "${basedir}/src/conf/aksess-webapp.conf")
    private File webappConf;

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
            if(logConfDest.exists()) {
                getLog().info("Using existing log config: " + logConfDest.getAbsolutePath());
            } else {
                if(logConfigFile.exists()){
                    getLog().info("Using logback.xml from project");
                    try(InputStream is = new FileInputStream(logConfigFile);
                        OutputStream os = new FileOutputStream(logConfDest)){
                        IOUtils.copy(is, os);
                    }
                } else {
                    getLog().info("Using logback.xml from aksess plugin");
                    try(InputStream is = getClass().getResourceAsStream("/logback.xml");
                        OutputStream os = new FileOutputStream(logConfDest)){
                        IOUtils.copy(is, os);
                    }
                }
            }

            System.setProperty("kantega.appDir", kantegaDir.getAbsolutePath());
            System.setProperty("logback.configurationFile", logConfDest.getAbsolutePath());
            System.setProperty("development", String.valueOf(true));

        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }
}
