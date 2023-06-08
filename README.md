# sbt-buf

Simple SBT plugin providing wrappers around a subset of [Buf](https://docs.buf.build) operations.

## Dependencies

### Buf

Has a managed dependency on [Buf](https://docs.buf.build/installation), which is resolved and downloaded from their [releases page](https://github.com/bufbuild/buf/releases) based on the type of system detected.

This version of the plugin was tested and validated against [Buf v1.15.1](https://github.com/bufbuild/buf/releases/tag/v1.15.1).

### sbt-protoc

This plugin depends on functionality provided by the [sbt-protoc](https://github.com/thesamet/sbt-protoc) plugin, specifically for it's features around managing protobuf sources, dependencies, generation tasks, and plugins.  

## Usage

Include in `project/plugins.sbt`:

```
addSbtPlugin("com.yoppworks.sbt", "sbt-buf", "0.8.0")
```

## Tasks

- `bufCompatCheck` - Breaking change detection task.  As described in How it Works, accepts as an argument a version of the artifact to which compare the working directory against.    
  - Eg. ```$> sbt "bufCompatCheck 1.2.1"```
- `bufLint` - Currently only supports running over the current working directory.  Runs only on sources from this project, ignores import/external proto sources.

## Configuration

- `Buf.imageArtifact: Boolean` - Controls the default behaviour of automatically adding the Buf artifact generation and publishing to the build.  Set to `false` to disable this default behaviour.
- `Buf.artifactDefinition: Artifact` - Defines the artifact characteristics of the Buf artifact.  Is not really meant for manipulation by clients.
- `Buf.imageDir: File` - Target directory in which Buf working directory image is generated.  Defaults to `target/buf`.
- `Buf.imageExt: ImageExtension` - Extension format to use for generated Buf images.  Defaults to Binary.  Beware that this is a global setting and controls not only image generation, but artifact resolution.  Be careful when changing this configuration:  if you have previously generated/published artifacts of a different extension type, changing this will have the effect of not being able to resolve that same artifact using the new/different extension value.
- `Buf.againstImageDir: File` - Target directory in which Buf against target image for breaking change detection is downloaded to.  Defaults to `target/buf-against`.
- `Buf.breakingCategory: Seq[BreakingUse]` - Category to use for configuring breaking change detection.  Defaults to the Buf default of `List(FILE)`.

## How it works

Buf requires that proto files be organized into Buf primitives of modules and workspaces to construct an image.  Images are the primary input for any other Buf operation, such as breaking change detection.  Modules and workspaces are both defined
using simple YAML configuration files at the root of their respective directories.  Buf requires all imports/dependencies of a protofile
to be present to build an image.  

This plugin works by way of creating 'virtual' Buf modules and workspace based on the configuration of ScalaPB, namely the proto sources and dependencies configured on the sbt project (a similar approach as taken by the [buf-gradle-plugin](https://github.com/andrewparmet/buf-gradle-plugin)).  This has the effect of creating `buf.yaml` and `buf.work.yaml` in the respective source,  external sources, and workspace directories managed by sbt-protoc.  Clients should be aware of these as 'managed' files insofar as they are not intended to be edited directly or necessarily checked into source control.

For this reason, it's not straightforward to build a Buf image of an arbitrary commit in history on the fly, as the native Buf tool supports.  This would rely on an assumption that the commit in git history met all the same requirements:  had both ScalaPB and this plugin enabled and configured.

#### Publishes image artifact

By default this plugin adds a Buf image artifact to the build.  This has the effect of building a Buf image from the configuration managed by sbt-protoc plugin, and publishing it as a binary artifact to the artifact repository configured for the project.  This is initiated through the standard `publish` or `publishLocal` tasks.

#### Breaking change detection against published artifact

Given that building an image on the fly is not straightforward, the breaking change detection can currently only
be run against a previously published Buf image artifact for the same project.  The breaking change detection task requires a parameter of the version that the current working directory should be compared against.

## Generating an image for a legacy commit version

Until native support can be implemented in the plugin for building an image from an artibrary commit in history, it is still possible to do so using the existing tasks (assuming the project already has sbt-protoc/ScalaPB support enabled) for comparison against.  As an example:

```
$> git checkout <arbitrary-ref>
$> echo 'addSbtPlugin("com.yoppworks", "sbt-buf", "0.1.0")' >> project/plugins.sbt
$> sbt publish
# publishes version x.y.z
$> git checkout master
$> sbt "bufCompatCheck x.y.z"
```
