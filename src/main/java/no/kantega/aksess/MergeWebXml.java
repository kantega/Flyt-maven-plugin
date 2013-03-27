/*
 * Copyright 2009 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.kantega.aksess;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

/**
 * Created by IntelliJ IDEA.
 * User: bjorsnos
 * Date: Nov 16, 2007
 * Time: 1:45:54 PM
 */
public class MergeWebXml {

    public static void main(String[] args) throws TransformerException, IOException, ParserConfigurationException, SAXException {
        URL in = new URL(args[0]);
        File  out = new File(args[1]);
        URL xsl = MergeWebXml.class.getClassLoader().getResource("merge_web_xml.xsl");
        File doc = new File(args[2]);
        System.out.println("In: " + in);
        System.out.println("Out: " + out);
        System.out.println("Xsl: " + xsl);
        System.out.println("Doc: " + doc);

        if(!doc.exists()) {
            usage("Input document (project web.xml) " + xsl +" does not exist");
        }

        out.getParentFile().mkdirs();

        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer(new StreamSource(xsl.openStream()));
        final String factoryKey = "javax.xml.parsers.DocumentBuilderFactory";
        String existingProperty = System.getProperty(factoryKey);
        System.setProperty(factoryKey, "org.apache.xerces.jaxp.DocumentBuilderFactoryImpl");
        final DocumentBuilderFactory docFac;
        try {
            docFac = DocumentBuilderFactory.newInstance();
        } finally {
            if(existingProperty != null) {
                System.setProperty(factoryKey, existingProperty);
            } else {
                System.clearProperty(factoryKey);
            }
        }
        docFac.setNamespaceAware(false);
        transformer.setParameter("doc", docFac.newDocumentBuilder().parse(doc).getDocumentElement());
        transformer.transform(new StreamSource(in.openStream()), new StreamResult(new FileOutputStream(out)));
    }

    private static void usage(String message) {
        if(message != null) {
            System.err.println("Error: " + message);
        }
        System.err.println("Usage: MergeWebXml input-web.xml output-web.xml merge-stylesheet.xsl input2-web.xml");
        System.exit(1);
    }

    private static Object getDocument(String doc) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return builder.parse(new InputSource(doc));
    }
}
