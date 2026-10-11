package com.eu.habbo.messages.outgoing.generic.alerts;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class WiredValidationErrorMessageComposer extends MessageComposer {
    public static final String GENERIC_MESSAGE_KEY = "wiredfurni.error.server_message";

    private final String localizationKey;
    private final Map<String, String> parameters;

    public WiredValidationErrorMessageComposer(String message) {
        this(GENERIC_MESSAGE_KEY, Collections.singletonMap("message", message));
    }

    public WiredValidationErrorMessageComposer(String localizationKey, Map<String, String> parameters) {
        this.localizationKey = localizationKey;
        this.parameters = new LinkedHashMap<>(parameters);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredValidationErrorMessageComposer);
        this.response.appendString(this.localizationKey);
        this.response.appendInt(this.parameters.size());
        for (Map.Entry<String, String> parameter : this.parameters.entrySet()) {
            this.response.appendString(parameter.getKey());
            this.response.appendString(parameter.getValue());
        }
        return this.response;
    }

    public String getMessage() {
        return this.parameters.get("message");
    }

    public String getLocalizationKey() {
        return this.localizationKey;
    }

    public Map<String, String> getParameters() {
        return Collections.unmodifiableMap(this.parameters);
    }
}
