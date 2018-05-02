package org.whispersystems.witness

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.security.MessageDigest

class WitnessPluginExtension {
    List verify
}

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
        def projectPath = project.file('.').canonicalPath
        def dependencies = new TreeMap<DependencyKey, String>()
        def addDependencies = {
            // Skip unresolvable configurations
            if (it.metaClass.respondsTo(it, 'isCanBeResolved') ? it.isCanBeResolved() : true) {
                it.fileCollection { dep ->
                    // Skip dependencies on other projects
                    dep.version != 'unspecified'
                }.each {
                    // Skip files within project directory
                    if (!it.canonicalPath.startsWith(projectPath)) {
                        def key = makeKey(it.path)
                        if (!dependencies.containsKey(key))
                            dependencies.put key, calculateSha256(it)
                    }
                }
            }
        }
        project.configurations.each addDependencies
        project.buildscript.configurations.each addDependencies
        return dependencies
    }

    void apply(Project project) {
        project.extensions.create("dependencyVerification", WitnessPluginExtension)
        project.afterEvaluate {
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

        project.task('calculateChecksums').doLast {
            def dependencies = calculateHashes project
            println "dependencyVerification {"
            println "    verify = ["
            dependencies.each { dep -> println "        '${dep.key.all}:${dep.value}'," }
            println "    ]"
            println "}"
        }
    }

    static class DependencyKey implements Comparable<DependencyKey> {

        final String group, name, version, file, all

        DependencyKey(group, name, version, file) {
            this.group = group
            this.name = name
            this.version = version
            this.file = file
            all = "${group}:${name}:${version}:${file}".toString()
        }

        @Override
        boolean equals(Object o) {
            if (o instanceof DependencyKey) return ((DependencyKey) o).all == all
            return false
        }

        @Override
        int hashCode() {
            return all.hashCode()
        }

        @Override
        int compareTo(DependencyKey k) {
            return all <=> k.all
        }
    }
}

