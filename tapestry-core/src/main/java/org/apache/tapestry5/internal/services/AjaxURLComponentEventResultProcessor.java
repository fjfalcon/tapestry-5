//  Copyright 2008, 2010, 2012 The Apache Software Foundation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.apache.tapestry5.internal.services;

import org.apache.tapestry5.internal.InternalConstants;
import org.apache.tapestry5.json.JSONObject;
import org.apache.tapestry5.services.Ajax;
import org.apache.tapestry5.services.ComponentEventResultProcessor;

import java.io.IOException;
import java.net.URL;

/**
 * Handles {@link java.net.URL} result types by building a JSON response with key "redirectURL".
 */
@SuppressWarnings("all")
public class AjaxURLComponentEventResultProcessor implements ComponentEventResultProcessor<URL>
{
    private final ComponentEventResultProcessor masterProcessor;

    public AjaxURLComponentEventResultProcessor(@Ajax ComponentEventResultProcessor masterProcessor)
    {
        this.masterProcessor = masterProcessor;
    }

    public void processResultValue(URL value) throws IOException
    {
        JSONObject response = new JSONObject();

        response.in(InternalConstants.PARTIAL_KEY).put("redirectURL", value.toExternalForm() );

        masterProcessor.processResultValue(response);
    }
}
