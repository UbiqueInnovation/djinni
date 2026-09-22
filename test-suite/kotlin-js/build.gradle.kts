plugins { kotlin("multiplatform") version "2.4.0" }

kotlin {
    js { nodejs() }
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    sourceSets {
        commonMain { kotlin.srcDir("generated/commonMain") }
        jsMain { kotlin.srcDir("generated/jsMain") }
        jsTest { dependencies { implementation(kotlin("test")) } }
    }
}
