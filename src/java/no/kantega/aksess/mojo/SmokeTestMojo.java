package no.kantega.aksess.mojo;

import no.kantega.aksess.JettyStarter;
import no.kantega.aksess.mojo.smoke.DriverConfig;
import no.kantega.aksess.mojo.smoke.ElementPage;
import no.kantega.aksess.mojo.smoke.Page;
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
import org.openqa.selenium.OutputType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


/**
 * @goal smoketest
 */
public class SmokeTestMojo extends AbstractMojo {

    /**
     * The name of the generated WAR.
     *
     * @parameter expression="${project.build.directory}/${project.build.finalName}.war"
     * @required
     */
    private File warFile;


    /**
     * @parameter expression="${project.build.directory}/kantega-dir"
     */
    private File kantegaDir;


    /**
     * @parameter expression="${project.build.directory}/aksessrun/smoke/"
     */
    private File smokeDir;


    /**
     * @parameter expression="/${project.artifactId}"
     */
    private String contextPath;

    /**
     * @parameter default-value="${basedir}/src/test/smoketest.xml"
     */
    private File smokeTestFile;

    /**
     * @parameter
     */
    private String fakeUsername = "admin";

    /**
     * @parameter
     */
    private String fakeUserDomain = "dbuser";


    public void execute() throws MojoExecutionException, MojoFailureException {


        JettyStarter starter = null;

        List<DriverConfig> drivers = new ArrayList<DriverConfig>();
        try {

            starter = new JettyStarter();
            starter.addContextParam("smokeTestEnabled", "true");
            starter.setSrcDir(warFile);
            starter.addContextParam("kantega.appDir", kantegaDir.getAbsolutePath());
            starter.addContextParam("fakeUsername", fakeUsername);
            starter.addContextParam("fakeUserDomain", fakeUserDomain);
            starter.setContextPath(contextPath);
            starter.setJoinServer(false);
            starter.start();

            try {
                drivers.add(new DriverConfig(new FirefoxDriver(), "firefox"));
            } catch (Exception e) {
                getLog().error("Failed adding FirefoxDriver: ", e);
            }
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                try {
                    drivers.add(new DriverConfig(new InternetExplorerDriver(), "ie"));
                } catch (Exception e) {
                    getLog().error("Failed adding InternetExplorerDriver: ", e);
                }
            }
            try {
                drivers.add(new DriverConfig(new ChromeDriver(), "chrome"));
            } catch (Exception e) {
                getLog().error("Failed adding ChromiumDriver: ", e);
            }


            smokeDir.mkdirs();

            final List<Page> pages = new ArrayList<Page>();
            if (smokeTestFile.exists()) {
                pages.addAll(getPages(smokeTestFile.toURL()));
            }
            final String root = "http://localhost:" + starter.getPort() + contextPath;
            pages.addAll(getPages(new URL(root + "/SmokeTestPages.action")));

            for (DriverConfig driver : drivers) {

                try {
                    File driverDir = new File(smokeDir, driver.getId());
                    driverDir.mkdirs();

                    for (Page page : pages) {
                        try {
                            final String pageUrl = root + page.getUrl();
                            getLog().info("GETing page " + pageUrl);
                            driver.getDriver().get(pageUrl);
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
            try {
                for (DriverConfig driver : drivers) {
                    if (driver != null) {
                        try {
                            driver.getDriver().close();
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


            dumpThreads("Jetty and server stopped");
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

    private List<Page> getPages(URL url) {
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

    private void diff(File oldFile, File newFile, File diffFile) {
        try {
            final BufferedImage oldImage = ImageIO.read(oldFile);
            final BufferedImage newImage = ImageIO.read(newFile);

            final BufferedImage diffImage = new BufferedImage(Math.max(oldImage.getWidth(), newImage.getWidth()),
                    Math.max(oldImage.getHeight(), newImage.getHeight()), BufferedImage.TYPE_INT_ARGB);

            for (int x = 0; x < oldImage.getWidth() && x < newImage.getWidth(); x++) {

                for (int y = 0; y < oldImage.getHeight() && y < newImage.getHeight(); y++) {

                    if (oldImage.getRGB(x, y) != newImage.getRGB(x, y)) {
                        diffImage.setRGB(x, y, 0xFF00FF00);
                    }
                }
            }
            ImageIO.write(diffImage, "png", diffFile);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    private void dumpThreads(String s) {
        System.out.println("-- " + s);
        final Thread[] threads = new Thread[Thread.activeCount()];
        final int i = Thread.enumerate(threads);
        for (Thread t : threads) {
            System.out.println("t: " + t + " " + t.isDaemon());
        }
    }

    public static void main(String[] args) throws MojoExecutionException, MojoFailureException {
        new SmokeTestMojo().execute();
    }

}
