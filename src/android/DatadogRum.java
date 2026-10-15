package com.company.datadog;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class DatadogRum extends CordovaPlugin {

    @Override
    public boolean execute(
            String action,
            JSONArray args,
            CallbackContext callbackContext) {

        try {

            if ("init".equals(action)) {

                JSONObject config =
                        args.getJSONObject(0);

                callbackContext.success();

                return true;
            }

        }
        catch (Exception ex) {

            callbackContext.error(
                    ex.getMessage());
        }

        return false;
    }
}