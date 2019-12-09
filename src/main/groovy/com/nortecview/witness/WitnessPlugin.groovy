package com.nortecview.witness

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

import java.security.MessageDigest

class WitnessPlugin implements Plugin<Project> {

    static String calculateSha256(File file) {
        MessageDigest md = MessageDigest.getInstance("SHA-256")
        file.eachByte 4096, { bytes, size ->
            md.update(bytes, 0, size)
        }
        return md.digest().collect { String.format "%02x", it }.join()
    }

    /**
     * Converts a file path into a DependencyKey, assuming the path ends with the elements
     * "group/name/version/sha1/file".
     * See https://docs.gradle.org/current/userguide/dependency_cache.html
     */
    static DependencyKey makeKey(String path) {
        def parts = path.tokenize(System.getProperty('file.separator'))
        if (parts.size() < 5) throw new AssertionError()
        parts = parts.subList(parts.size() - 5, parts.size())
        return new DependencyKey(parts[0], parts[1], parts[2], parts[4])
    }

    static Map<DependencyKey, String> calculateHashes(Project project) {
        def excludedProp = project.properties.get('noWitness')
        def excluded = excludedProp == null ? [] : excludedProp.split(',')
        def dependenciesMap = new TreeMap<DependencyKey, String>()

        def configurations = collectProjectConfigurations(project).findAll { Configuration configuration ->
            // Skip excluded configurations and their subconfigurations
            def scopedName = "${project.name}:${configuration.name}"
            configuration.hierarchy.each { Configuration superConfiguration ->
                def superScopedName = "${project.name}:${superConfiguration.name}"
                if (excluded.contains(superConfiguration.name) || excluded.contains(superScopedName)) {
                    println "Skipping excluded configuration ${scopedName}"
                    return false
                }
            }
            true
        }
        configurations.each { Configuration configuration ->
            filterConfigurationDependencies(project, configuration).each { key, hash ->
                dependenciesMap.put key, hash
            }
        }
        return dependenciesMap
    }

    static Map<String, ConfigurationInfo> findDependencies(Project project) {
        def dependenciesMap = new TreeMap<String, ConfigurationInfo>()

        collectProjectConfigurations(project).each { Configuration configuration ->
            def configDependencies = new ArrayList<String>()

            filterConfigurationDependencies(project, configuration).each { key, hash ->
                configDependencies.add("$key:$hash".toString())
            }
            Collections.sort configDependencies

            def superConfigurations = new ArrayList<>()
            configuration.hierarchy.each { Configuration superConfiguration ->
                if (superConfiguration.name != configuration.name) superConfigurations.add(superConfiguration.name)
            }
            def info = new ConfigurationInfo(superConfigurations, configDependencies)

            dependenciesMap.put "${project.name}:${configuration.name}".toString(), info
        }
        return dependenciesMap
    }

    static Set<Configuration> collectProjectConfigurations(Project project) {
        project.configurations + project.buildscript.configurations
    }

    static Map<DependencyKey, String> filterConfigurationDependencies(Project project, Configuration configuration) {
        def dependenciesMap = new TreeMap<DependencyKey, String>()
        def projectPath = project.file('.').canonicalPath

        // Skip unresolvable configurations
        if (configuration.metaClass.respondsTo(configuration, 'isCanBeResolved') ? configuration.isCanBeResolved() : true) {
            configuration.files.each { File file ->
                String depPath = file.canonicalPath
                // Skip files within project directory
                if (depPath.contains('build/lib')) {
                    // ">>>> Skipping ${project.name} local dependency ${file.name}"
                } else if (depPath.startsWith(projectPath)) {
                    // ">>>> Skipping ${project.name} subpath ${file.name}"
                } else if (file.exists() && file.isFile()) {
                    dependenciesMap.put makeKey(file.path), calculateSha256(file)
                }
            }
        }
        dependenciesMap
    }

    void apply(Project project) {
        project.extensions.create('dependencyVerification', WitnessPluginExtension)

        project.task('verifyDependencies', group: 'witness').doLast {
            println 'calculating hashes'
            def dependencies = calculateHashes project
            project.dependencyVerification.verify.each { assertion ->
                def parts = assertion.tokenize(":")
                if (parts.size() != 5) {
                    throw new InvalidUserDataException("Invalid or obsolete integrity assertion '${assertion}'")
                }
                def (group, name, version, file, expectedHash) = parts
                def key = new DependencyKey(group, name, version, file)
                println "Verifying ${key.all}"
                def hash = dependencies.get key
                if (hash == null) {
                    throw new InvalidUserDataException("No dependency for integrity assertion '${assertion}'")
                }
                if (hash != expectedHash) {
                    throw new InvalidUserDataException("Checksum failed for ${key.all}")
                }
            }
        }

        project.task('calculateChecksums', group: 'witness').doLast {
            def dependencies = calculateHashes project
            println "dependencyVerification {"
            println "    verify = ["
            dependencies.each { dep -> println "        '${dep.key.all}:${dep.value}'," }
            println "    ]"
            println "}"
        }

        project.task('printDependencies', group: 'witness').doLast {
            def dependencies = findDependencies project
            dependencies.each {
                println "${it.key}:"
                println "    superconfigurations:"
                it.value.superConfigurations.each { println "        ${it}" }
                println "    dependencies:"
                it.value.dependencies.each { println "        ${it}" }
            }
        }
    }

}

