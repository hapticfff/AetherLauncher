package com.example.aetherlauncher.runtime

object AndroidRuntimeCatalog {
    private const val BASE = "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download"

    data class RuntimePackage(
        val majorVersion: Int,
        val architecture: String,
        val url: String,
        val sha256: String
    )

    fun packageFor(majorVersion: Int, architecture: String): RuntimePackage? {
        val fileArchitecture = when (architecture) {
            "arm" -> "arm"
            "arm64" -> "arm64"
            "x86" -> "x86"
            "x86_64" -> "x86_64"
            else -> return null
        }

        val sha = when (majorVersion to fileArchitecture) {
            8 to "arm" -> "9dbee3b09af5f170e2ed9dd596bc81d0e573c88f8bf760c59c8fadbb9073d1e7"
            8 to "arm64" -> "9a59124d9791957d55c68be664ab76831f336cf2e1e1cd4414220c6fdbf0e06d"
            8 to "x86" -> "b96ce49fab52b28688dccc1a7d85dfc6d0f4048636aac826e1967524b28f75af"
            8 to "x86_64" -> "b1fbcef4965c17925894febe8216d089c6dd47b37950b5f945a89616443c1d0e"
            17 to "arm" -> "4a9134f1ebf6340dd855805d351712462cddab6f3c2684da7e7da10ccf06648d"
            17 to "arm64" -> "e162c860fe05ee4a4e4af7606437419879f6c748386a7b09fa77d10db6a64091"
            17 to "x86" -> "223a2d54606a9eb853c8451cf1d6bda1a8f09cb357f40b790140e57d45731ed7"
            17 to "x86_64" -> "893e27d2aed8b40407f29fe939e2a0f193e5d55f72892a303db634b0808a2b61"
            21 to "arm" -> "96c297487def64666e379a9a363d9955c05b1a0b091b0cf24af88359a66f394a"
            21 to "arm64" -> "8d41ec401ee59f7722df60ed991f81ad146e130452804bfdd8a05d3436f7bbfe"
            21 to "x86" -> "9b8c7d10c5f751acb3b33506593da44ece52a0fd03e0b3c283ba08a7f285a40f"
            21 to "x86_64" -> "cb88723961f5f9ad63afa1f212eb199816c27cabfd7dc66567bde1d8fb69713b"
            else -> return null
        }

        val tag = when (majorVersion) {
            8 -> "download_jre8"
            17, 21 -> "download_jre21"
            else -> return null
        }
        val name = "jre${majorVersion}-android-${fileArchitecture}.tar.xz"
        return RuntimePackage(majorVersion, architecture, "$BASE/$tag/$name", sha)
    }
}
