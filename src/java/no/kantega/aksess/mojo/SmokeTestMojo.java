package no.kantega.aksess.mojo;

import no.kantega.aksess.JettyStarter;
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
import org.jdom.input.SAXBuilder;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.firefox.FirefoxDriver;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


/**
 * @goal smoketest
 * @execute phase="install"
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
     * @parameter expression="${project.build.directory}/aksessrun/smoke/images"
     */
    private File imagesDir;


    /**
     * @parameter expression="/${project.artifactId}"
     */
    private String contextPath;

    public void execute() throws MojoExecutionException, MojoFailureException {


        JettyStarter starter = null;

        FirefoxDriver driver = null;

        try {

            starter = new JettyStarter();
            starter.addContextParam("smokeTestEnabled", "true");
            starter.setSrcDir(warFile);
            starter.addContextParam("kantega.appDir", kantegaDir.getAbsolutePath());
            starter.setContextPath(contextPath);
            starter.setJoinServer(false);
            starter.start();

            SAXBuilder builder = new SAXBuilder();
            final Document doc = builder.build(new URL("http://localhost:8080" + contextPath + "/SmokeTestPages.action"));
            driver = new FirefoxDriver();

            imagesDir.mkdirs();

            final List<Page> pages = getPages(doc);
            for (Page page : pages) {
                try {
                    driver.get("http://localhost:8080" + page.getUrl());
                } finally {
                    File f = driver.getScreenshotAs(OutputType.FILE);
                    final File imgFile = new File(imagesDir, page.getContentId() + ".png");
                    if (imgFile.exists()) {
                        diff(imgFile, f, new File(imagesDir, page.getContentId() + "-diff.png"));
                        FileUtils.copyFile(imgFile, new File(imagesDir, page.getContentId() + "-prev.png"));
                    }
                    FileUtils.copyFile(f, imgFile);
                    f.delete();
                }

            }

            writeReport(pages, new File(imagesDir, "index.html"));


        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            try {
                if (driver != null) {
                    driver.close();
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

    private void writeReport(List<Page> pages, File reportFile) {
        VelocityContext context = new VelocityContext();
        context.put("pages", pages);

        try {
            final Writer writer = new OutputStreamWriter(new FileOutputStream(reportFile));
            final Reader reader = new InputStreamReader(getClass().getResourceAsStream("report-template.vm"));
            Velocity.evaluate(context, writer, "smokereport", reader);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Page> getPages(Document doc) {
        List<Element> elems = doc.getRootElement().getChildren("page");

        List<Page> pages = new ArrayList<Page>();

        for (Element elem : elems) {
            pages.add(new ElementPage(elem));
        }

        return pages;
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
