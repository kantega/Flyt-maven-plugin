package no.kantega.aksess.mojo;

import no.kantega.aksess.mojo.smoke.DriverConfig;
import no.kantega.aksess.mojo.smoke.Page;
import no.kantega.aksess.mojo.smoke.SmokeTestBase;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.*;
import java.net.MalformedURLException;
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

    /**
     * @parameter default-value="${java.io.tmpdir}/chromedriver"
     */
    private File chromeFile;

    public void execute() throws MojoExecutionException, MojoFailureException {

        List<DriverConfig> drivers = new ArrayList<DriverConfig>();

        try {

            start(smokeWar);
            copyWar();

            final String resize = "window.resizeTo(1280, 1024);";

            configureIEDriver(drivers);
            configureChrome(drivers);
            configureFirefox(drivers);

            final List<Page> pages = getTestPagesFromOA();

            executeDriversOnPages(drivers, resize, pages);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            dumpThreads("Jetty and server stopped");
        }
    }

    private void executeDriversOnPages(List<DriverConfig> drivers, String resize, List<Page> pages) {
        for (DriverConfig driver : drivers) {
            try {
                File driverDir = createImageFolder(driver);

                for (Page page : pages) {
                    takeScreenshot(resize, driver, driverDir, page);
                }
                writeReport(pages, drivers, driver, new File(driverDir, "index.html"));
            } catch (Exception e) {
                getLog().info("Ignoring failed driver " + driver.getId(), e);
            }
        }
    }

    private File createImageFolder(DriverConfig driver) {
        smokeDir.mkdirs();
        File driverDir = new File(smokeDir, driver.getId());
        driverDir.mkdirs();
        return driverDir;
    }

    private void takeScreenshot(String resize, DriverConfig driver, File driverDir, Page page) throws InterruptedException, IOException {
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

    private List<Page> getTestPagesFromOA() throws MalformedURLException {
        final List<Page> pages = new ArrayList<Page>();
        if (smokeTestFile.exists()) {
            pages.addAll(getPages(smokeTestFile.toURL()));
        }
        pages.addAll(pages());
        return pages;
    }

    private void configureFirefox(List<DriverConfig> drivers) {
        try {
            DriverConfig firefox = new DriverConfig(new FirefoxDriver(), "firefox");
            drivers.add(firefox);
            addDriver(firefox);
        } catch (Exception e) {
            getLog().error("Failed adding FirefoxDriver: ", e);
        }
    }

    private void configureChrome(List<DriverConfig> drivers) {
        try {
            configureChromeDriver();
            DriverConfig chrome = new DriverConfig(new ChromeDriver(), "chrome");
            drivers.add(chrome);
            addDriver(chrome);
        } catch (Exception e) {
            getLog().error("Failed adding ChromiumDriver: ", e);
        }
    }

    private void configureIEDriver(List<DriverConfig> drivers) {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                DesiredCapabilities ieCapabilities = DesiredCapabilities.internetExplorer();
                ieCapabilities.setCapability(InternetExplorerDriver.INTRODUCE_FLAKINESS_BY_IGNORING_SECURITY_DOMAINS, true);
                DriverConfig ie = new DriverConfig(new InternetExplorerDriver(ieCapabilities), "ie");
                drivers.add(ie);
                addDriver(ie);
            } catch (Exception e) {
                getLog().error("Failed adding InternetExplorerDriver: ", e);
            }
        }
    }

    private void configureChromeDriver() {
        InputStream chromeDriverStream = null;
        if(System.getProperty("os.name").toLowerCase().contains("win")){
            chromeDriverStream = getClass().getResourceAsStream("/chromeDriver/chromedriverWindows.exe");
        }else if(System.getProperty("os.name").toLowerCase().contains("nux")){
            chromeDriverStream = getClass().getResourceAsStream("/chromeDriver/chromedriverLinux64");
        }else if(System.getProperty("os.name").toLowerCase().contains("mac")){
            chromeDriverStream = getClass().getResourceAsStream("/chromeDriver/chromedriverMac");
        }
        try{
            if(!chromeFile.exists()){
                chromeFile = new File(System.getProperty("java.io.tmpdir"), "chromedriver");
                IOUtils.copy(chromeDriverStream, new FileOutputStream(chromeFile));
            }
            System.setProperty("webdriver.chrome.driver", chromeFile.getAbsolutePath());
        }catch(FileNotFoundException e){
            e.printStackTrace();
        }catch(IOException e){
            e.printStackTrace();
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
