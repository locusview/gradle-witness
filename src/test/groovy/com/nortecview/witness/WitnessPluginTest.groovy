package com.nortecview.witness

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.*

class WitnessPluginTest {

    Project project

    @Before
    void init() {
        project = ProjectBuilder.builder().build()
        project.getPluginManager().apply(WitnessPlugin.class)
    }

    @Test
    void witnessTasks() {
        ['verifyDependencies', 'calculateChecksums', 'printDependencies'].each { expectedTaskName ->
            def expectedTask = project.getTasks().getByName(expectedTaskName)
            assertNotNull("expected project task $expectedTaskName", expectedTask)
            assertEquals('witness', expectedTask.group)
        }
    }

    @Test
    void witnessExtensions() {
        assertNotNull(project.getExtensions().getByName('dependencyVerification'))
    }
}
