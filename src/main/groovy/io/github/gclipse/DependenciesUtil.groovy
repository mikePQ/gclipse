package io.github.gclipse

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency


class DependenciesUtil {
    static void copyTargetPlatformToBuildFolder(Project project, Config config, File distro) {
        project.copy {
            from config.nonMavenizedTargetPlatformDir
            into distro
        }
    }

    static void publishDependenciesIntoTemporaryRepo(Project project, Config config, File additionalPluginsDir) {
        // take all direct dependencies and and publish their jar archive to the build folder
        // (eclipsetest/additions subfolder) as a mini P2 update site
        for (ProjectDependency dep : project.configurations.compile.dependencies.withType(ProjectDependency)) {
            Project p = dep.dependencyProject
            project.logger.debug("Publish '${p.tasks.jar.outputs.files.singleFile.absolutePath}' to '${additionalPluginsDir.path}/${p.name}'")
            project.exec {
                commandLine(config.eclipseSdkExe,
                        "-application", "org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher",
                        "-metadataRepository", "file:${additionalPluginsDir.path}/${p.name}",
                        "-artifactRepository", "file:${additionalPluginsDir.path}/${p.name}",
                        "-bundles", p.tasks.jar.outputs.files.singleFile.path,
                        "-publishArtifacts",
                        "-nosplash",
                        "-consoleLog")
            }
        }

        // and do the same with the current plugin
        project.logger.debug("Publish '${project.jar.outputs.files.singleFile.absolutePath}' to '${additionalPluginsDir.path}/${project.name}'")
        project.exec {
            commandLine(config.eclipseSdkExe,
                    "-application", "org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher",
                    "-metadataRepository", "file:${additionalPluginsDir.path}/${project.name}",
                    "-artifactRepository", "file:${additionalPluginsDir.path}/${project.name}",
                    "-bundles", project.jar.outputs.files.singleFile.path,
                    "-publishArtifacts",
                    "-nosplash",
                    "-consoleLog")
        }
    }

    static void installDepedenciesIntoTargetPlatform(Project project, Config config, File additionalPluginsDir, File testDistributionDir) {
        // take the mini P2 update sites from the build folder and install it into the test Eclipse distribution
        for (ProjectDependency dep : project.configurations.compile.dependencies.withType(ProjectDependency)) {
            Project p = dep.dependencyProject
            project.logger.debug("Install '${additionalPluginsDir.path}/${p.name}' into '${testDistributionDir.absolutePath}'")
            project.exec {
                commandLine(config.eclipseSdkExe,
                        '-application', 'org.eclipse.equinox.p2.director',
                        '-repository', "file:${additionalPluginsDir.path}/${p.name}",
                        '-installIU', p.name,
                        '-destination', testDistributionDir,
                        '-profile', 'SDKProfile',
                        '-p2.os', Constants.os,
                        '-p2.ws', Constants.ws,
                        '-p2.arch', Constants.arch,
                        '-roaming',
                        '-nosplash',
                        '-consoleLog')
            }
        }

        // do the same with the current project
        project.logger.debug("Install '${additionalPluginsDir.path}/${project.name}' into '${testDistributionDir.absolutePath}'")
        project.exec {
            commandLine(config.eclipseSdkExe,
                    '-application', 'org.eclipse.equinox.p2.director',
                    '-repository', "file:${additionalPluginsDir.path}/${project.name}",
                    '-installIU', project.name,
                    '-destination', testDistributionDir,
                    '-profile', 'SDKProfile',
                    '-p2.os', Constants.os,
                    '-p2.ws', Constants.ws,
                    '-p2.arch', Constants.arch,
                    '-roaming',
                    '-nosplash')
        }
    }
}
