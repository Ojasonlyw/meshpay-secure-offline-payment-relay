package com.demo.upimesh.dto.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.math.BigDecimal;

/** Preserve the decimal lexeme; reject exponent notation and excess scale without rounding. */
public class DecimalAmountDeserializer extends StdDeserializer<BigDecimal> {
    public DecimalAmountDeserializer() { super(BigDecimal.class); }

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        String value = parser.getText();
        if ((token != JsonToken.VALUE_STRING && token != JsonToken.VALUE_NUMBER_INT
                && token != JsonToken.VALUE_NUMBER_FLOAT)
                || value.length() > 32 || !value.matches("[0-9]+(\\.[0-9]{1,2})?")) {
            return (BigDecimal) context.handleUnexpectedToken(BigDecimal.class, parser);
        }
        return new BigDecimal(value);
    }
}
