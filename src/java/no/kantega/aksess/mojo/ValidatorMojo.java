package no.kantega.aksess.mojo;

import no.kantega.aksess.mojo.smoke.DriverConfig;
import no.kantega.aksess.mojo.smoke.Page;
import no.kantega.aksess.mojo.smoke.SmokeTestBase;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.codehaus.jackson.map.ObjectMapper;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;

import java.io.*;
import java.net.URL;
import java.util.*;

/**
 * @goal validator
 * @phase validate
*/
public class ValidatorMojo extends SmokeTestBase {

    /**
     * @parameter
     */
    private boolean validate;

    /**
     * @parameter
     */
    private URL validatorURL;

    /**
     * @parameter expression="${project.build.directory}/aksessrun/${project.build.finalName}.war"
     */
    private File validatorWar;

    /**
     * @parameter expression="${project.build.directory}/aksessrun/validate/"
     */
    private File validateDir;

    public void execute() throws MojoExecutionException, MojoFailureException {

        WebDriver driver = null;

        try {

            start(validatorWar);

            driver = new HtmlUnitDriver();
            DriverConfig config = new DriverConfig(driver, "ValidatorMojo");
            addDriver(config);

            HttpClient httpclient = new DefaultHttpClient();
            ObjectMapper mapper = new ObjectMapper();

            List<String> allowedErrorMessages = new LinkedList<String>();
            allowedErrorMessages.add("Attribute property not allowed on element meta at this point.");
            allowedErrorMessages.add("Element meta is missing one or more of the following attributes: http-equiv, itemprop, name.");
            allowedErrorMessages.add("Element fb:like not allowed as child of element div in this context. (Suppressing further errors from this subtree.)");

            final List<Page> pages = pages();

            Map<Page, String> errors = new HashMap<Page, String>();

            for(int i = 0; i < 10; i++) {
                Page page = pages.get(i);
                try {
                    final String pageUrl = getRoot() + page.getUrl();
                    getLog().info("GETing page in: " + pageUrl);
                    driver.get(pageUrl);
                } finally {
                    validity(driver, httpclient, mapper, allowedErrorMessages, page, errors);
                }
            }

            validateDir.mkdirs();
            File testValidate = new File(validateDir, "validate.html");
            writeReport(errors, testValidate);

        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            dumpThreads("Jetty and server stopped");
        }
    }

    private void writeReport(Map<Page, String> pages, File reportFile) {
        VelocityContext context = new VelocityContext();
        context.put("pages", pages);
        try {
            final Writer writer = new OutputStreamWriter(new FileOutputStream(reportFile), "utf-8");
            final Reader reader = new InputStreamReader(getClass().getResourceAsStream("validate-report-template.vm"));
            Velocity.evaluate(context, writer, "Validatereport", reader);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void validity(WebDriver driver, HttpClient httpclient, ObjectMapper mapper, 
                             List<String> allowedErrorMessages, Page page, Map<Page, String> errors) {
        try {
            HttpPost post = new HttpPost("http://validator.w3.org/check");
            List<NameValuePair> formparams = new ArrayList<NameValuePair>();
            String pageSource = driver.getPageSource();
            formparams.add(new BasicNameValuePair("fragment", pageSource));
            formparams.add(new BasicNameValuePair("output", "json"));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, "UTF-8");

            post.setEntity(entity);
            HttpResponse httpResponse = httpclient.execute(post);
            HttpEntity responseEntity = httpResponse.getEntity();

            Map values = mapper.readValue(responseEntity.getContent(), Map.class);

            isValid((List<Map<String, Object>>) values.get("messages"), allowedErrorMessages, page, errors);

        } catch (IOException e) {
            errors.put(page, e.getMessage());
        }
    }
    private void isValid(List<Map<String, Object>> messages, List<String> allowedErrorMessages, Page page, Map<Page, String> errors) {
        StringBuilder errorMessageBuilder = new StringBuilder();
        for (Map<String, Object> message : messages){
            String type = (String) message.get("type");
            String validationMessage = (String) message.get("message");
            boolean messageIsNotAllowed = !allowedErrorMessages.contains(validationMessage);
            if("error".equals(type) && messageIsNotAllowed){
                StringBuilder explanation = new StringBuilder();
                explanation.append(message.get("explanation"));
                explanation.delete(explanation.indexOf("<p"), explanation.indexOf("</p>") + 7);
                if(explanation.indexOf("href") > 10) {
                    explanation.delete(explanation.indexOf("href") - 15, explanation.length());
                    explanation.append("</br>");
                }
                errorMessageBuilder = errorMessageBuilder.append("<b> Line: ").append(message.get("lastLine")).append(", Column: ").append(message.get("lastColumn")).append("</b> </br>").append("Error: ").append(validationMessage).append(".").append(explanation).append("</br>");
            }
            errors.put(page, errorMessageBuilder.toString());
        }
    }

    public static void main(String[] args) throws MojoExecutionException, MojoFailureException {
        new ValidatorMojo().execute();
    }
}
