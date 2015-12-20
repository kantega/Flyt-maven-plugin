package no.kantega.aksess.mojo.smoke;


import org.w3c.dom.NamedNodeMap;

public class Page {
    public final String url;
    public final String category;
    public final String title;
    public final String id;

    public Page(String url, String category, String title, String id) {
        this.url = url;
        this.category = category;
        this.title = title;
        this.id = id;
    }

    public Page(NamedNodeMap attributes) {
        this(attributes.getNamedItem("url").getNodeValue(),
                attributes.getNamedItem("category").getNodeValue(),
                attributes.getNamedItem("title").getNodeValue(),
                attributes.getNamedItem("id").getNodeValue());
    }

    @Override
    public String toString() {
        return title + " {" +
                "id='" + id + '\'' +
                ", url='" + url + '\'' +
                ", category='" + category + '\'' +
                '}';
    }
}
