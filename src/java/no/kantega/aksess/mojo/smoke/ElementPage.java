package no.kantega.aksess.mojo.smoke;

import org.jdom.Element;

/**
 *
 */
public class ElementPage implements Page {
    private final Element elem;

    public ElementPage(Element elem) {
        this.elem = elem;
    }

    public String getUrl() {
        return elem.getAttributeValue("url");
    }

    public String getDisplayTemplate() {
        return elem.getAttributeValue("displayTemplate");
    }

    public String getTitle() {
        return elem.getAttributeValue("title");
    }

    public int getContentId() {
        return Integer.parseInt(elem.getAttributeValue("contentId"));
    }
}
