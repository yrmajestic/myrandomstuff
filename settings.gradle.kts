plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "CloudstreamPlugins"

// تضمين فقط الإضافة الخاصة بك لضمان سرعة ونجاح البناء
include(":OnepornProvider")
