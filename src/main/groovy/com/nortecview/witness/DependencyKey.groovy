package com.nortecview.witness

class DependencyKey implements Comparable<DependencyKey> {

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

    @Override
    String toString() {
        return "${group}:${name}:${version}"
    }
}