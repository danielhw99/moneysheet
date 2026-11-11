package com.danielhw99.common.api;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public class ApiIdDeserializer extends StdDeserializer<ApiId> {

    public ApiIdDeserializer() {
        super(ApiId.class);
    }

    @Override
    public ApiId deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return context.reportInputMismatch(ApiId.class, "ID must be a JSON string.");
        }
        try {
            return ApiId.parse(parser.getString());
        } catch (IllegalArgumentException exception) {
            return context.reportInputMismatch(ApiId.class, "Invalid ID string.");
        }
    }
}
