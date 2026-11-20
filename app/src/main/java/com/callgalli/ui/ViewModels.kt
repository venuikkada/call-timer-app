package com.callgalli.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.callgalli.AppContainer
import com.callgalli.CallGalliApp
import com.callgalli.data.BlockedSummary
import com.callgalli.data.NumberToday
import com.callgalli.data.PhoneContact
import com.callgalli.data.RuleEntity
import com.callgalli.data.TodaySummary
import com.callgalli.rules.PhoneNumbers
import com.callgalli.rules.RuleSettings
import com.callgalli.rules.RulesEngine
import com.callgalli.rules.Stage
import com.callgalli.telecom.InCallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun <T> kotlinx.coroutines.flow.Flow<T>.state(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

private val CreationExtrasApp get() = ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY

/** Builds every ViewModel from the app's [AppContainer]. */
val AppViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer { HomeViewModel((this[CreationExtrasApp] as CallGalliApp).container) }
    initializer { ContactsViewModel((this[CreationExtrasApp] as CallGalliApp).container) }
    initializer { BlockedViewModel((this[CreationExtrasApp] as CallGalliApp).container) }
    initializer { AllowedViewModel((this[CreationExtrasApp] as CallGalliApp).container) }
    initializer { SettingsViewModel((this[CreationExtrasApp] as CallGalliApp).container) }
}

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val summary: StateFlow<TodaySummary> = c.repository.todaySummary.state(this, TodaySummary())
    val numbers: StateFlow<List<NumberToday>> = c.repository.numbersToday.state(this, emptyList())
    val inCall: StateFlow<InCallState> = c.callMonitor.state
    fun hangUp() = c.callMonitor.hangUp()
    val settings = c.settings.settings
}

/** One row on the Contacts & Rules screen. */
data class ContactRow(
    val name: String?,
    val number: String,
    val stage: Stage,
    val alwaysAllowed: Boolean,
)

class ContactsViewModel(private val c: AppContainer) : ViewModel() {
    private val contacts = MutableStateFlow<List<PhoneContact>>(emptyList())
    val query = MutableStateFlow("")
    val hasPermission = MutableStateFlow(c.contacts.hasPermission())

    val rows: StateFlow<List<ContactRow>> = combine(
        contacts, c.repository.usageToday, c.repository.alwaysAllowed, query, c.repository.todayKeyFlow,
    ) { list, usage, allowed, q, today ->
        val engine = RulesEngine(c.settings.current.rules)
        val usageBy = usage.associateBy { it.number }
        val allowedSet = allowed.map { it.number }.toSet()
        // Contacts first, then numbers used today that are not saved contacts.
        val fromContacts = list.map { it.number to it.name }
        val extra = usage.filter { u -> list.none { it.number == u.number } }.map { it.number to it.displayName }
        (fromContacts + extra)
            .filter { (number, name) ->
                q.isBlank() || name?.contains(q, ignoreCase = true) == true ||
                    PhoneNumbers.normalize(q).let { d -> d.isNotEmpty() && number.contains(d) }
            }
            .map { (number, name) ->
                ContactRow(name, number, engine.stageOf(usageBy[number]?.toNumberDay(), today), number in allowedSet)
            }
    }.state(this, emptyList())

    fun reload() {
        hasPermission.value = c.contacts.hasPermission()
        viewModelScope.launch { contacts.value = c.contacts.allContacts() }
    }

    fun setAlwaysAllowed(row: ContactRow, allowed: Boolean) =
        viewModelScope.launch { c.repository.setAlwaysAllowed(row.number, row.name, allowed) }

    fun reset(row: ContactRow) = viewModelScope.launch { c.repository.resetNumberToday(row.number) }
}

class BlockedViewModel(private val c: AppContainer) : ViewModel() {
    val blocked: StateFlow<List<BlockedSummary>> = c.repository.blockedToday.state(this, emptyList())
    fun history(number: String) = c.repository.history(number)
    fun sessions(number: String) = c.repository.recentSessions(number)
}

class AllowedViewModel(private val c: AppContainer) : ViewModel() {
    val allowed: StateFlow<List<RuleEntity>> = c.repository.alwaysAllowed.state(this, emptyList())

    /** Returns false if the number is empty after cleaning. */
    fun add(name: String?, rawNumber: String): Boolean {
        val number = PhoneNumbers.normalize(rawNumber)
        if (number.isEmpty()) return false
        viewModelScope.launch { c.repository.setAlwaysAllowed(number, name?.ifBlank { null }, true) }
        return true
    }

    fun remove(rule: RuleEntity) = viewModelScope.launch { c.repository.setAlwaysAllowed(rule.number, rule.displayName, false) }

    fun readPicked(uri: android.net.Uri): PhoneContact? = c.contacts.readPickedPhone(uri)
}

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings
    fun updateRules(transform: (RuleSettings) -> RuleSettings) = c.settings.updateRules(transform)
    fun setTheme(mode: com.callgalli.data.ThemeMode) = c.settings.setTheme(mode)
    fun clearAll() = viewModelScope.launch { c.repository.clearAllData() }
}
