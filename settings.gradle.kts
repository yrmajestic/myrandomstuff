plugins {
    id("org.jetbrains.kotlin.toolchains.foojay-resolver-convention") version "0.10.0"
}
rootProject.name = "CloudstreamPlugins"

// سنقوم فقط بتضمين الإضافة الخاصة بك لتسريع البناء وتجنب أخطاء الإضافات الأخرى
include(":OnepornProvider")
