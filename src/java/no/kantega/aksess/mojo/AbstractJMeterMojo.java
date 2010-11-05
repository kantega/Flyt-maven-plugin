package no.kantega.aksess.mojo;

import no.kantega.aksess.JettyStarter;
import no.kantega.aksess.mojo.smoke.Page;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.Expand;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Text;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public abstract class AbstractJMeterMojo extends RunMojo {

    /**
     * @parameter default-value="${settings.localRepository}/apache-jmeter/"
     */
    private File jmeterDirectory;

    /**
     * @parameter default-value="${project.build.Directory}/aksessrun/jmeter"
     */
    private File jmeterTestDirectory;

    /**
     * @parameter default-value="2.4"
     */
    private String jmeterVersion;


    @Override
    protected void configureStarter(JettyStarter starter) {
        starter.setJoinServer(false);
        starter.setOpenBrowser(false);
        starter.addContextParam("smokeTestEnabled", "true");
    }

    public void execute() throws MojoExecutionException, MojoFailureException {

        super.execute();

        File jmeterDir = new File(this.jmeterDirectory, "jakarta-jmeter-" +jmeterVersion);

        getLog().info("JMeter dir is: " + jmeterDir);

        if(!jmeterDir.exists()) {
            getLog().info("Resolving Apache JMeter version " + jmeterVersion);


            final Artifact artifact = artifactFactory.createArtifact("org.apache.jmeter", "jakarta-jmeter", "2.4", "runtime", "zip");

            try {
                resolver.resolve(artifact, remoteRepositories, localRepository);
            } catch (ArtifactResolutionException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } catch (ArtifactNotFoundException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

            Expand expand = new Expand();
            expand.setDest(jmeterDir.getParentFile());
            expand.setSrc(artifact.getFile());
            expand.setOverwrite(true);

            getLog().info("Unpacking Apache JMeter to " + jmeterDir.getParentFile());
            try {
                expand.execute();
            } catch (Exception e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

        }


        final File jmeterTestFile = new File(jmeterTestDirectory, "test.jmx");
        jmeterTestFile.getParentFile().mkdirs();

        try {
            SAXBuilder builder = new SAXBuilder();
            final Document doc = builder.build(getClass().getClassLoader().getResourceAsStream("jmeter.jmx"));

            final Element def = (Element) XPath.selectNodes(doc, "//ConfigTestElement[@guiclass='HttpDefaultsGui']").get(0);

            Element port = (Element) XPath.selectNodes(def, "stringProp[@name='HTTPSampler.port']").get(0);

            port.setContent(new Text(Integer.toString(getJettyStarter().getPort())));


            final List<Page> pages = new ArrayList<Page>();
            final String root = "http://localhost:" + getJettyStarter().getPort() + getJettyStarter().getContextPath();
            pages.addAll(SmokeTestMojo.getPages(new URL(root + "/SmokeTestPages.action")));


            WebDriver driver = new HtmlUnitDriver();

            for(Page page : pages) {
                getLog().info("Pre-heating page " + page.getUrl());
                driver.get(root + page.getUrl());
            }
            Element frontPage= (Element) XPath.selectNodes(doc, "//HTTPSampler[@testname='Frontpage']").get(0);

            final Element parent = frontPage.getParentElement();
            parent.removeContent();

            for(Page page : pages) {

                Element sampler = (Element) frontPage.clone();
                sampler.setAttribute("testname", page.getCategory() +" (" +page.getTitle() +")");
                Element path = (Element) XPath.selectNodes(sampler, "stringProp[@name='HTTPSampler.path']").get(0);
                path.setContent(new Text(getJettyStarter().getContextPath() + page.getUrl()));
                Element hash = new Element("hashTree");
                parent.addContent(sampler);
                parent.addContent(hash);
            }


            XMLOutputter outputter = new XMLOutputter();

            final FileOutputStream fileOutputStream = new FileOutputStream(jmeterTestFile);
            outputter.output(doc, fileOutputStream);
            IOUtils.closeQuietly(fileOutputStream);
            
            
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (JDOMException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        try {

            List<URL> classPath = new ArrayList<URL>();

            List<File> classFiles = new ArrayList<File>();

            final File apacheJMeterJar = new File(jmeterDir, "bin/ApacheJMeter.jar");
            classFiles.add(apacheJMeterJar);

            for(File file : classFiles) {
                classPath.add(file.toURI().toURL());
            }

            StringBuilder builder = new StringBuilder();
            for(File file : classFiles) {
                builder.append(System.getProperty("path.separator")).append(file.getAbsolutePath());
            }
            System.setProperty("java.class.path", System.getProperty("java.class.path") + builder.toString() +System.getProperty("path.separator") +"fake");
            System.setProperty("jmeter.home", jmeterDir.getAbsolutePath());

            getLog().info("Starting JMeter with classpath " + classPath);

            URLClassLoader classLoader = new URLClassLoader(classPath.toArray(new URL[classPath.size()]));

            Class jmeterClass = classLoader.loadClass("org.apache.jmeter.NewDriver");

            jmeterClass.getMethod("addURL", URL.class).invoke(null, apacheJMeterJar.toURI().toURL());

            String[] args = getJMeterCommandLine(jmeterTestFile);
            final Method method = jmeterClass.getMethod("main", new Class[] {args.getClass()});

            method.invoke(null, new Object[] {args});


            afterJMeterStarted();

            getJettyStarter().stop();
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }


    }

    protected abstract void afterJMeterStarted() ;

    protected abstract String[] getJMeterCommandLine(File jmeterTestFile);
}
