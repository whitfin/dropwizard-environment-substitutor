package io.whitfin.dropwizard.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.jackson.Jackson;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;


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
        try (final InputStream in = this.delegate.open(path)) {
            final ObjectNode config = this.mapper.readValue(in, ObjectNode.class);
            replace(config, this.namespace);
            return new ByteArrayInputStream(this.mapper.writeValueAsBytes(config));
        }
    }

    /**
     * Replaces all overridden properties from the environment.
     *
     * @param node
     *      the current map node to replace within.
     * @param prefix
     *      the prefix of the path taken so far.
     */
    private void replace(final ObjectNode node, String prefix) {
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
            Map.Entry<String, JsonNode> field = it.next();

            final String key = field.getKey();
            String path = createPrefix(key, prefix);

            replace(field.getValue(), path, new Overrider() {
                @Override
                public void accept(String override) {
                    node.put(key, override);
                }
            });
        }
    }

    /**
     * Replaces all overridden properties from the environment.
     *
     * @param node
     *      the current list node to replace within.
     * @param prefix
     *      the prefix of the path taken so far.
     */
    private void replace(final ArrayNode node, String prefix) {
        for (int i = 0, j = node.size(); i < j; i++) {
            final int k = i;
            String path = createPrefix("" + i, prefix);
            replace(node.path(i), path, new Overrider() {
                @Override
                public void accept(String override) {
                    JsonNode value;
                    try {
                        value = mapper.readTree(override);
                    } catch (IOException e) {
                        value = TextNode.valueOf(override);
                    }
                    node.set(k, value);
                }
            });
        }
    }

    /**
     * Replaces all overridden properties from the environment.
     *
     * @param node
     *      the current node being processed.
     * @param path
     *      the path to the current node.
     * @param overrider
     *      an override to apply on value nodes.
     */
    private void replace(JsonNode node, String path, Overrider overrider) {
        if (node.isObject()) {
            replace((ObjectNode) node, path);
            return;
        }

        if (node.isArray()) {
            replace((ArrayNode) node, path);
            return;
        }

        String override = System.getenv(path);
        if (override == null) {
            return;
        }

        overrider.accept(override);
    }

    /**
     * Generates a path from a key and prefix.
     *
     * @param key
     *      the key being processed.
     * @param prefix
     *      the prefix to apply to the key.
     * @return
     *      a String prefix to continue using.
     */
    private String createPrefix(String key, String prefix) {
        return prefix + "_" + UPPER_CAMEL
                .to(LOWER_UNDERSCORE, key)
                .replace('.', '_')
                .replace('-', '_')
                .toUpperCase();
    }

    /**
     * Small consumer interface for JDK7.
     */
    private interface Overrider {

        /**
         * Accepts an override as a String.
         *
         * @param override
         *      the overridden value to use.
         */
        void accept(String override);
    }
}
