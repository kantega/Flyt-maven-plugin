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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.Date;
import java.util.Properties;


public class MakeVersion {

    public static void main(String[] args) throws ParserConfigurationException, FileNotFoundException {
        File entriesFile = new File(args[0]);
        File versionFile = new File(args[1]);
        String version = args[2];

        String revision = "unknown";
        String date = new Date().toString();

        if(entriesFile.exists()) {

            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

            builder.setErrorHandler(new ErrorHandler() {

                public void warning(SAXParseException saxParseException) throws SAXException {
                }

                public void error(SAXParseException saxParseException) throws SAXException {
                }

                public void fatalError(SAXParseException saxParseException) throws SAXException {
                }
            });
            try {
                Document doc = builder.parse(entriesFile);

                System.out.println("Reading committed-rev and commited-date from old SVN format: " + entriesFile);
                NodeList entries = doc.getElementsByTagName("entry");

                for(int i = 0; i < entries.getLength(); i++) {
                  Node node = entries.item(i);

                    if(node instanceof Element) {
                        Element nodeElement = (Element) node;

                        if("".equals(nodeElement.getAttribute("name"))) {
                            revision = nodeElement.getAttribute("committed-rev");
                            date = nodeElement.getAttribute("committed-date"); 
                        }
                    }
                }
            } catch (SAXException e) {
                System.out.println(entriesFile +" subversion version is >= 1.4");
                try {
                    BufferedReader reader = new BufferedReader(new FileReader(entriesFile));
                    for(int i = 0; i < 9; i++) {
                        reader.readLine();
                    }
                    date = reader.readLine();
                    if (date != null) {
                        revision = reader.readLine();
                    } else {
                        System.out.println(entriesFile + " + unable to read SVN metadata. Support for SVN 1.7 not yet implemented");
                        date = new Date().toString();
                    }
                    if (revision == null) {
                        revision = "unknown";
                    }
                } catch (IOException ex) {
                    System.out.println("Error reading " + entriesFile);
                    ex.printStackTrace();
                    System.exit(-1);
                }
            } catch (IOException e) {
                System.out.println("IOException reading " + entriesFile);
                e.printStackTrace();
                System.exit(-1);
            }
        }

        Properties props = new Properties();
        props.setProperty("revision", revision);
        props.setProperty("date", date);
        props.setProperty("version", version);

        try {
            props.store(new FileOutputStream(versionFile), "iso-8859-1");
        } catch (IOException e) {
            System.out.println("IOException writing " + versionFile +" to disk");
            e.printStackTrace();
            System.exit(-1);
        }

    }
}
