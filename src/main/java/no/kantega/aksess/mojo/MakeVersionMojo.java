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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

/**
 * Mojo that creates «aksess-webapp-version.properties», containing version information about the building project.
 */
@Mojo(name = "makeversion", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresProject = true)
public class MakeVersionMojo extends AbstractMojo {

    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject mavenProject;

    @Parameter( defaultValue = "${session}", readonly = true )
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager pluginManager;

    /**
     * The version of the project
     */
    @Parameter(defaultValue = "${project.version}", readonly = true, required = true)
    private String version;

    /**
    * Where to put the file containing version information
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}/aksess-webapp-version.properties")
    private File versionFile;

    /**
     * Timestamp for the build. Current time is default.
     */
    @Parameter
    private String buildDate;

    public void execute() throws MojoExecutionException, MojoFailureException {
        executeBuildnumberMavenPlugin();
        if (buildDate == null) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            buildDate = format.format(new Date());
        }

        String revision = (String) mavenProject.getProperties().getOrDefault("buildNumber", "unknown");

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
            try (FileOutputStream fileOutputStream = new FileOutputStream(versionFile)){
                props.store(fileOutputStream, "iso-8859-1");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("IOException writing " + versionFile +" to disk");
        }
    }

    private void executeBuildnumberMavenPlugin() {
        try {
            executeMojo(
                    plugin(
                            groupId("org.codehaus.mojo"),
                            artifactId("buildnumber-maven-plugin"),
                            version("1.3")
                    ),
                    goal("create"),
                    configuration(
                            element(name("doCheck"), "false"),
                            element(name("doCheck"), "doUpdate")
                    ),
                    executionEnvironment(
                            mavenProject,
                            mavenSession,
                            pluginManager
                    )
            );
        } catch (MojoExecutionException e) {
            getLog().error("Error running buildnumber-maven-plugin", e);
        }
    }


}
