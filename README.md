# sbt-buf

Simple SBT plugin providing wrappers around a subset of Buf operations

## Dependencies

### Buf

Has an unmanaged dependency on [Buf](https://docs.buf.build/installation) being installed on the build system.  It uses only the `buf` binary, which it expects to find on `$PATH`

This version of the plugin was tested and validated against Buf version 1.1.0:

### sbt-protoc

This plugin depends on functionality provided by the [sbt-protoc](https://github.com/thesamet/sbt-protoc) plugin, specifically for it's features around managing protobuf sources, dependencies, generation tasks, and plugins.  

## Usage

Include in `project/plugins.sbt`:

```
addSbtPlugin("com.yoppworks", "sbt-buf", "0.1.0")
```

## How it works

Buf requires that proto files be organized into Buf primitives of modules and workspaces to construct an image.  Images are the primary input for any other Buf operation, such as breaking change detection.  Modules and workspaces are both defined
using simple YAML configuration files at the root of their respective directories.  Buf requires all imports/dependencies of a protofile
to be present to build an image.  This plugin works by way of creating 'virtual' Buf modules and workspace based on the configuration of ScalaPB, namely the proto sources and dependencies confnigured on the sbt project.

For this reason, it's not straightforward to build a Buf image of an arbitrary commit in history on the fly, as the native Buf tool supports.  This would rely on an assumption that the commit in git history met all the same requirements.

#### Publishes image artifact

By default this plugin adds a Bug image artifact to the build.  This has the effect of building a Buf image from the configuration managed by sbt-protoc plugin, and publishing it as a binary artifact to the artifact repository configured for the project.  This will be initiated by a standard `publish` or `publishLocal` task.

#### Breaking change detection against published artifact

Given that building an image on the fly is not straightforward, the breaking change detection can currently only
be run against a previously published Bug image for this project.  The breaking change detection task requires a parameter of the version that the current working directory should be compared against.

## Tasks



### Breaking change detection

### Linting



