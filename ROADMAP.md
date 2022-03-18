## Future Improvement Ideas/TODO List

- Add support for publishing
- Greater flexibility for configuring both breaking change detection and lint tasks.
- Support for building Buf images against an arbitrary git ref (assuming it meets the necessary requirements)
  - Necessary for supporting a task that would run breaking change detection against `master`, for example.
- Find a way to provide Buf binary in a managed way - look to sbt-protoc for inspiration as they do something similar with protoc.
