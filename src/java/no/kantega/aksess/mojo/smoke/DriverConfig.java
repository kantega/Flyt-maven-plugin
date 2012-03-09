package no.kantega.aksess.mojo.smoke;

import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/**
 *
 */
public class DriverConfig {
    private final WebDriver driver;
    private final String id;

    public DriverConfig(WebDriver driver, String id) {
        this.driver = driver;
        this.id = id;
    }

    public TakesScreenshot getScreenshotTaker() {
        return (TakesScreenshot) driver;
    }

    public WebDriver getDriver() {
        return driver;
    }

    public String getId() {
        return id;
    }
}
