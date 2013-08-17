package no.kantega.aksess;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

public class MergeWebXmlTest {

    @Test
    public void OAAndProjectWebXmlShouldBeMerged() throws ParserConfigurationException, TransformerException, SAXException, IOException {
        String mergedWebXml = System.getProperty("java.io.tmpdir") + "/mergedweb.xml";
        MergeWebXml.mergeWebXml(
                getClass().getResource("/webinf/web.xml_oa").toExternalForm(),
                mergedWebXml,
                getClass().getResource("/webinf/web.xml_project").getFile()
                );
        File mergedWebXmlFile = new File(mergedWebXml);
        String fileContent = FileUtils.readFileToString(mergedWebXmlFile);
        assertTrue("Merged web.xml did not contain <url-pattern>/content/*</url-pattern>", fileContent.contains("<url-pattern>/content/*</url-pattern>"));
        assertTrue("Merged web.xml did not contain <url-pattern>/renate/*</url-pattern>", fileContent.contains("<url-pattern>/renate/*</url-pattern>"));
    }
}
