package app.aaps.plugins.aps.loop

import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong
import kotlin.reflect.full.declaredMemberProperties

/**
 * Extra data for remote analysis of loop settings (fork japigr/AndroidAPS).
 *
 * Sent to Nightscout as `openaps.suggested.aapsTuning` in every device status:
 * everything the algorithm saw in its last run (settings, glucose, IOB curve, meal data,
 * autosens), the constraints that limited the result, what the pump actually did,
 * and the algorithm debug output. Full APS/safety preferences are added on change
 * and at least once per hour.
 *
 * Export only. Nothing here is read back and nothing influences dosing.
 */
object TuningData {

    const val KEY = "aapsTuning"
    private const val FORMAT_VERSION = 1
    private const val SETTINGS_EVERY = 60 * 60 * 1000L

    private val settingsPrefixes = listOf("Aps", "Safety", "Absorption", "Autosens", "Insulin", "Loop")

    private var lastSettingsHash = 0
    private var lastSettingsSent = 0L

    fun build(lastRun: Loop.LastRun, runningMode: RM, pump: Pump, preferences: Preferences, now: Long): JSONObject? {
        val request = lastRun.request ?: return null
        val json = JSONObject()
        json.put("v", FORMAT_VERSION)
        json.put("algorithm", request.algorithm.name)
        lastRun.source?.let { json.put("source", it) }
        json.put("runningMode", runningMode.mode.name)
        runningMode.reasons?.let { json.put("runningModeReasons", it) }

        (request.oapsProfile ?: request.oapsProfileAutoIsf)?.let { json.put("profile", it.toJson()) }
        request.glucoseStatus?.let { json.put("glucoseStatus", it.toJson()) }
        request.currentTemp?.let { json.put("currentTemp", it.toJson()) }
        request.mealData?.let { json.put("mealData", it.toJson()) }
        request.autosensResult?.let { json.put("autosens", it.toJson()) }
        request.iobData?.takeIf { it.isNotEmpty() }?.let { json.put("iob", it.toIobJson()) }
        request.variableSens?.let { putRounded(json, "variableSens", it) }

        val constraints = JSONObject()
        request.inputConstraints?.getReasons()?.takeIf { it.isNotEmpty() }?.let { constraints.put("input", it) }
        lastRun.constraintsProcessed?.let { processed ->
            putRounded(constraints, "rate", processed.rate)
            putRounded(constraints, "smb", processed.smb)
            processed.rateConstraint?.getMostLimitedReasons()?.takeIf { it.isNotEmpty() }?.let { constraints.put("rateReasons", it) }
            processed.smbConstraint?.getMostLimitedReasons()?.takeIf { it.isNotEmpty() }?.let { constraints.put("smbReasons", it) }
        }
        json.put("constraints", constraints)

        val enact = JSONObject()
        enact.put("tbrRequested", lastRun.lastTBRRequest)
        enact.put("tbrEnacted", lastRun.lastTBREnact)
        enact.put("smbRequested", lastRun.lastSMBRequest)
        enact.put("smbEnacted", lastRun.lastSMBEnact)
        lastRun.tbrSetByPump?.let {
            enact.put("tbr", JSONObject().put("success", it.success).put("enacted", it.enacted).put("rate", it.absolute).put("duration", it.duration).put("comment", it.comment))
        }
        lastRun.smbSetByPump?.let {
            enact.put("smb", JSONObject().put("success", it.success).put("delivered", it.bolusDelivered).put("comment", it.comment))
        }
        json.put("enact", enact)

        json.put("pump", JSONObject().put("lastDataTime", pump.lastDataTime).put("connected", pump.isConnected()).put("suspended", pump.isSuspended()).put("baseBasal", pump.baseBasalRate))

        request.scriptDebug?.takeIf { it.isNotEmpty() }?.let { json.put("debug", JSONArray(it)) }

        val settings = settings(preferences)
        val settingsHash = settings.toString().hashCode()
        if (settingsHash != lastSettingsHash || now - lastSettingsSent > SETTINGS_EVERY) {
            json.put("settings", settings)
            lastSettingsHash = settingsHash
            lastSettingsSent = now
        }
        return json
    }

    private fun settings(preferences: Preferences): JSONObject {
        val json = JSONObject()
        fun wanted(name: String) = settingsPrefixes.any { name.startsWith(it) }
        BooleanKey.entries.filter { wanted(it.name) }.forEach { json.put(it.key, preferences.get(it)) }
        IntKey.entries.filter { wanted(it.name) }.forEach { json.put(it.key, preferences.get(it)) }
        DoubleKey.entries.filter { wanted(it.name) }.forEach { putRounded(json, it.key, preferences.get(it)) }
        UnitDoubleKey.entries.filter { wanted(it.name) }.forEach { putRounded(json, it.key, preferences.get(it)) }
        StringKey.entries.filter { wanted(it.name) }.forEach { json.put(it.key, preferences.get(it)) }
        return json
    }

    // Flat data classes only: numbers, booleans and strings are copied, anything else is skipped
    private fun Any.toJson(): JSONObject {
        val json = JSONObject()
        this::class.declaredMemberProperties.forEach { property ->
            when (val value = property.call(this)) {
                is Double  -> putRounded(json, property.name, value)
                is Number  -> json.put(property.name, value)
                is Boolean -> json.put(property.name, value)
                is String  -> json.put(property.name, value)
            }
        }
        return json
    }

    // First element in full, the rest as compact arrays (5 min steps) needed to recompute predictions
    private fun Array<IobTotal>.toIobJson(): JSONObject {
        val json = JSONObject()
        json.put("first", this[0].toJson().also { first -> this[0].iobWithZeroTemp?.let { first.put("iobWithZeroTemp", it.toJson()) } })
        json.put("iob", JSONArray(this.map { round(it.iob) }))
        json.put("activity", JSONArray(this.map { round(it.activity) }))
        json.put("iobZeroTemp", JSONArray(this.map { round(it.iobWithZeroTemp?.iob ?: 0.0) }))
        json.put("activityZeroTemp", JSONArray(this.map { round(it.iobWithZeroTemp?.activity ?: 0.0) }))
        return json
    }

    private fun putRounded(json: JSONObject, name: String, value: Double) {
        if (value.isFinite()) json.put(name, round(value))
    }

    private fun round(value: Double): Double = if (value.isFinite()) (value * 100000.0).roundToLong() / 100000.0 else 0.0
}
