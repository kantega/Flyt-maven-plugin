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

package no.kantega.aksess.mojo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SystemProperties
 * <p/>
 * Map of name to SystemProperty.
 * <p/>
 * When a SystemProperty instance is added, if it has not
 * been already set (eg via the command line java system property)
 * then it will be set.
 *
 * Based on SystemProperties in Jetty-maven-plugin
 */
public class SystemProperties {
    private final Map<String, SystemProperty> properties;

    public SystemProperties() {
        properties = new HashMap<>();
    }

    public void setSystemProperty(SystemProperty prop) {
        properties.put(prop.getName(), prop);
    }

    public SystemProperty getSystemProperty(String name) {
        return properties.get(name);
    }

    public boolean containsSystemProperty(String name) {
        return properties.containsKey(name);
    }

    public List<SystemProperty> getSystemProperties() {
        return new ArrayList<>(properties.values());
    }
}
