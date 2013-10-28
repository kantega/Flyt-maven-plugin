package no.kantega.aksess;

import com.sun.codemodel.*;
import org.apache.maven.plugin.MojoExecutionException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class MakeAksessTemplateConfig {

    private static final int STATIC_FINAL = JMod.PUBLIC | JMod.STATIC | JMod.FINAL;
    public static final String AKSESS_TEMPLATE_CONFIG_JAVA = "AksessTemplateConfig";

    private static XPath xpath = XPathFactory.newInstance().newXPath();
    private static DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();

    // http://docs.oracle.com/javase/specs/jls/se5.0/html/lexical.html#3.9
    private static final List<String> reservedWords = Arrays.asList(
            "abstract","continue","for","new",
            "switch","assert","default","if",
            "package","synchronized","boolean",
            "do","goto","private","this","break",
            "double","implements","protected","throw",
            "byte","else","import","public","throws",
            "case","enum","instanceof","return","transient",
            "catch","extends","int","short","try","char",
            "final","interface","static","void","class",
            "finally","long","strictfp","volatile","const",
            "float","native","super","while");

    public static File createAksessTemplateConfigSources(File aksessTemplateConfigXml, String projectPackage, File destinationFolder ) throws MojoExecutionException {
        return createAksessTemplateConfigSources(aksessTemplateConfigXml, projectPackage, destinationFolder, false);
    }

    public static File createAksessTemplateConfigSources(File aksessTemplateConfigXml, String projectPackage, File destinationFolder, boolean includeAllAttributes ) throws MojoExecutionException {
        try {
            Document doc = getDocument(aksessTemplateConfigXml);

            JCodeModel jCodeModel = new JCodeModel();
            JPackage jp = jCodeModel._package(projectPackage);
            JDefinedClass jc = jp._class(JMod.PUBLIC | JMod.FINAL, AKSESS_TEMPLATE_CONFIG_JAVA);

            setSites(doc,xpath, jCodeModel, jc);

            setAssociationCategories(doc, xpath, jCodeModel, jc);

            setDocumentTypes(doc, xpath, jCodeModel, jc);

            File templates = getTemplateRootDir(aksessTemplateConfigXml);
            setContentTemplates(doc, xpath, jCodeModel, jc, templates, includeAllAttributes);
            setMetaDateTemplates(doc, xpath, jCodeModel, jc, templates);

            setDisplayTemplates(doc, xpath, jCodeModel, jc, includeAllAttributes);
            jCodeModel.build(destinationFolder);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to create AksessTemplateConfig.java", e);
        }

        File generatedFile = new File(destinationFolder, projectPackage.replaceAll("\\.", "/") + "/" + AKSESS_TEMPLATE_CONFIG_JAVA + ".java");
        throwIfDoesNotExist(generatedFile);
        return generatedFile;
    }

    private static Document getDocument(File aksessTemplateConfigXml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(aksessTemplateConfigXml);
        doc.getDocumentElement().normalize();
        return doc;
    }

    private static File getTemplateRootDir(File aksessTemplateConfigXml) {
        File webinf = aksessTemplateConfigXml.getParentFile();
        return new File(webinf, "templates/content");
    }

    private static void throwIfDoesNotExist(File generatedFile) {
        if(!generatedFile.exists()){
            throw new IllegalStateException(generatedFile.getAbsolutePath() + " was not generated not exist!");
        }
    }

    private static void setDisplayTemplates(Document doc, XPath xpath, JCodeModel jCodeModel, JDefinedClass jc, boolean includeAllAttributes) throws JClassAlreadyExistsException, XPathExpressionException {
        JDefinedClass displayTemplatesClass = jc._class(STATIC_FINAL, "displayTemplates");
        NodeList displayTemplates = getNodeList(doc, xpath, "displayTemplates", "displayTemplate");
        for(int i = 0; i < displayTemplates.getLength(); i++){
            Element displayTemplate = (Element) displayTemplates.item(i);

            String databaseId = displayTemplate.getAttribute("databaseId");
            String id = displayTemplate.getAttribute("id");

            String contentTemplate = getAttributeFromTagWithName("contentTemplate", "id", displayTemplate);
            String metaDataTemplate = getAttributeFromTagWithName("metaDataTemplate", "id", displayTemplate);
            String name = getSingleValueFromTagWithName("name", displayTemplate);

            JDefinedClass displayTemplateClass = displayTemplatesClass._class(STATIC_FINAL, cleanFieldName(id));
            setIdNameAndDatabaseId(jCodeModel, databaseId, id, name, displayTemplateClass);

            displayTemplateClass.field(STATIC_FINAL, String.class, "contentTemplate", JExpr.lit(contentTemplate));
            if (isNotBlank(metaDataTemplate)) {
                displayTemplateClass.field(STATIC_FINAL, String.class, "metaDataTemplate", JExpr.lit(metaDataTemplate));
            }

            if (includeAllAttributes) {
                String allowMultipleUsages = displayTemplate.getAttribute("allowMultipleUsages");
                displayTemplateClass.field(STATIC_FINAL, jCodeModel.BOOLEAN, "allowMultipleUsages", JExpr.lit(Boolean.parseBoolean(allowMultipleUsages)));
                String isNewGroup = displayTemplate.getAttribute("isNewGroup");
                displayTemplateClass.field(STATIC_FINAL, jCodeModel.BOOLEAN, "isNewGroup", JExpr.lit(Boolean.parseBoolean(isNewGroup)));

                String description = displayTemplate.getAttribute("description");
                displayTemplateClass.field(STATIC_FINAL, String.class, "description", JExpr.lit(description));

                String view = getSingleValueFromTagWithName("view", displayTemplate);
                displayTemplateClass.field(STATIC_FINAL, String.class, "view", JExpr.lit(view));
                String rssView = getSingleValueFromTagWithName("rssView", displayTemplate);
                displayTemplateClass.field(STATIC_FINAL, String.class, "rssView", JExpr.lit(rssView));
                String miniView = getSingleValueFromTagWithName("miniView", displayTemplate);
                displayTemplateClass.field(STATIC_FINAL, String.class, "miniView", JExpr.lit(miniView));
                addClassAndFieldsForSubNodesWithId(displayTemplate, displayTemplateClass, "sites", "site");
                addClassAndFieldsForSubNodesWithTextContent(displayTemplate, displayTemplateClass, "controllers", "controller");
            }
        }
    }

    private static void setContentTemplates(Document doc, XPath xpath, JCodeModel jCodeModel, JDefinedClass jc, File templates, boolean includeAllAttributes) throws JClassAlreadyExistsException, XPathExpressionException, ParserConfigurationException, IOException, SAXException {
        JDefinedClass contentTemplatesClass = jc._class(STATIC_FINAL, "contentTemplates");
        NodeList contentTemplates = getNodeList(doc, xpath, "contentTemplates", "contentTemplate");
        addContentTemplates(jCodeModel, contentTemplatesClass, contentTemplates, templates, includeAllAttributes);
    }

    private static void setMetaDateTemplates(Document doc, XPath xpath, JCodeModel jCodeModel, JDefinedClass jc, File templates) throws JClassAlreadyExistsException, XPathExpressionException, ParserConfigurationException, IOException, SAXException {
        JDefinedClass contentTemplatesClass = jc._class(STATIC_FINAL, "metaDataTemplates");
        NodeList contentTemplates = getNodeList(doc, xpath, "metadataTemplates", "contentTemplate");
        addContentTemplates(jCodeModel, contentTemplatesClass, contentTemplates, templates, false);
    }

    private static void addContentTemplates(JCodeModel jCodeModel, JDefinedClass contentTemplatesClass, NodeList contentTemplates, File templates, boolean includeAllAttributes) throws JClassAlreadyExistsException, ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        for(int i = 0; i < contentTemplates.getLength(); i++){
            Element contentTemplate = (Element) contentTemplates.item(i);
            String databaseId = contentTemplate.getAttribute("databaseId");
            String id = contentTemplate.getAttribute("id");

            JDefinedClass contentTemplateClass = contentTemplatesClass._class(STATIC_FINAL, cleanFieldName(id));
            String name = getSingleValueFromTagWithName("name", contentTemplate);
            setIdNameAndDatabaseId(jCodeModel, databaseId, id, name, contentTemplateClass);
            String contentType = contentTemplate.getAttribute("contentType");
            contentTemplateClass.field(STATIC_FINAL, String.class, "contentType", JExpr.lit(contentType));

            addClassAndFieldsForSubNodesWithId(contentTemplate, contentTemplateClass, "associationCategories", "associationCategory");

            String templateFile = getSingleValueFromTagWithName("templateFile", contentTemplate);
            String documentType = getAttributeFromTagWithName("documentType", "id", contentTemplate);
            if (isNotBlank(documentType)){
                contentTemplateClass.field(STATIC_FINAL, String.class, "documentType", JExpr.lit(documentType));
            }

            addContentTemplateAttributes(templates, contentTemplateClass, templateFile);
            contentTemplateClass.field(STATIC_FINAL, String.class, "templateFile", JExpr.lit(templateFile));

            if (includeAllAttributes) {
                String expireAction = contentTemplate.getAttribute("expireAction");
                if (isNotBlank(expireAction)){
                    contentTemplateClass.field(STATIC_FINAL, jCodeModel.INT, "expireAction", JExpr.lit(Integer.parseInt(expireAction)));
                }

                String expireMonths = contentTemplate.getAttribute("expireMonths");
                if (isNotBlank(expireMonths)){
                    contentTemplateClass.field(STATIC_FINAL, jCodeModel.INT, "expireMonths", JExpr.lit(Integer.parseInt(expireMonths)));
                }

                String isDefaultSearchable = contentTemplate.getAttribute("isDefaultSearchable");
                contentTemplateClass.field(STATIC_FINAL, jCodeModel.BOOLEAN, "isDefaultSearchable", JExpr.lit(isDefaultSearchable == null || Boolean.parseBoolean(isDefaultSearchable)));

                String isSearchable = contentTemplate.getAttribute("isSearchable");
                contentTemplateClass.field(STATIC_FINAL, jCodeModel.BOOLEAN, "isSearchable", JExpr.lit(isSearchable == null || Boolean.parseBoolean(isSearchable)));

                String isHearingEnabled = contentTemplate.getAttribute("isHearingEnabled");
                contentTemplateClass.field(STATIC_FINAL, jCodeModel.BOOLEAN, "isHearingEnabled", JExpr.lit(Boolean.parseBoolean(isHearingEnabled)));

                String keepVersions = contentTemplate.getAttribute("keepVersions");
                if (isNotBlank(keepVersions)){
                    contentTemplateClass.field(STATIC_FINAL, jCodeModel.INT, "keepVersions", JExpr.lit(Integer.parseInt(keepVersions)));
                }

                String defaultPageUrlAlias = getSingleValueFromTagWithName("defaultPageUrlAlias", contentTemplate);
                if (isNotBlank(defaultPageUrlAlias)){
                    contentTemplateClass.field(STATIC_FINAL, String.class, "defaultPageUrlAlias", JExpr.lit(defaultPageUrlAlias));
                }

                String documentTypeForChildren = getAttributeFromTagWithName("documentTypeForChildren", "id", contentTemplate);
                if (isNotBlank(documentTypeForChildren)){
                    contentTemplateClass.field(STATIC_FINAL, String.class, "documentTypeForChildren", JExpr.lit(documentTypeForChildren));
                }
                addClassAndFieldsForSubNodesWithId(contentTemplate, contentTemplateClass, "allowedParentTemplates", "contentTemplate");
            }
        }
    }

    private static void addContentTemplateAttributes(File templates, JDefinedClass contentTemplateClass, String templateFile) throws JClassAlreadyExistsException, ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        JDefinedClass attributesClass = contentTemplateClass._class(STATIC_FINAL, "attributes");
        File contentTemplate = new File(templates, templateFile);
        Document document = getDocument(contentTemplate);
        NodeList attributes = getAttributeNodeList(document, xpath, "attribute");
        for(int i = 0; i < attributes.getLength(); i++){
            Element attribute = (Element) attributes.item(i);
            String name = attribute.getAttribute("name");
            attributesClass.field(STATIC_FINAL, String.class, cleanFieldName(name), JExpr.lit(name));
        }

        NodeList repeaterattributes = getAttributeNodeList(document, xpath, "repeater");
        for(int i = 0; i < repeaterattributes.getLength(); i++){
            Element attribute = (Element) repeaterattributes.item(i);
            String name = attribute.getAttribute("name");
            JDefinedClass attributeClass = attributesClass._class(STATIC_FINAL, cleanFieldName(name));
            attributeClass.field(STATIC_FINAL, String.class, "repeater_name", JExpr.lit(name));

            NodeList repeatersubattributes = attribute.getChildNodes();
            for(int j = 0; j < repeatersubattributes.getLength(); j++){
                Node item = repeatersubattributes.item(j);
                String localName = item.getNodeName();
                if(localName != null && localName.equals("attribute")){
                    Element subattribute = (Element) item;
                    String subattributeName = subattribute.getAttribute("name");

                    attributeClass.field(STATIC_FINAL, String.class, cleanFieldName(subattributeName), JExpr.lit(subattributeName));
                }
            }

        }

    }

    private static void addClassAndFieldsForSubNodesWithId(Element parentNode, JDefinedClass parentClass, String containingNode, String targetNodeName) throws JClassAlreadyExistsException {
        NodeList allowedParentTemplates = parentNode.getElementsByTagName(targetNodeName);
        if (allowedParentTemplates.getLength() > 0) {
            JDefinedClass allowedParentTemplatesClass = parentClass._class(STATIC_FINAL, containingNode);
            for(int j = 0; j < allowedParentTemplates.getLength(); j++){
                Element allowedParent = (Element) allowedParentTemplates.item(j);
                String allowedParentId = allowedParent.getAttribute("id");
                allowedParentTemplatesClass.field(STATIC_FINAL, String.class, cleanFieldName(allowedParentId), JExpr.lit(allowedParentId));
            }
        }
    }

    private static void addClassAndFieldsForSubNodesWithTextContent(Element parentNode, JDefinedClass parentClass, String containingNode, String targetNodeName) throws JClassAlreadyExistsException {
        NodeList nodes = parentNode.getElementsByTagName(targetNodeName);
        if (nodes.getLength() > 0) {
            JDefinedClass allowedParentTemplatesClass = parentClass._class(STATIC_FINAL, containingNode);
            for(int j = 0; j < nodes.getLength(); j++){
                Element allowedParent = (Element) nodes.item(j);
                String allowedParentId = allowedParent.getTextContent();
                allowedParentTemplatesClass.field(STATIC_FINAL, String.class, cleanFieldName(allowedParentId), JExpr.lit(allowedParentId));
            }
        }

    }

    private static void setIdNameAndDatabaseId(JCodeModel jCodeModel, String databaseId, String id, String name, JDefinedClass templateClass) {
        templateClass.field(STATIC_FINAL, String.class, "id", JExpr.lit(id));
        templateClass.field(STATIC_FINAL, String.class, "name", JExpr.lit(name));
        templateClass.field(STATIC_FINAL, jCodeModel.INT, "databaseId", JExpr.lit(Integer.parseInt(databaseId)));
    }

    private static String cleanFieldName(String fieldName) {
        String cleaned = reservedWords.contains(fieldName) ? fieldName + '_' : fieldName;
        cleaned = cleaned.replaceAll("å", "a").replaceAll("ø", "o").replaceAll("æ", "a");

        StringBuilder fieldNameBuilder = new StringBuilder(fieldName.length());
        char[] chars = cleaned.toCharArray();
        if(Character.isJavaIdentifierStart(chars[0])){
            fieldNameBuilder.append(chars[0]);
        } else {
            fieldNameBuilder.append('_');
        }
        for (int i = 1; i < chars.length; i++) {
            if(Character.isJavaIdentifierPart(chars[i])){
                fieldNameBuilder.append(chars[i]);
            } else {
                fieldNameBuilder.append('_');
            }

        }
        return fieldNameBuilder.toString();
    }

    private static void setDocumentTypes(Document doc, XPath xpath, JCodeModel jCodeModel, JDefinedClass jc) throws JClassAlreadyExistsException, XPathExpressionException {
        JDefinedClass documentTypesClass = jc._class(STATIC_FINAL, "documentTypes");
        NodeList documentTypes = getNodeList(doc, xpath, "documentTypes", "documentType");
        for(int i = 0; i < documentTypes.getLength(); i++){
            Element documentType = (Element) documentTypes.item(i);
            String databaseId = documentType.getAttribute("databaseId");
            String id = documentType.getAttribute("id");
            String name = getSingleValueFromTagWithName("name", documentType);

            JDefinedClass documentTypeClass = documentTypesClass._class(STATIC_FINAL, cleanFieldName(id));
            setIdNameAndDatabaseId(jCodeModel, databaseId, id, name, documentTypeClass);
        }
    }

    private static void setAssociationCategories(Document doc, XPath xpath, JCodeModel jCodeModel, JDefinedClass jc) throws JClassAlreadyExistsException, XPathExpressionException {

        JDefinedClass associationCategoriesClass = jc._class(STATIC_FINAL, "associationCategories");
        NodeList associationCategories = getNodeList(doc, xpath, "associationCategories", "associationCategory");
        for(int i = 0; i < associationCategories.getLength(); i++){
            Element associationCategory = (Element) associationCategories.item(i);
            String databaseId = associationCategory.getAttribute("databaseId");
            String id = associationCategory.getAttribute("id");
            String name = getSingleValueFromTagWithName("name", associationCategory);
            String description = getSingleValueFromTagWithName("description", associationCategory);

            JDefinedClass associationCategoryClass = associationCategoriesClass._class(STATIC_FINAL, cleanFieldName(id));
            setIdNameAndDatabaseId(jCodeModel, databaseId, id, name, associationCategoryClass);
            if (isNotBlank(description)) {
                associationCategoryClass.field(STATIC_FINAL, String.class, "description", JExpr.lit(description));
            }

        }
    }

    private static void setSites(Document doc, XPath xpath, JCodeModel codeModel, JDefinedClass jc) throws JClassAlreadyExistsException, XPathExpressionException {

        JDefinedClass sitesClass = jc._class(STATIC_FINAL, "sites");
        NodeList sites = getNodeList(doc, xpath, "sites", "site");
        for(int i = 0; i < sites.getLength(); i++){
            Element site = (Element) sites.item(i);
            String id = site.getAttribute("id");
            String databaseId = site.getAttribute("databaseId");
            String alias = site.getAttribute("alias");
            String displayTemplateId = site.getAttribute("displayTemplateId");
            String isDefault = site.getAttribute("isDefault");
            String name = getSingleValueFromTagWithName("name", site);
            String disabled = getSingleValueFromTagWithName("disabled", site);

            JDefinedClass siteClass = sitesClass._class(STATIC_FINAL, cleanFieldName(id));
            setIdNameAndDatabaseId(codeModel, databaseId, id, name, siteClass);
            siteClass.field(STATIC_FINAL, String.class, "alias", JExpr.lit(alias));
            if (isNotBlank(displayTemplateId)) {
                siteClass.field(STATIC_FINAL, String.class, "displayTemplateId", JExpr.lit(displayTemplateId));
            }
            siteClass.field(STATIC_FINAL, codeModel.BOOLEAN, "isDefault", JExpr.lit(Boolean.parseBoolean(isDefault)));
            siteClass.field(STATIC_FINAL, codeModel.BOOLEAN, "disabled", JExpr.lit(Boolean.parseBoolean(disabled)));

        }
    }

    private static NodeList getNodeList(Document doc, XPath xpath, String parentNode, String nodeName) throws XPathExpressionException {
        return getNodeListXpath(doc, xpath, "templateConfiguration", parentNode, nodeName);
    }

    private static NodeList getAttributeNodeList(Document doc, XPath xpath, String nodeName) throws XPathExpressionException {
        return getNodeListXpath(doc, xpath, "template", "attributes", nodeName);
    }

    private static NodeList getNodeListXpath(Document doc, XPath xpath, String root, String parentNode, String nodeName) throws XPathExpressionException {
        XPathExpression sitesExpression = xpath.compile("*[local-name() = '" + root + "']/*[local-name() = '" + parentNode + "']/*[local-name() = '" + nodeName + "']");
        return (NodeList) sitesExpression.evaluate(doc, XPathConstants.NODESET);
    }

    private static String getSingleValueFromTagWithName(String tagName, Element parent){
        String value = "";
        NodeList nodes = parent.getElementsByTagName(tagName);
        if(nodes.getLength() > 0){
            value = nodes.item(0).getTextContent();
        }
        return value;
    }

    private static String getAttributeFromTagWithName(String tagName, String attributeName, Element parent){
        String value = "";
        NodeList nodes = parent.getElementsByTagName(tagName);
        if(nodes.getLength() > 0){
            value = ((Element)nodes.item(0)).getAttribute(attributeName);
        }
        return value;
    }
}
