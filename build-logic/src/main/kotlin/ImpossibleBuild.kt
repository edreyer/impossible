// Values every module's convention plugin shares, so the multiplatform and JVM-only
// modules publish under one group and version and build for the same JDK floor.
object ImpossibleBuild {
    const val GROUP = "io.liquidsoftware"
    const val VERSION = "0.1.0"

    // JDK 17 is the oldest JDK consumers can run this library on.
    const val JVM_TOOLCHAIN = 17
}
