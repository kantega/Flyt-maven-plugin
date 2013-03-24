package no.kantega.aksess.mojo.smoke;

import no.kantega.aksess.JettyStarter;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


public abstract class SmokeTestBase extends AbstractMojo {

    /**
     * The name of the generated WAR.
     *
     * @parameter expression="${project.build.directory}/${project.build.finalName}.war"
     * @required
     */
    private File warFile;

    /**
     * @parameter expression="${project.build.directory}/aksessrun/unpackedwar/"
     */
    private File unpackedWarDir;

    /**
     * @parameter expression="${project.build.directory}/aksessrun/${project.build.finalName}.war"
     */
    private File smokeWar;

    /**
     * @parameter expression="${project.build.directory}/kantega-dir"
     */
    private File kantegaDir;

    /**
     * @parameter expression="/${project.artifactId}"
     */
    private String contextPath;
    /**
     * @parameter
     */
    private String fakeUsername = "admin";

    /**
     * @parameter
     */
    private String fakeUserDomain = "dbuser";

    private String root;
    private JettyStarter starter;
    private List<DriverConfig> drivers = new ArrayList<DriverConfig>();

    public void addDriver(DriverConfig d) {
        drivers.add(d);
    }
    public String getRoot() {
        return root;
    }
    public void start() throws Exception {
        starter = new JettyStarter();
        starter.addContextParam("testPagesEnabled", "true");
        starter.setSrcDir(smokeWar);
        starter.setWorkDir(unpackedWarDir);
        starter.addContextParam("kantega.appDir", kantegaDir.getAbsolutePath());
        starter.addContextParam("fakeUsername", fakeUsername);
        starter.addContextParam("fakeUserDomain", fakeUserDomain);
        starter.setContextPath(contextPath);
        starter.setJoinServer(false);

        starter.start();
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            copyWar();
            start();


        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public List<Page> pages() throws MalformedURLException {
        final List<Page> pages = new ArrayList<Page>();
        root = "http://localhost:" + starter.getPort() + contextPath + "/";
        String testOptions = "excludeFilter=smoketest=false";
        pages.addAll(getPages(new URL(root + "TestPages.action?" + testOptions)));

        return pages;
    }

    public static List<Page> getPages(URL url) {
        try {
            SAXBuilder builder = new SAXBuilder();
            final Document doc = builder.build(url);

            List<Element> elems = doc.getRootElement().getChildren("page");
            List<Page> pages = new ArrayList<Page>();

            for (Element elem : elems) {
                pages.add(new ElementPage(elem));
            }
            return pages;
        } catch (JDOMException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void dumpThreads(String s) {
        try {
            for (DriverConfig driver : drivers) {
                if (driver != null) {
                    try {
                        driver.getDriver().close();
                        driver.getDriver().quit();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }

        } finally {
            if (starter != null) {
                try {
                    starter.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("-- " + s);
        final Thread[] threads = new Thread[Thread.activeCount()];
        final int i = Thread.enumerate(threads);
        for (Thread t : threads) {
            System.out.println("t: " + t + " " + t.isDaemon());
        }
    }

    private void copyWar() throws IOException {
        if (!smokeWar.exists() || smokeWar.lastModified() < warFile.lastModified()) {
            FileUtils.copyFile(warFile, smokeWar);
        }
    }
}
