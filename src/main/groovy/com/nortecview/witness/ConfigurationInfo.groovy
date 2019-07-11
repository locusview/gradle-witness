package com.nortecview.witness

class ConfigurationInfo {

    final List<String> superConfigurations
    final List<String> dependencies

    ConfigurationInfo(List<String> superConfigurations, List<String> dependencies) {
        this.superConfigurations = superConfigurations
        this.dependencies = dependencies
    }
}