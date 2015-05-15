package no.kantega.aksess.mojo;

/**
 * SystemProperty
 * <p/>
 * Provides the ability to set System properties
 * for the mojo execution. A value will only
 * be set if it is not set already. That is, if
 * it was set on the command line or by the system,
 * it won't be overridden by settings in the
 * plugin's configuration.
 * Based on SystemProperty in Jetty-maven-plugin
 */
public class SystemProperty {
    private String name;
    private String value;

    /**
     * @return Returns the name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * @param name The name to set.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return Returns the value.
     */
    public String getValue() {
        return this.value;
    }

    /**
     * @param value The value to set.
     */
    public void setValue(String value) {
        this.value = value;
    }

}
