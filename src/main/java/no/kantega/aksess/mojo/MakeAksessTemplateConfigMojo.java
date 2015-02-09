package no.kantega.aksess.mojo;

import no.kantega.aksess.MakeAksessTemplateConfig;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Mojo that processes aksess-templateconfig.xml, generating a class representing it.
 */
@Mojo(name = "generateAksessTemplateConfig", defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.RUNTIME, requiresProject = true)
public class MakeAksessTemplateConfigMojo extends AbstractMojo {

    /**
     * Where is aksess-templateconfig.xml located. Default src/main/webapp/WEB-INF/aksess-templateconfig.xml
     */
    @Parameter(defaultValue = "src/main/webapp/WEB-INF/aksess-templateconfig.xml")
    private File aksessTemplateConfigXml;

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/aksess")
    private File destination;

    /**
     * Package name for the generated class
     */
    @Parameter(defaultValue = "${project.groupId}")
    private String projectPackage;

    /**
     * Where the generated class should be written
     */
    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    protected String classesDirectory;

    @Component
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            destination.mkdirs();
            project.addCompileSourceRoot(destination.getPath());
            MakeAksessTemplateConfig.createAksessTemplateConfigSources(aksessTemplateConfigXml, projectPackage, destination, project.getArtifacts());
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

    }
}
