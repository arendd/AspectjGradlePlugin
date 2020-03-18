package aspectj

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction

/**
 * This Plugin is a fork of the outdated aspectj.gradle plugin for Gradle 5+
 *
 * @author arendd
 * 
 * Based on the source code of:
 * @author Luke Taylor
 * @author Mike Noordermeer
 * 
 * Fixed with the help of 
 * @author lambovg
 * 
 */
class AspectjGradlePlugin implements Plugin<Project> {

	void apply(Project project) {
		project.plugins.apply(JavaPlugin)

		if (!project.hasProperty('aspectjVersion')) {
			throw new GradleException("You must set the property 'aspectjVersion' before applying the aspectj plugin")
		}

		if (project.configurations.findByName('ajtools') == null) {
			project.configurations.create('ajtools')
			project.dependencies {
				ajtools "org.aspectj:aspectjtools:${project.aspectjVersion}"
				compile "org.aspectj:aspectjrt:${project.aspectjVersion}"
			}
		}

		for (projectSourceSet in project.sourceSets) {
			def namingConventions = projectSourceSet.name.equals('main') ? new MainNamingConventions() : new DefaultNamingConventions();
			for (configuration in [
				namingConventions.getAspectPathConfigurationName(projectSourceSet),
				namingConventions.getAspectInpathConfigurationName(projectSourceSet)
			]) {
				if (project.configurations.findByName(configuration) == null) {
					project.configurations.create(configuration)
				}
			}

			if (!projectSourceSet.allJava.isEmpty()) {
				def aspectTaskName = namingConventions.getAspectCompileTaskName(projectSourceSet)
				def javaTaskName = namingConventions.getJavaCompileTaskName(projectSourceSet)

				project.tasks.create(name: aspectTaskName, description: "Compiles AspectJ Source for ${projectSourceSet.name} source set", type: Ajc) {
					sourceSet = projectSourceSet
					inputs.files(sourceSet.allJava)
					//replaced by arendd
					//outputs.dir(sourceSet.output.classesDirs)
					outputs.dir(sourceSet.java.outputDir)
					aspectpath = project.configurations.findByName(namingConventions.getAspectPathConfigurationName(projectSourceSet))
					ajInpath = project.configurations.findByName(namingConventions.getAspectInpathConfigurationName(projectSourceSet))
				}

				project.tasks[aspectTaskName].setDependsOn(project.tasks[javaTaskName].dependsOn)
				project.tasks[aspectTaskName].dependsOn(project.tasks[aspectTaskName].aspectpath)
				project.tasks[aspectTaskName].dependsOn(project.tasks[aspectTaskName].ajInpath)
				//replaced by arendd
				//project.tasks[javaTaskName].deleteAllActions()
				project.tasks[javaTaskName].getActions().clear()
				project.tasks[javaTaskName].dependsOn(project.tasks[aspectTaskName])
			}
		}
	}

	private static class MainNamingConventions implements NamingConventions {

		@Override
		String getJavaCompileTaskName(final SourceSet sourceSet) {
			return "compileJava"
		}

		@Override
		String getAspectCompileTaskName(final SourceSet sourceSet) {
			return "compileAspect"
		}

		@Override
		String getAspectPathConfigurationName(final SourceSet sourceSet) {
			return "aspectpath"
		}

		@Override
		String getAspectInpathConfigurationName(final SourceSet sourceSet) {
			return "ajInpath"
		}
	}

	private static class DefaultNamingConventions implements NamingConventions {

		@Override
		String getJavaCompileTaskName(final SourceSet sourceSet) {
			return "compile${sourceSet.name.capitalize()}Java"
		}

		@Override
		String getAspectCompileTaskName(final SourceSet sourceSet) {
			return "compile${sourceSet.name.capitalize()}Aspect"
		}

		@Override
		String getAspectPathConfigurationName(final SourceSet sourceSet) {
			return "${sourceSet.name}Aspectpath"
		}

		@Override
		String getAspectInpathConfigurationName(final SourceSet sourceSet) {
			return "${sourceSet.name}AjInpath"
		}
	}
}

class Ajc extends DefaultTask {

	SourceSet sourceSet

	FileCollection aspectpath
	FileCollection ajInpath

	// ignore or warning
	String xlint = 'ignore'

	String maxmem
	Map<String, String> additionalAjcArgs

	Ajc() {
		logging.captureStandardOutput(LogLevel.INFO)
	}

	@TaskAction
	def compile() {
		logger.info("=" * 30)
		logger.info("=" * 30)
		logger.info("Running ajc ...")
		logger.info("classpath: ${sourceSet.compileClasspath.asPath}")
		logger.info("srcDirs $sourceSet.java.srcDirs")

		def iajcArgs = [classpath           : sourceSet.compileClasspath.asPath,
		    //replaced by arendd
			//destDir             : sourceSet.output.classesDirs.absolutePath,
		      destDir      		  : sourceSet.java.outputDir,
		    //replaced by arendd
			//s                   : sourceSet.output.classesDirs.absolutePath,
		      s                   : sourceSet.java.outputDir,
		    source              : project.convention.plugins.java.sourceCompatibility,
			target              : project.convention.plugins.java.targetCompatibility,
			//replaced by arendd
			//inpath              : sourceSet.output.classesDirs.absolutePath,
			inpath       	      : sourceSet.java.outputDir,
			xlint               : xlint,
			fork                : 'false',
			aspectPath          : aspectpath.asPath,
			showWeaveInfo       : 'true']

		if (null != maxmem) {
			iajcArgs['maxmem'] = maxmem
		}

		if (null != additionalAjcArgs) {
			for (pair in additionalAjcArgs) {
				iajcArgs[pair.key] = pair.value
			}
		}

		ant.taskdef(resource: "org/aspectj/tools/ant/taskdefs/aspectjTaskdefs.properties", classpath: project.configurations.ajtools.asPath)
		ant.iajc(iajcArgs) {
			sourceRoots {
				sourceSet.java.srcDirs.each {
					logger.info("   sourceRoot $it")
					pathelement(location: it.absolutePath)
				}
			}
		}
	}
}
