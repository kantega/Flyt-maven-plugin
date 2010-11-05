package no.kantega.aksess.mojo;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

/**
 * @goal jmeter-report
 */
public class ReportJMeterMojo extends AbstractJMeterMojo {
    private File reportFile;

    @Override
    protected void afterJMeterStarted() {

        long start = System.currentTimeMillis();
        while(true) {
            if(hasJMeterEnded() || (System.currentTimeMillis()-start > 60000)) {
                break;
            } else {
                getLog().info("Wating for JMeter to end");
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    private boolean hasJMeterEnded() {
        try {

            byte[] hasEnded = "Test has ended \n".getBytes("ascii");
            if(!reportFile.exists()) {
                return false;
            }
            RandomAccessFile file = new RandomAccessFile(reportFile, "r");
            if(file.length() < hasEnded.length) {
                return false;
            }
            file.seek(file.length()-hasEnded.length);
            byte[] buffer = new byte[hasEnded.length];
            file.read(buffer);
            return Arrays.equals(hasEnded, buffer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    protected String[] getJMeterCommandLine(File jmeterTestFile) {
        reportFile = new File(jmeterTestFile.getParentFile(), "jmeter.log");
        return  new String[] {
                "-t", jmeterTestFile.getAbsolutePath(),
                "-n",
                "-l",  new File(jmeterTestFile.getParentFile(), jmeterTestFile.getName().substring(0, jmeterTestFile.getName().lastIndexOf(".")) + ".jtl").getAbsolutePath(),
                "-j", reportFile.getAbsolutePath()};

    }
}
