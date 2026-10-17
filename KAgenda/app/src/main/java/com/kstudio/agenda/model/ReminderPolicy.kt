package com.kstudio.agenda.model

/** Individual overrides remain active even when the global default is off. */
data class ReminderPolicy(val notify: Boolean, val alarm: Boolean, val leadMinutes: Int) {
    val enabled: Boolean get() = notify || alarm
    companion object {
        fun forEvent(event: AgendaEvent, globalEnabled: Boolean, includeAgenda: Boolean, globalAlarm: Boolean, globalLead: Int): ReminderPolicy =
            if (event.followGlobalReminder) ReminderPolicy(globalEnabled && includeAgenda, globalEnabled && includeAgenda && globalAlarm, globalLead.coerceIn(0, 120))
            else ReminderPolicy(event.notifyEnabled, event.alarmEnabled, event.reminderLeadMinutes.coerceIn(0, 10080))
    }
}
