package io.github.andrealtb.lockscreenlyrics.management.onboarding

import android.content.Context
import android.content.SharedPreferences

/**
 * The user's setup choices (plan §5.2): its own preferences file, never LyricUiConfig or the
 * Bridge's settings channel. [FLOW_REVISION] marks the step list; a later revision that adds a
 * step reads the older choices and keeps them.
 */
internal class SetupStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): SetupChoices = SetupChoices(
        players = if (prefs.getBoolean(KEY_PLAYERS_CHOSEN, false)) prefs.getStringSet(KEY_PLAYERS, emptySet())!!.toSet() else null,
        skipped = prefs.getStringSet(KEY_SKIPPED, emptySet())!!
            .mapNotNull { name -> SetupStep.entries.firstOrNull { it.name == name } }.toSet(),
        playbackConfirmedAtMillis = prefs.getLong(KEY_PLAYBACK_AT, 0L).takeIf { it > 0L },
    )

    fun save(choices: SetupChoices) {
        prefs.edit()
            .putInt(KEY_FLOW_REVISION, FLOW_REVISION)
            .putBoolean(KEY_PLAYERS_CHOSEN, choices.players != null)
            .putStringSet(KEY_PLAYERS, choices.players.orEmpty())
            .putStringSet(KEY_SKIPPED, choices.skipped.map { it.name }.toSet())
            .putLong(KEY_PLAYBACK_AT, choices.playbackConfirmedAtMillis ?: 0L)
            .apply()
    }

    companion object {
        const val FLOW_REVISION = 1
        private const val FILE = "management_setup"
        private const val KEY_FLOW_REVISION = "flowRevision"
        private const val KEY_PLAYERS_CHOSEN = "playersChosen"
        private const val KEY_PLAYERS = "players"
        private const val KEY_SKIPPED = "skipped"
        private const val KEY_PLAYBACK_AT = "playbackConfirmedAt"
    }
}
