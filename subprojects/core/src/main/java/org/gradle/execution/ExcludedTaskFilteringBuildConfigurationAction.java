/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.execution;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Pair;

import java.util.Set;

/**
 * A {@link BuildConfigurationAction} which filters excluded tasks.
 */
public class ExcludedTaskFilteringBuildConfigurationAction implements BuildConfigurationAction {
    private final TaskSelector taskSelector;

    public ExcludedTaskFilteringBuildConfigurationAction(TaskSelector taskSelector) {
        this.taskSelector = taskSelector;
    }

    @Override
    public void configure(BuildExecutionContext context) {
        GradleInternal gradle = context.getGradle();
        Set<String> excludedTaskNames = gradle.getStartParameter().getExcludedTaskNames();
        if (!excludedTaskNames.isEmpty()) {
            Multimap<GradleInternal, Spec<Task>> filters = MultimapBuilder.linkedHashKeys().hashSetValues().build();
            for (String taskName : excludedTaskNames) {
                Pair<GradleInternal, Spec<Task>> filter = taskSelector.getFilter(taskName);
                filters.put(filter.getLeft(), filter.getRight());
            }
            for (GradleInternal gradleInternal : filters.keySet()) {
                gradleInternal.getTaskGraph().useFilter(Specs.intersect(filters.get(gradleInternal)));
            }
        }

        context.proceed();
    }
}
