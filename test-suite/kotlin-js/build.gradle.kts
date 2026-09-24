plugins { kotlin("multiplatform") version "2.4.0" }

kotlin {
    js { nodejs() }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { nodejs() }
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    sourceSets {
        commonMain { kotlin.srcDir("generated/commonMain") }
        wasmJsMain { kotlin.srcDir("generated/wasmJsMain") }
        wasmJsTest { dependencies { implementation(kotlin("test")) } }
        jsMain { kotlin.srcDir("generated/jsMain") }
        jsTest { dependencies { implementation(kotlin("test")) } }
    }
}
