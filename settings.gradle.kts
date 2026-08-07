rootProject.name = "CyberLevels"

// Builds these from source and substitutes them for their com.bitaspire coordinates, so this
// plugin can be developed against unpublished changes. Locally the checkouts are sibling folders;
// CI checks them out inside this repository instead. They are not on any maven repository, so the
// checkout is required rather than a fallback.
listOf("CyberCore").forEach { dependency ->
    listOf("../$dependency", dependency)
        .map(::file)
        .firstOrNull { it.resolve("settings.gradle.kts").isFile }
        ?.let(::includeBuild)
}
