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

import java.io.File;
import java.io.FileNotFoundException;

import no.kantega.aksess.MakeVersion;

import javax.xml.parsers.ParserConfigurationException;

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
     * @parameter expression="${basedir}/../.svn/entries"
     * @required
     * @readonly
     */
    private File entriesFile;


    /**
    * @parameter default-value="${project.build.outputDirectory}/aksess-webapp-version.properties"
     */
    private File versionFile;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            versionFile.getParentFile().mkdirs();
            MakeVersion.main(new String[] {entriesFile.getAbsolutePath(), versionFile.getAbsolutePath(), version});
        } catch (ParserConfigurationException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

    }


}
