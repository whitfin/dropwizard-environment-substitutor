package io.whitfin.dropwizard.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.zackehh.dotnotes.DotNotes;
import com.zackehh.dotnotes.ParseException;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.jackson.Jackson;
import io.whitfin.dottie.joiner.NotationJoiner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

/**
 * A delegating {@link ConfigurationSourceProvider} which replaces variables
 * in the underlying configuration source according to any provided environment
 * overrides (automatically).
 */
public class EnvironmentSubstitutor implements ConfigurationSourceProvider {

    /**
     * The original configuration provider (from the config file).
     */
    private final ConfigurationSourceProvider delegate;

    /**
     * A YAML mapper used to read in the base YAML configuration.
     */
    private final ObjectMapper mapper;

    /**
     * The namespace prefix to use to detect envrionment variables.
     */
    private final String namespace;

    /**
     * Create a new instance.
     *
     * @param namespace
     *      the namespace of allowed configuration overrides.
     * @param delegate
     *      the underlying {@link ConfigurationSourceProvider}.
     */
    public EnvironmentSubstitutor(String namespace, ConfigurationSourceProvider delegate) {
        this(namespace, delegate, Jackson.newObjectMapper(new YAMLFactory()));
    }

    /**
     * Create a new instance.
     *
     * @param namespace
     *      the namespace of allowed configuration overrides.
     * @param delegate
     *      the underlying {@link ConfigurationSourceProvider}.
     * @param mapper
     *      a custom {@link ObjectMapper} to use when reading configuration.
     */
    public EnvironmentSubstitutor(String namespace, ConfigurationSourceProvider delegate, ObjectMapper mapper) {
        this.namespace = Objects.requireNonNull(namespace).toUpperCase();
        this.delegate = Objects.requireNonNull(delegate);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InputStream open(String path) throws IOException {
        // use the delegate to open the base configuration
        try (final InputStream in = this.delegate.open(path)) {
            // read in the configuration object and construct a notation joiner
            final ObjectNode config = this.mapper.readValue(in, ObjectNode.class);
            final NotationJoiner joiner = new NotationJoiner();

            // iterate all pairs of properties in the process environment
            for (Map.Entry<String, String> prop : System.getenv().entrySet()) {
                // pull the next key/value pair
                final String key = prop.getKey();
                final String value = prop.getValue();

                // skip any keys outside of the namespace
                if (!key.startsWith(this.namespace + "_")) {
                    continue;
                }

                // iterate all segments of the key (denoted by the "_" character in the env)
                for (String segment : key.substring(this.namespace.length() + 1).split("_")) {
                    // lower-case the segment value
                    segment = segment.toLowerCase();

                    // parse the key segment
                    JsonNode parsed;
                    try {
                        parsed = this.mapper.readTree(segment);
                    } catch (IOException e) {
                        parsed = TextNode.valueOf(segment);
                    }

                    // append a new textual field
                    if (parsed.isTextual()) {
                        joiner.append(parsed.asText());
                    }

                    // append a numeric index
                    if (parsed.isNumber()) {
                        joiner.append(parsed.asInt());
                    }
                }

                // attempt to parse the value
                JsonNode parsedValue;
                try {
                    parsedValue = this.mapper.readTree(value);
                } catch (IOException e) {
                    parsedValue = TextNode.valueOf(value);
                }

                // attempt to set the new value
                try {
                    DotNotes.create(config, joiner.toString(), parsedValue);
                } catch (ParseException e) {
                    // we're unable to substitute for some reason?
                }

                // re-use the joiner
                joiner.reset();
            }

            // turn the updated node back into a byte stream for continuity
            return new ByteArrayInputStream(this.mapper.writeValueAsBytes(config));
        }
    }
}
