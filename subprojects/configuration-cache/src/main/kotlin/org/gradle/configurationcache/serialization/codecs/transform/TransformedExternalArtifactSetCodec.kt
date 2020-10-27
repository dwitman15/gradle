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

package org.gradle.configurationcache.serialization.codecs.transform

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.PreResolvedResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.transform.BoundTransformationStep
import org.gradle.api.internal.artifacts.transform.TransformedExternalArtifactSet
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.decodePreservingSharedIdentity
import org.gradle.configurationcache.serialization.encodePreservingSharedIdentityOf
import org.gradle.configurationcache.serialization.readList
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.internal.Describables
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import java.io.File


class TransformedExternalArtifactSetCodec : Codec<TransformedExternalArtifactSet> {
    override suspend fun WriteContext.encode(value: TransformedExternalArtifactSet) {
        encodePreservingSharedIdentityOf(value) {
            write(value.ownerId)
            write(value.targetVariantAttributes)
            val files = mutableListOf<File>()
            value.visitArtifacts { files.add(file) }
            write(files)
            val steps = unpackTransformationSteps(value.steps)
            writeCollection(steps)
        }
    }

    override suspend fun ReadContext.decode(): TransformedExternalArtifactSet {
        return decodePreservingSharedIdentity {
            val ownerId = readNonNull<ComponentIdentifier>()
            val targetAttributes = readNonNull<ImmutableAttributes>()
            val files = readNonNull<List<File>>()
            val steps: List<TransformStepSpec> = readList().uncheckedCast()
            TransformedExternalArtifactSet(ownerId, FixedFilesArtifactSet(ownerId, files), targetAttributes, ImmutableList.copyOf(steps.map { BoundTransformationStep(it.transformation, it.recreate()) }))
        }
    }
}


private
class FixedFilesArtifactSet(private val ownerId: ComponentIdentifier, private val files: List<File>) : ResolvedArtifactSet {
    override fun visitDependencies(context: TaskDependencyResolveContext) {
        throw UnsupportedOperationException("should not be called")
    }

    override fun visit(actions: BuildOperationQueue<RunnableBuildOperation>, visitor: ResolvedArtifactSet.Visitor) {
        val artifacts = artifacts
        visitor.visitArtifacts { artifactVisitor ->
            val displayName = Describables.of(ownerId)
            for (artifact in artifacts) {
                artifactVisitor.visitArtifact(displayName, ImmutableAttributes.EMPTY, artifact)
            }
        }
    }

    override fun visitLocalArtifacts(visitor: ResolvedArtifactSet.LocalArtifactVisitor) {
        throw UnsupportedOperationException("should not be called")
    }

    override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact>) {
        for (artifact in artifacts) {
            visitor.execute(artifact)
        }
    }

    private
    val artifacts: List<ResolvableArtifact>
        get() = files.map { file -> PreResolvedResolvableArtifact(null, DefaultIvyArtifactName.forFile(file, null), ComponentFileArtifactIdentifier(ownerId, file.name), file, TaskDependencyContainer.EMPTY) }
}
