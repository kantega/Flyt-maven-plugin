package no.kantega.aksess;

import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;

/**
 * http://dev.eclipse.org/mhonarc/lists/jetty-dev/msg00371.html
 */
public class FixJettyUnpackConfiguration implements Configuration {
    public void preConfigure(WebAppContext context) throws Exception {
        final Resource resource = context.getBaseResource();
        if(resource instanceof ResourceCollection) {
            ResourceCollection rc = (ResourceCollection) resource;

            if(rc.getResources().length == 2 && rc.getResources()[1] instanceof ResourceCollection) {
                context.setBaseResource(rc.getResources()[1]);
            }

        }
    }

    public void configure(WebAppContext context) throws Exception {
    }

    public void postConfigure(WebAppContext context) throws Exception {
    }

    public void deconfigure(WebAppContext context) throws Exception {
    }
}
