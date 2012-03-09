package no.kantega.aksess.mojo;

import no.kantega.aksess.JettyStarter;
import no.kantega.aksess.mojo.smoke.DriverConfig;
import no.kantega.aksess.mojo.smoke.ElementPage;
import no.kantega.aksess.mojo.smoke.Page;
import no.kantega.aksess.mojo.smoke.SmokeTestBase;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


/**
 * @goal smoketest
 */
public class SmokeTestMojo extends SmokeTestBase {

    /**
     * The name of the generated WAR.
     *
     * @parameter expression="${project.build.directory}/${project.build.finalName}.war"
     * @required
     */
    private File warFile;

    /**
     * @parameter expression="${project.build.directory}/aksessrun/${project.build.finalName}.war"
     */
    private File smokeWar;

    /**
     * @parameter default-value="${basedir}/src/test/smoketest.xml"
     */
    private File smokeTestFile;

    /**
     * @parameter expression="${project.build.directory}/aksessrun/smoke/"
     */
    private File smokeDir;

    public void execute() throws MojoExecutionException, MojoFailureException {

        List<DriverConfig> drivers = new ArrayList<DriverConfig>();

        try {
            start();
            copyWar();

            final String resize = "window.resizeTo(1280, 1024);";

            try {
                drivers.add(new DriverConfig(new FirefoxDriver(), "firefox"));
                addDriver(drivers.get(0));
            } catch (Exception e) {
                getLog().error("Failed adding FirefoxDriver: ", e);
            }
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                try {
                    drivers.add(new DriverConfig(new InternetExplorerDriver(), "ie"));
                    addDriver(drivers.get(1));
                } catch (Exception e) {
                    getLog().error("Failed adding InternetExplorerDriver: ", e);
                }
            }
            try {
                drivers.add(new DriverConfig(new ChromeDriver(), "chrome"));
                addDriver(drivers.get(2));
            } catch (Exception e) {
                getLog().error("Failed adding ChromiumDriver: ", e);
            }

            smokeDir.mkdirs();

            final List<Page> pages = new ArrayList<Page>();
            if (smokeTestFile.exists()) {
                pages.addAll(getPages(smokeTestFile.toURL()));
            }
            pages.addAll(pages());

            for (DriverConfig driver : drivers) {
                try {
                    File driverDir = new File(smokeDir, driver.getId());
                    driverDir.mkdirs();

                    for (Page page : pages) {
                        try {
                            final String pageUrl = getRoot() + page.getUrl();
                            getLog().info("GETing page in " + driver.getId() +": " + pageUrl);
                            driver.getDriver().get(pageUrl);
                            ((JavascriptExecutor)driver.getDriver()).executeScript(resize);
                            Thread.sleep(100);
                        } finally {
                            File f = driver.getScreenshotTaker().getScreenshotAs(OutputType.FILE);
                            final File imgFile = new File(driverDir, page.getId() + ".png");
                            FileUtils.copyFile(f, imgFile);
                            f.delete();
                        }
                    }
                    writeReport(pages, drivers, driver, new File(driverDir, "index.html"));
                } catch (Exception e) {
                    getLog().info("Ignoring failed driver " + driver.getId(), e);
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            dumpThreads("Jetty and server stopped");
        }
    }

    private void copyWar() throws IOException {
        if (!smokeWar.exists() || smokeWar.lastModified() < warFile.lastModified()) {
            FileUtils.copyFile(warFile, smokeWar);
        }
    }

    private void writeReport(List<Page> pages, List<DriverConfig> drivers, DriverConfig driver, File reportFile) {
        VelocityContext context = new VelocityContext();
        context.put("pages", pages);
        context.put("drivers", drivers);
        context.put("driver", driver);

        try {
            final Writer writer = new OutputStreamWriter(new FileOutputStream(reportFile), "utf-8");
            final Reader reader = new InputStreamReader(getClass().getResourceAsStream("report-template.vm"));
            Velocity.evaluate(context, writer, "smokereport", reader);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws MojoExecutionException, MojoFailureException {
        new SmokeTestMojo().execute();
    }

}
