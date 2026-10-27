package my.mysolat.model

/** Represents a Malaysian state and its associated prayer zone codes. */
public data class WaktuState(
    /** The name of the state (e.g., "Johor"). */
    val negeri: String,
    /** The prayer zone codes belonging to this state (e.g., ["JHR01", "JHR02"]). */
    val zones: List<String>,
)
