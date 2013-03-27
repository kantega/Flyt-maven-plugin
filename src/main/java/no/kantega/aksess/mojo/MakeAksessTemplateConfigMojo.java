package no.kantega.aksess.mojo;

import no.kantega.aksess.MakeAksessTemplateConfig;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * @phase generate-sources
 * @goal generateAksessTemplateConfig
 * @requiresProject
 */
public class MakeAksessTemplateConfigMojo extends AbstractMojo {

    /**
     * @parameter
     * @required
     */
    private File aksessTemplateConfigXml;

    /**
     * @parameter default-value="${project.build.directory}/generated-sources/aksess
     * @required
     */
    private File destination;

    /**
     * @parameter expression="${project.groupId}"
     * @required
     */
    private String projectPackage;

    /**
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     */
    protected String classesDirectory;

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @read-only
     * @required
     */
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            destination.mkdirs();
            project.addCompileSourceRoot(destination.getPath());
            MakeAksessTemplateConfig.createAksessTemplateConfigSources(aksessTemplateConfigXml, projectPackage, destination);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

    }
}
