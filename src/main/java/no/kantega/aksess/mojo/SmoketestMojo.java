package no.kantega.aksess.mojo;

import no.kantega.aksess.JettyStarter;
import no.kantega.aksess.mojo.smoke.Page;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Mojo that starts a instance of the webapp and tests that all pages returned by
 * TestPages.action returns HTTP 200
 */
@Mojo(name = "smoketest", requiresDependencyResolution = ResolutionScope.RUNTIME, requiresDependencyCollection = ResolutionScope.RUNTIME)
public class SmoketestMojo extends AbstractMojo {

    /**
     * The name of the generated WAR.
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.war")
    private File warFile;

    @Parameter(defaultValue = "${project.build.directory}/aksessrun/unpackedwar/")
    private File unpackedWarDir;

    /**
     * Folder to set as java.io.tmpdir
     */
    @Parameter(defaultValue = "${project.build.Directory}/aksessrun/temp")
    private File tempDir;

    @Parameter(defaultValue = "${project.build.directory}/aksessrun/${project.build.finalName}.war")
    private File smokeWar;

    @Parameter(defaultValue = "${project.build.directory}/kantega-dir")
    private File kantegaDir;

    @Parameter(defaultValue = "/${project.artifactId}")
    private String contextPath;

    @Parameter(defaultValue = "admin")
    private String fakeUsername;

    @Parameter(defaultValue = "dbuser")
    private String fakeUserDomain;

    /**
     * How many pages should be tested per display template?
     */
    @Parameter(defaultValue = "10")
    private Integer numberPrTemplate;

    private JettyStarter starter;
    private String root;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            copyWar();
            start();
            doCheckPages();
        } catch (MojoExecutionException e){
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed starting application", e);
        } finally {
            try {
                starter.stop();
            } catch (Exception e) {
                throw new MojoExecutionException("Failed starting application", e);
            }
        }

    }

    public void start() throws Exception {
        System.setProperty("java.io.tmpdir", tempDir.getAbsolutePath());

        starter = new JettyStarter();
        starter.addContextParam("testPagesEnabled", "true");
        starter.setSrcDir(smokeWar);
        starter.setWorkDir(unpackedWarDir);
        starter.addContextParam("kantega.appDir", kantegaDir.getAbsolutePath());
        starter.addContextParam("fakeUsername", fakeUsername);
        starter.addContextParam("fakeUserDomain", fakeUserDomain);
        starter.setContextPath(contextPath);
        starter.setJoinServer(false);
        starter.setThrowUnavailableOnStartupException(true);
        starter.start();
        root = "http://localhost:" + starter.getPort() + contextPath;
    }


    private void doCheckPages() throws Exception {
        List<Page> failedPages = new LinkedList<>();
        CloseableHttpClient httpClient = HttpClients.createDefault();
        List<Page> pages = pages(httpClient);
        for (Page page : pages) {
            try(CloseableHttpResponse response = httpClient.execute(new HttpGet(root + page.url))){
                getLog().info(page + " status: " + response.getStatusLine().getStatusCode());
                if(response.getStatusLine().getStatusCode() >= 500){
                    failedPages.add(page);
                }
            }
        }
        if(!failedPages.isEmpty()){
            for (Page page : failedPages) {
                getLog().error(page + " failed");
            }
            throw new MojoExecutionException("Pages has failed");
        }
    }

    private List<Page> pages(CloseableHttpClient httpClient) throws MalformedURLException, MojoExecutionException {
        String testOptions = "excludeFilter=smoketest=false&numberPrTemplate=" + numberPrTemplate;
        return getPages(httpClient, new URL(root + "/TestPages.action?" + testOptions));
    }

    private List<Page> getPages(CloseableHttpClient httpClient, URL url) throws MojoExecutionException {
        try (CloseableHttpResponse response = httpClient.execute(new HttpGet(url.toURI()))){
            if(response.getStatusLine().getStatusCode() != 200){
                throw new MojoExecutionException("Could not get Test pages");
            }
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(response.getEntity().getContent());
            NodeList elems = doc.getElementsByTagName("page");
            if(elems.getLength() == 0){
                throw new MojoExecutionException("No pages to test");
            }
            List<Page> pages = new ArrayList<>(elems.getLength());

            for (int i = 0; i < elems.getLength(); i++) {
                pages.add(new Page(elems.item(i).getAttributes()));
            }
            return pages;
        } catch (IOException | ParserConfigurationException | SAXException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private void copyWar() throws IOException {
        if (!smokeWar.exists() || smokeWar.lastModified() < warFile.lastModified()) {
            FileUtils.copyFile(warFile, smokeWar);
        }
    }
}
