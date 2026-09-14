External Test
-------------
This folder is a standalone Bazel 9.2.0 module that consumes Djinni through a
`local_path_override`. From this folder, run:

```sh
bazel run @djinni//src:djinni -- <command line flags>
```

Copy the setup in `MODULE.bazel` to use a local Djinni checkout from another
project, adjusting the override path. The root project must configure the Scala
toolchain: Djinni requires Scala 2.11.12. If the project already uses a different
Scala version, add `2.11.12` to its `scala_versions` setting instead of changing
its default. Djinni selects its own version on the generator target.

Run `bash ci/bazel.sh` from the parent directory to compare generated output from
the local target, packaged executable, and this external module.
