/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.plugin.use.resolve.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.plugin.management.internal.InvalidPluginRequestException;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nonnull;

import static com.google.common.base.Strings.isNullOrEmpty;

public class ArtifactRepositoriesPluginResolver implements PluginResolver {

    public static final String PLUGIN_MARKER_SUFFIX = ".gradle.plugin";

    @VisibleForTesting
    static final String SOURCE_NAME = "Plugins Repositories";

    public static ArtifactRepositoriesPluginResolver createWithDefaults(DependencyResolutionServices dependencyResolutionServices, VersionSelectorScheme versionSelectorScheme) {
        RepositoryHandler repositories = dependencyResolutionServices.getResolveRepositoryHandler();
        if (repositories.isEmpty()) {
            repositories.gradlePluginPortal();
        }
        return new ArtifactRepositoriesPluginResolver(dependencyResolutionServices, versionSelectorScheme);
    }

    private final DependencyResolutionServices resolution;
    private final VersionSelectorScheme versionSelectorScheme;

    @VisibleForTesting
    ArtifactRepositoriesPluginResolver(DependencyResolutionServices dependencyResolutionServices, VersionSelectorScheme versionSelectorScheme) {
        this.resolution = dependencyResolutionServices;
        this.versionSelectorScheme = versionSelectorScheme;
    }

    @Override
    public void resolve(PluginRequestInternal pluginRequest, PluginResolutionResult result) throws InvalidPluginRequestException {
        ModuleDependency markerDependency = getMarkerDependency(pluginRequest);
        String markerVersion = markerDependency.getVersion();
        if (isNullOrEmpty(markerVersion)) {
            handleNotFound(result, "plugin dependency must include a version number for this source");
            return;
        }

        if (markerVersion.endsWith("-SNAPSHOT")) {
            handleNotFound(result, "snapshot plugin versions are not supported");
            return;
        }

        if (versionSelectorScheme.parseSelector(markerVersion).isDynamic()) {
            handleNotFound(result, "dynamic plugin versions are not supported");
            return;
        }

        ResolvedConfiguration resolved = resolvedConfigurationFor(markerDependency);
        if (resolved.hasError()) {
            handleNotFound(result, markerDependency, resolved);
        } else {
            handleFound(pluginRequest, markerDependency, result);
        }
    }

    private void handleNotFound(PluginResolutionResult result, String message) {
        result.notFound(SOURCE_NAME, message);
    }

    private void handleNotFound(PluginResolutionResult result, ModuleDependency markerDependency, ResolvedConfiguration resolved) {
        result.notFound(SOURCE_NAME, "could not resolve plugin artifacts", new ResolutionFailureProvider(markerDependency, resolved));
    }

    private void handleFound(PluginRequestInternal pluginRequest, Dependency markerDependency, PluginResolutionResult result) {
        result.found(SOURCE_NAME, new ArtifactPluginResolution(pluginRequest.getId(), markerDependency));
    }

    private ResolvedConfiguration resolvedConfigurationFor(ModuleDependency dependency) {
        ConfigurationContainer configurations = resolution.getConfigurationContainer();
        Configuration configuration = configurations.detachedConfiguration(dependency);
        configuration.setTransitive(false);
        return configuration.getResolvedConfiguration();
    }

    private ModuleDependency getMarkerDependency(PluginRequestInternal pluginRequest) {
        ModuleVersionSelector selector = pluginRequest.getModule();
        if (selector == null) {
            String id = pluginRequest.getId().getId();
            return new DefaultExternalModuleDependency(id, id + PLUGIN_MARKER_SUFFIX, pluginRequest.getVersion());
        } else {
            return new DefaultExternalModuleDependency(selector.getGroup(), selector.getName(), selector.getVersionConstraint().getPreferredVersion());
        }
    }

    private static class ArtifactPluginResolution implements PluginResolution {

        private final PluginId pluginId;
        private final Dependency markerDependency;

        private ArtifactPluginResolution(PluginId pluginId, Dependency markerDependency) {
            this.pluginId = pluginId;
            this.markerDependency = markerDependency;
        }

        @Override
        public PluginId getPluginId() {
            return pluginId;
        }

        @Override
        public void execute(@Nonnull PluginResolveContext context) {
            context.addLegacy(pluginId, markerDependency);
        }
    }

    private static class ResolutionFailureProvider implements PluginResolutionResult.FailureProvider {

        private final ModuleDependency markerDependency;
        private final ResolvedConfiguration resolved;

        private ResolutionFailureProvider(ModuleDependency markerDependency, ResolvedConfiguration resolved) {
            this.markerDependency = markerDependency;
            this.resolved = resolved;
        }

        @Override
        public Throwable getFailure() {
            try {
                resolved.rethrowFailure();
                throw new IllegalStateException("Failed resolved configuration didn't throw, this should never happen");
            } catch (ResolveException ex) {
                return new DefaultMultiCauseException(getNotation(markerDependency), ex.getCauses());
            }
        }

        private String getNotation(Dependency dependency) {
            return Joiner.on(':').join(dependency.getGroup(), dependency.getName(), dependency.getVersion());
        }
    }
}
