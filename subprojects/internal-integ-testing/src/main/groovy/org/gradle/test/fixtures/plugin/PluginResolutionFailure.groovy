/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.test.fixtures.plugin

import org.gradle.api.internal.artifacts.BaseRepositoryFactory
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.test.fixtures.Repository
import org.gradle.test.fixtures.ivy.IvyRepository
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.maven.MavenRepository
import org.hamcrest.Matcher

import static org.hamcrest.Matchers.allOf
import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.startsWith

class PluginResolutionFailure {

    final ExecutionFailure failure

    final String pluginId
    final String pluginVersion

    final String request
    final String group
    final String name
    final String version

    PluginResolutionFailure(ExecutionFailure failure, String pluginId, String pluginVersion, String artifact = null) {
        this.failure = failure
        this.pluginId = pluginId
        this.pluginVersion = pluginVersion
        if (artifact == null) {
            this.request = "[id: '$pluginId', version: '$pluginVersion']"
            this.group = pluginId
            this.name = "$pluginId${PluginBuilder.PLUGIN_MARKER_SUFFIX}"
            this.version = pluginVersion
        } else {
            this.request = "[id: '$pluginId', version: '$pluginVersion', artifact: '$artifact']"
            def coordinates = artifact.split(":")
            this.group = coordinates[0]
            this.name = coordinates[1]
            this.version = coordinates[2]
        }
    }

    void assertIsArtifactResolutionFailure(Repository... repositories = [DEFAULT_REPOSITORY]) {
        assertContainsArtifactResolutionFailureDescription()
        assertHasArtifactResolutionFailureCause(repositories)
    }

    void assertContainsArtifactResolutionFailureDescription() {
        failure.assertThatDescription(containsArtifactResolutionFailureDescription())
    }

    void assertHasArtifactResolutionFailureCause(Repository... repositories = [DEFAULT_REPOSITORY]) {
        failure.assertThatCause(isArtifactResolutionFailureCause(repositories))
    }

    private Matcher<String> containsArtifactResolutionFailureDescription() {
        allOf(
            startsWith("Plugin $request was not found in any of the following sources:"),
            containsString("- Plugins Repositories (could not resolve plugin artifacts)"))
    }

    private Matcher<String> isArtifactResolutionFailureCause(Repository... repositories) {
        equalTo(resolutionFailureCause(repositories))
    }

    private String resolutionFailureCause(Repository... repositories) {
        List<String> searchedLocations = repositories.collectMany { repository ->
            if (repository instanceof MavenRepository) {
                return searchedLocations(repository as MavenRepository)
            } else if (repository instanceof IvyRepository) {
                return searchedLocations(repository as IvyRepository)
            } else {
                throw new UnsupportedOperationException("Fixture does not support this repository: $repository")
            }
        }
        return resolutionFailureCauseFor(searchedLocations)
    }

    private String resolutionFailureCauseFor(List<String> searchedLocations) {
        return """
            Could not find $group:$name:$version.
            Searched in the following locations:
                ${searchedLocations.join("\n                ")}
            Required by:
                unspecified:unspecified:unspecified
        """.stripIndent().trim()
    }

    private List<String> searchedLocations(MavenRepository repository) {
        def baseUri = repoUri(repository)
        def groupPath = group.replace('.', '/')
        return [
            "$baseUri$groupPath/$name/$version/$name-${version}.pom",
            "$baseUri$groupPath/$name/$version/$name-${version}.jar"
        ]
    }

    private List<String> searchedLocations(IvyRepository repository) {
        def baseUri = repoUri(repository)
        return [
            "$baseUri$group/$name/$version/ivy-${version}.xml",
            "$baseUri$group/$name/$version/$name-${version}.jar"
        ]
    }

    private static String repoUri(Repository repository) {
        def normalizedUri = repository.uri.toString().replace("%20", " ")
        return normalizedUri.endsWith('/') ? normalizedUri : normalizedUri + '/'
    }

    private static final MavenRepository DEFAULT_REPOSITORY = new MavenRepository() {

        @Override
        URI getUri() {
            return URI.create(BaseRepositoryFactory.PLUGIN_PORTAL_DEFAULT_URL)
        }

        @Override
        MavenModule module(String groupId, String artifactId) {
            throw new UnsupportedOperationException()
        }

        @Override
        MavenModule module(String groupId, String artifactId, String version) {
            throw new UnsupportedOperationException()
        }
    }
}
