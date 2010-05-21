// Copyright 2008, 2010 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.apache.tapestry5.internal.services;

import java.util.Map;

import org.apache.tapestry5.ioc.internal.util.CollectionFactory;
import org.apache.tapestry5.json.JSONArray;
import org.apache.tapestry5.json.JSONObject;
import org.apache.tapestry5.services.javascript.InitializationPriority;

public class PartialMarkupDocumentLinker implements DocumentLinker
{
    private final JSONArray scripts = new JSONArray();

    private final JSONArray stylesheets = new JSONArray();

    private final Map<InitializationPriority, StringBuilder> priorityToScript = CollectionFactory.newMap();

    private final Map<InitializationPriority, JSONObject> priorityToInits = CollectionFactory.newMap();

    public void addScriptLink(String scriptURL)
    {
        scripts.put(scriptURL);
    }

    public void addStylesheetLink(StylesheetLink stylesheet)
    {
        JSONObject object = new JSONObject(

        "href", stylesheet.getUrl(),

        "media", stylesheet.getOptions().getMedia());

        stylesheets.put(object);
    }

    public void addScript(InitializationPriority priority, String script)
    {
        StringBuilder builder = priorityToScript.get(priority);

        if (builder == null)
        {
            builder = new StringBuilder();
            priorityToScript.put(priority, builder);
        }

        builder.append(script);
        builder.append("\n");
    }

    public void setInitialization(InitializationPriority priority, JSONObject initialization)
    {
        priorityToInits.put(priority, initialization);
    }

    /**
     * Commits changes, adding one or more keys to the reply.
     * 
     * @param reply
     *            JSON Object to be sent to client
     */
    public void commit(JSONObject reply)
    {
        if (scripts.length() > 0)
            reply.put("scripts", scripts);

        if (stylesheets.length() > 0)
            reply.put("stylesheets", stylesheets);

        StringBuilder master = new StringBuilder();
        JSONArray inits = new JSONArray();

        for (InitializationPriority p : InitializationPriority.values())
        {
            StringBuilder builder = priorityToScript.get(p);

            if (builder != null)
                master.append(builder);

            JSONObject init = priorityToInits.get(p);

            if (init != null)
                inits.put(init);
        }

        if (master.length() > 0)
            reply.put("script", master.toString());

        if (inits.length() > 0)
            reply.put("inits", inits);
    }
}
