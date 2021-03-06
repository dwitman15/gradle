/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.std;

import com.google.common.collect.ImmutableList;
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.logging.text.TreeFormatter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.stream.Collectors;

public class DependenciesSourceGenerator extends AbstractSourceGenerator {

    private static final int MAX_ENTRIES = 30000;
    private final AllDependenciesModel config;

    public DependenciesSourceGenerator(Writer writer,
                                       AllDependenciesModel config) {
        super(writer);
        this.config = config;
    }

    public static void generateSource(Writer writer,
                                      AllDependenciesModel config,
                                      String packageName,
                                      String className) {
        DependenciesSourceGenerator generator = new DependenciesSourceGenerator(writer, config);
        try {
            generator.generate(packageName, className);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void generate(String packageName, String className) throws IOException {
        writeLn("package " + packageName + ";");
        writeLn();
        addImport("org.gradle.api.artifacts.MinimalExternalModuleDependency");
        addImport("org.gradle.api.artifacts.ExternalModuleDependencyBundle");
        addImport("org.gradle.api.artifacts.MutableVersionConstraint");
        addImport("org.gradle.api.provider.Provider");
        addImport("org.gradle.api.provider.ProviderFactory");
        addImport("org.gradle.api.internal.std.AbstractExternalDependencyFactory");
        addImport("org.gradle.api.internal.std.AllDependenciesModel");
        addImport("java.util.Map");
        addImport("javax.inject.Inject");
        writeLn();
        writeLn("public class " + className + " extends AbstractExternalDependencyFactory {");
        writeLn();
        writeLn("    @Inject");
        writeLn("    public " + className + "(AllDependenciesModel config, ProviderFactory providers) {");
        writeLn("        super(config, providers);");
        writeLn("    }");
        writeLn();
        List<String> dependencies = config.getDependencyAliases();
        List<String> bundles = config.getBundleAliases();
        List<String> versions = config.getVersionAliases();
        performValidation(dependencies, bundles, versions);
        writeDependencyAccessors(dependencies);
        writeBundleAccessors(bundles);
        writeVersionAccessors(versions);
        writeLn("}");
    }

    private void writeBundleAccessors(List<String> bundles) throws IOException {
        for (String alias : bundles) {
            List<String> coordinates = config.getBundle(alias).stream()
                .map(config::getDependencyData)
                .map(this::coordinatesDescriptorFor)
                .collect(Collectors.toList());
            writeBundle(alias, coordinates);
        }
    }

    private void writeDependencyAccessors(List<String> dependencies) throws IOException {
        for (String alias : dependencies) {
            String coordinates = coordinatesDescriptorFor(config.getDependencyData(alias));
            writeAccessor(alias, coordinates);
        }
    }

    private void writeVersionAccessors(List<String> versions) throws IOException {
        for (String version : versions) {
            writeLn("    /**");
            writeLn("     * Returns the version associated to this alias: " + version);
            writeLn("     * If the version is a rich version and that its not expressable as a");
            writeLn("     * single version string, then an empty string is returned.");
            writeLn("     */");
            writeLn("    public String get" + toJavaName(version) + "Version() { return getVersion(\"" + version + "\"); }");
            writeLn();
        }
    }

    private static void performValidation(List<String> dependencies, List<String> bundles, List<String> versions) {
        assertDependencyAliases(dependencies);
        assertUnique(dependencies, "dependency aliases", "");
        assertUnique(bundles, "dependency bundles", "Bundle");
        assertUnique(versions, "dependency versions", "Version");
        int size = dependencies.size() + bundles.size() + versions.size();
        if (size > MAX_ENTRIES) {
            maybeThrowValidationError(ImmutableList.of("model contains too many entries (" + size + "), maximum is " + MAX_ENTRIES));
        }
    }

    private static void assertDependencyAliases(List<String> names) {
        List<String> errors = names.stream()
            .filter(n -> n.toLowerCase().endsWith("bundle") || n.toLowerCase().endsWith("version"))
            .map(n -> "alias " + n + " isn't a valid: it shouldn't end with 'Bundle' or 'Version'")
            .collect(Collectors.toList());
        maybeThrowValidationError(errors);
    }

    private static void assertUnique(List<String> names, String prefix, String suffix) {
        List<String> errors = names.stream()
            .collect(Collectors.groupingBy(AbstractSourceGenerator::toJavaName))
            .entrySet()
            .stream()
            .filter(e -> e.getValue().size() > 1)
            .map(e -> prefix + " " + e.getValue().stream().sorted().collect(Collectors.joining(" and ")) + " are mapped to the same accessor name get" + e.getKey() + suffix + "()")
            .collect(Collectors.toList());
        maybeThrowValidationError(errors);
    }

    private static void maybeThrowValidationError(List<String> errors) {
        if (errors.size() == 1) {
            throw new InvalidUserDataException("Cannot generate dependency accessors because " + errors.get(0));
        }
        if (errors.size() > 1) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Cannot generate dependency accessors because");
            formatter.startChildren();
            errors.stream()
                .sorted()
                .forEach(formatter::node);
            formatter.endChildren();
            throw new InvalidUserDataException(formatter.toString());
        }
    }

    private String coordinatesDescriptorFor(DependencyModel dependencyData) {
        return dependencyData.getGroup() + ":" + dependencyData.getName();
    }

    private void writeAccessor(String alias, String coordinates) throws IOException {
        writeLn("    /**");
        writeLn("     * Creates a dependency provider for " + alias + " (" + coordinates + ")");
        writeLn("     */");
        writeLn("    public Provider<MinimalExternalModuleDependency> get" + toJavaName(alias) + "() { return create(\"" + alias + "\"); }");
        writeLn();
    }

    private void writeBundle(String alias, List<String> coordinates) throws IOException {
        writeLn("    /**");
        writeLn("     * Creates a dependency bundle provider for " + alias + " which is an aggregate for the following dependencies:");
        writeLn("     * <ul>");
        for (String coordinate : coordinates) {
            writeLn("     *    <li>" + coordinate + "</li>");
        }
        writeLn("     * </ul>");
        writeLn("     */");
        writeLn("    public Provider<ExternalModuleDependencyBundle> get" + toJavaName(alias) + "Bundle() { return createBundle(\"" + alias + "\"); }");
        writeLn();
    }

}
