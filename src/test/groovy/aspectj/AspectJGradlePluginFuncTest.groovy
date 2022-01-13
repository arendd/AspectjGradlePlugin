package aspectj

import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.lang.management.ManagementFactory

class AspectJGradlePluginFuncTest extends Specification {

    @Rule
    TemporaryFolder projectDir

    def buildFile

    void setup() {
        buildFile = projectDir.newFile("build.gradle")
    }

    def "can be applied via plugins block"() {
        given:
        buildFile << """
        plugins {
            id 'aspectj.AspectjGradlePlugin'
        }
        project.ext.aspectjVersion = '1.9.6'
        """

        expect:
        success("build")
    }

    def "fails the build if aspectjVersion is not set as project extension"() {
        given:
        buildFile << """
        plugins {
            id 'aspectj.AspectjGradlePlugin'
        }
        """

        expect:
        fail("build")
    }

    def success(String... args) {
        runner(args).build()
    }

    def fail(String... args) {
        runner(args).buildAndFail()
    }

    private runner(String... args) {
        GradleRunner.create()
                .forwardOutput()
                .withDebug(ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0)
                .withPluginClasspath()
                .withArguments([*args, "-s"])
                .withProjectDir(projectDir.root)
    }
}
