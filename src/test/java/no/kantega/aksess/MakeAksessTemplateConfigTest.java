package no.kantega.aksess;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.junit.Assert.assertTrue;

public class MakeAksessTemplateConfigTest {

    @Test
    public void shouldGenerateAksessTemplateConfig() throws IOException, MojoExecutionException {
        File templateConfig = new File(getClass().getResource("/webinf/aksess-templateconfig.xml").getFile());


        File destination = Files.createTempDirectory("AksessTemplateConfigJava").toFile();
        File aksessTemplateConfigSources = MakeAksessTemplateConfig.createAksessTemplateConfigSources(templateConfig, "no.kantega.aksess", destination);

        String content = FileUtils.readFileToString(aksessTemplateConfigSources);
        FileUtils.deleteDirectory(destination);
        assertTrue("Java file was empty", isNotBlank(content));
        assertTrue("Did not contain correct identifierName for Folkebiblioteket - forside", content.contains("class Folkebiblioteket_forside"));
    }
}
