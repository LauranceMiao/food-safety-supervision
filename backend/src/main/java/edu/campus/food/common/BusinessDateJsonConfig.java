package edu.campus.food.common;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.sql.Date;

@Configuration
public class BusinessDateJsonConfig {
    // SQL DATE represents a calendar day, not a UTC instant. Keep it date-only in every API.
    @Bean
    public JsonMapperBuilderCustomizer businessDates() {
        SimpleModule module = new SimpleModule("school-business-dates");
        module.addSerializer(Date.class, new ValueSerializer<Date>() {
            @Override
            public void serialize(Date value, JsonGenerator generator, SerializationContext context) {
                generator.writeString(value.toLocalDate().toString());
            }
        });
        return builder -> builder.addModule(module);
    }
}
