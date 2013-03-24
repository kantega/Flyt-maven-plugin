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

    public String getCategory() {
        return elem.getAttributeValue("category");
    }

    public String getTitle() {
        return elem.getAttributeValue("title");
    }

    public String getId() {
        return elem.getAttributeValue("id");
    }
}
