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

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * @goal makeversion
 * @phase process-resources
 * @requiresProject
 */
public class MakeVersionMojo extends AbstractMojo {

    
    /**
     * The version of the project
     * @parameter expression="${project.version}"
     * @required
     * @readonly
     *
     */
    private String version;

    /**
    * @parameter default-value="${project.build.outputDirectory}/aksess-webapp-version.properties"
     */
    private File versionFile;

    /**
     *
     * @parameter default-value="${openaksess.webapp.revision}"
     */
    private String revision;

    /**
     * @parameter
     */
    private String buildDate;

    public void execute() throws MojoExecutionException, MojoFailureException {

        if (StringUtils.isEmpty(revision)) {
            getLog().warn("Revision not set, use <revision> in config or -Dopenaksess.webapp.revision");
            revision = "unknown";
        }

        if (buildDate == null) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            buildDate = format.format(new Date());
        }


        Properties props = new Properties();
        props.setProperty("revision", revision);
        props.setProperty("date", buildDate);
        props.setProperty("version", version);

        try {
            File dir = versionFile.getParentFile();
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    throw new MojoExecutionException("Failed to create directory " + versionFile.getParentFile());
                }
            }
            props.store(new FileOutputStream(versionFile), "iso-8859-1");
        } catch (IOException e) {
            throw new MojoExecutionException("IOException writing " + versionFile +" to disk");
        }
    }
}
