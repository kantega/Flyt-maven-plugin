package no.kantega.aksess.mojo;

import java.io.File;

/**
 * @goal jmeter
 */
public class GuiJMeterMojo extends AbstractJMeterMojo {


    @Override
    protected void afterJMeterStarted() {

        synchronized (this) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    protected String[] getJMeterCommandLine(File jmeterTestFile) {
        String[] args = new String[] {"-t", jmeterTestFile.getAbsolutePath()};
        return args;
    }
}
