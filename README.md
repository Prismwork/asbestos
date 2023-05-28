# Asbestos

Quilt Loom extension providing some extra features centered around mappings.

### Setup

`settings.gradle`:

```groovy
pluginManagement {
    repositories {
        // ...
        maven { url 'https://maven.nova-committee.cn/releases' }
        maven { url 'https://maven.nova-committee.cn/snapshots' }
    }
}
```

`build.gradle`:

```groovy
plugins {
    id 'org.quiltmc.loom' version '<ANY-VERSION-HIGHER-THAN-1.1>' // Required
    id 'io.github.prismwork.asbestos' version '1.0.0' // Or 1.0-SNAPSHOT for latest versions
    // ...
}
```

### Usage

Retarget mappings based on a set of intermediary to another:

```groovy
dependencies {
	// ...
	mappings asbestos.newMappingsBuilder {
		it.base("cool.mappings:1.0.0:v2")
		it.sourceIntermediary("cool.intermediary:1.0.0:v2")
		it.targetIntermediary("magnificent.intermediary:1.0.0:v2")
	}.build()
    // ...
}
```
