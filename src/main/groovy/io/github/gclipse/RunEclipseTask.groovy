package io.github.gclipse

import io.github.gclipse.testing.EclipseTestExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.TaskAction
import org.gradle.process.internal.DefaultJavaExecAction
import org.gradle.process.internal.JavaExecAction


class RunEclipseTask {
    private static final Logger LOGGER = Logging.getLogger(RunEclipseTask.class)

    @TaskAction
    public void runEclipseIDE() {
        def testDistributionDir = project.file("$project.buildDir/eclipseRun/eclipse")
        def additionalPluginsDir = project.file("$project.buildDir/eclipseRun/additions")

        Project project = getProject()
        runEclipseInstance(project, Config.on(project), testDistributionDir, additionalPluginsDir)
    }


    private void runEclipseInstance(Project project, Config config, File targetPlatformDir, File additionalPluginsDir) {

        project.logger.info("Delete '${additionalPluginsDir.absolutePath}'")
        additionalPluginsDir.deleteDir()

        if (!targetPlatformDir.exists()) {
            // copy the target platform to the test distribution folder
            project.logger.info("Copy target platform from '${config.nonMavenizedTargetPlatformDir.absolutePath}' into the build folder '${targetPlatformDir.absolutePath}'")
            TestBundlePlugin.copyTargetPlatformToBuildFolder(project, config, targetPlatformDir)
        }

        // publish the dependencies' output jars into a P2 repository in the additions folder
        project.logger.info("Create mini-update site from the test plug-in and its dependencies at '${additionalPluginsDir.absolutePath}'")
        TestBundlePlugin.publishDependenciesIntoTemporaryRepo(project, config, additionalPluginsDir)

        // install all elements from the P2 repository into the test Eclipse distribution
        project.logger.info("Install the test plug-in and its dependencies from '${additionalPluginsDir.absolutePath}' into '${targetPlatformDir.absolutePath}'")
        TestBundlePlugin.installDepedenciesIntoTargetPlatform(project, config, additionalPluginsDir, targetPlatformDir)

        runInJdk()
    }

    private void runInJdk() {
        File runDir = new File(getProject().getBuildDir(), getName())

        File buildDir = (File) getProject().property("buildDir")
        String path = buildDir.getAbsolutePath() + "/eclipseRun/eclipse"

        File runtimeEclipseDir = new File(path)

        File configIniFile = new File(runtimeEclipseDir, "configuration/config.ini")
        assert configIniFile.exists()

        File runPluginsDir = new File(runtimeEclipseDir, "plugins")
        LOGGER.info("Eclipse runtime directory is {}", runPluginsDir.getPath())

        File equinoxLauncherFile = getEquinoxLauncherFile(runtimeEclipseDir)
        LOGGER.info("equinox launcher file {}", equinoxLauncherFile)

        List<File> classpathEntries = collectClasspathEntries(getProject())
        classpathEntries.add(equinoxLauncherFile)

        final JavaExecAction javaExecHandleBuilder = new DefaultJavaExecAction(getFileResolver(this))
        javaExecHandleBuilder.setClasspath(this.project.files(classpathEntries))
        javaExecHandleBuilder.setMain("org.eclipse.equinox.launcher.Main")

        String javaHome = getExtension(this).getTestEclipseJavaHome()
        File executable = new File(javaHome, "bin/java")
        if (executable.exists()) {
            javaExecHandleBuilder.setExecutable(executable)
        } else {
            LOGGER.warn("Java executable doesn't exist: " + executable.getAbsolutePath())
        }

        List<String> programArgs = new ArrayList<String>()

        programArgs.add("-os")
        programArgs.add(Constants.getOs())
        programArgs.add("-ws")
        programArgs.add(Constants.getWs())
        programArgs.add("-arch")
        programArgs.add(Constants.getArch())

        if (getExtension(this).isConsoleLog()) {
            programArgs.add("-consoleLog")
        }

        File optionsFile = getExtension(this).getOptionsFile()
        if (optionsFile != null) {
            programArgs.add("-debug")
            programArgs.add(optionsFile.getAbsolutePath())
        }

        programArgs.add("-product org.eclipse.platform.ide")

        // alternatively can use URI for -data and -configuration (file:///path/to/dir/)
        programArgs.add("-data")
        programArgs.add(runDir.getAbsolutePath() + File.separator + "workspace")
        programArgs.add("-configuration")
        programArgs.add(configIniFile.getParentFile().getAbsolutePath())

        javaExecHandleBuilder.setArgs(programArgs)

        List<String> jvmArgs = new ArrayList<>()
        if (getExtension(this).isDebug()) {
            jvmArgs.add("-Xdebug")
            jvmArgs.add("-Xrunjdwp:transport=dt_socket,address=8998,server=y")
        }

        javaExecHandleBuilder.setJvmArgs(jvmArgs)
        javaExecHandleBuilder.setWorkingDir(this.project.getBuildDir())

        javaExecHandleBuilder.execute()
    }

    private static List<File> collectClasspathEntries(Project project) {
        File buildDir = (File) project.property("buildDir")
        String path = buildDir.getAbsolutePath() + "/eclipseRun"
        File runtimeDir = new File(path)
        File[] additions = new File(runtimeDir, "additions").listFiles()

        List<File> result = new ArrayList<>()

        for (File addition : additions) {
            File[] plugins = new File(addition, "plugins").listFiles()
            result.addAll(plugins)
        }

        return result
    }

    private static File getEquinoxLauncherFile(File testEclipseDir) {
        File[] plugins = new File(testEclipseDir, "plugins").listFiles()
        for (File plugin : plugins) {
            if (plugin.getName().startsWith("org.eclipse.equinox.launcher_")) {
                return plugin
            }
        }
        return null
    }

    private static FileResolver getFileResolver(Task task) {
        return task.getProject().getPlugins().findPlugin(EclipseRunnerPlugin.class).fileResolver
    }

    private static EclipseTestExtension getExtension(Task task) {
        return (EclipseTestExtension) task.getProject().getExtensions().findByName("eclipseRun")
    }
}
