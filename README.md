# sbt-buf

Simple SBT plugin providing wrappers around a subset of Buf operations

## Dependencies

Has an unmanaged dependency on [Buf](https://docs.buf.build/installation) being installed on the build system.  It uses only the `buf` binary, which it expects to find on `$PATH`

This version of the plugin was tested and validated against Buf version 1.1.0:

## Usage

Include in `project/plugins.sbt`:

```
addSbtPlugin("com.yoppworks", "sbt-buf", "0.1.0")
```

### Testing

Run `test` for regular unit tests.

Run `scripted` for [sbt script tests](http://www.scala-sbt.org/1.x/docs/Testing-sbt-plugins.html).

