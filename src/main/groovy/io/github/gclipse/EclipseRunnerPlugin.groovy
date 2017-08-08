package io.github.gclipse

import io.github.gclipse.testing.EclipseTestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.file.FileResolver

import javax.inject.Inject

class EclipseRunnerPlugin implements Plugin<Project> {

    static String DSL_EXTENSION_NAME = "eclipseRun"

    static final TASK_NAME_RUN_ECLIPSE_IDE = 'eclipseRun'

    public final FileResolver fileResolver

    @Inject
    EclipseRunnerPlugin(FileResolver fileResolver) {
        this.fileResolver = fileResolver
    }

    @Override
    void apply(Project project) {
        configureProject(project)
        addTaskRunEclipseIDE(project)
    }

    static void configureProject(Project project) {
        project.extensions.create(DSL_EXTENSION_NAME, EclipseTestExtension)
        project.getPlugins().apply(BundlePlugin)

        // append the sources of each first-level dependency and its transitive dependencies of
        // the 'bundled' configuration to the 'bundledSource' configuration
        project.afterEvaluate {
            project.configurations.bundled.resolvedConfiguration.firstLevelModuleDependencies.each { dep ->
                addSourcesRecursively(project, dep)
            }
        }
    }

    private static addSourcesRecursively(project, dep) {
        project.dependencies {
            bundledSource group: dep.moduleGroup, name: dep.moduleName, version: dep.moduleVersion, classifier: 'sources'
        }
        dep.children.each { childDep -> addSourcesRecursively(project, childDep) }
    }

    static void addTaskRunEclipseIDE(Project project) {
        project.task(TASK_NAME_RUN_ECLIPSE_IDE, type: RunEclipseTask) {
            group = Constants.gradleTaskGroupName
            description = "Run Eclipse instance"
        }
    }
}
