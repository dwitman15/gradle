package projects

import common.isLinuxBuild
import configurations.FunctionalTest
import configurations.SanityCheck
import configurations.buildReportTab
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.IdOwner
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.SpecificBuild
import model.Stage
import model.TestType

class StageProject(model: CIBuildModel, stage: Stage, rootProjectUuid: String) : Project({
    this.uuid = "${model.projectPrefix}Stage_${stage.stageName.uuid}"
    this.id = AbsoluteId("${model.projectPrefix}Stage_${stage.stageName.id}")
    this.name = stage.stageName.stageName
    this.description = stage.stageName.description
}) {
    val specificBuildTypes: List<BuildType>

//    val performanceTests: List<PerformanceTestsPass>

    val functionalTests: List<FunctionalTest>

    init {
        features {
            if (stage.specificBuilds.contains(SpecificBuild.SanityCheck)) {
                buildReportTab("API Compatibility Report", "report-architecture-test-binary-compatibility-report.html")
                buildReportTab("Incubating APIs Report", "incubation-reports/all-incubating.html")
            }
        }

        specificBuildTypes = stage.specificBuilds.map {
            it.create(model, stage)
        }
        specificBuildTypes.forEach(this::buildType)

//        val performanceTestsFromModel = stage.performanceTests.map { createPerformanceTests(model, performanceTestBucketProvider, stage, it) }
//
//        performanceTests = if (stage.stageName == StageNames.EXPERIMENTAL_PERFORMANCE) {
//            val coverage = PerformanceTestCoverage(14, PerformanceTestType.adHoc, Os.LINUX, numberOfBuckets = 1, withoutDependencies = true)
//            val performanceTests = Os.values().mapIndexed { index, os -> index to os }.flatMap { (index, os) ->
//                val osCoverage = PerformanceTestCoverage(14, PerformanceTestType.adHoc, os, numberOfBuckets = 1, withoutDependencies = true)
//                listOf("async-profiler", "async-profiler-alloc").mapIndexed { profIndex, profiler ->
//                    createProfiledPerformanceTest(
//                        model,
//                        stage,
//                        osCoverage,
//                        testProject = "santaTrackerAndroidBuild",
//                        scenario = Scenario("org.gradle.performance.regression.corefeature.FileSystemWatchingPerformanceTest", "assemble for non-abi change with file system watching"),
//                        profiler = profiler,
//                        bucketIndex = 2 * index + profIndex
//                    )
//                }
//            }
//            val performanceTestProject = ManuallySplitPerformanceTestProject(model, coverage, performanceTests)
//            subProject(performanceTestProject)
//            performanceTestsFromModel + listOf(PerformanceTestsPass(model, performanceTestProject).also(this::buildType))
//        } else {
//            performanceTestsFromModel
//        }

        val (topLevelCoverage, allCoverage) = stage.functionalTests.partition { it.testType == TestType.soak || it.testDistribution }
        val topLevelFunctionalTests = topLevelCoverage
            .map { FunctionalTest(model, it.asConfigurationId(model), it.asName(), it.asName(), it, stage = stage) }

        val coverageFunctionalTests = allCoverage
            .map { testCoverage ->
                val coverageFunctionalTest = FunctionalTest(
                    model,
                    testCoverage.asId(model),
                    testCoverage.asName(),
                    testCoverage.asName(),
                    testCoverage,
                    stage)

                if (stage.functionalTestsDependOnSpecificBuilds) {
                    specificBuildTypes.forEach(coverageFunctionalTest::dependsOn)
                }
                if (!(stage.functionalTestsDependOnSpecificBuilds && stage.specificBuilds.contains(SpecificBuild.SanityCheck)) && stage.dependsOnSanityCheck) {
                    coverageFunctionalTest.dependsOn(AbsoluteId(SanityCheck.buildTypeId(model)))
                }
                coverageFunctionalTest
            }

        functionalTests = topLevelFunctionalTests + coverageFunctionalTests
        functionalTests.forEach(this::buildType)
    }
}

private fun FunctionalTest.dependsOn(dependency: IdOwner) {
    if (this.isLinuxBuild()) {
        dependencies {
            dependency(dependency) {
                snapshot {
                    onDependencyFailure = FailureAction.CANCEL
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
        }
    }
}
