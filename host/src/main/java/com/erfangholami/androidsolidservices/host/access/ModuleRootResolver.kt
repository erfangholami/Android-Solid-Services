package com.erfangholami.androidsolidservices.host.access

import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.shared.model.datamodule.DataModuleId
import java.util.concurrent.ConcurrentHashMap

/**
 * Where a data module lives on an account's pod, so a grant on the module can cover the
 * module's resources when a raw resource verb names one of them.
 *
 * Only the host knows: the containers come from the type-index registrations, which can differ
 * per pod. [invalidate] drops what is remembered for a WebID after a verb that may have added a
 * container.
 */
public interface ModuleRootResolver {

    /** The containers [moduleId] occupies on [webId]'s pod; empty for a module the host does not know. */
    public suspend fun rootContainers(moduleId: String, webId: String): List<String>

    /** Forgets anything remembered for [webId]. */
    public fun invalidate(webId: String) {}

    public companion object {

        /** A resolver that knows no module: a module entry then covers the module's verbs only. */
        public val NONE: ModuleRootResolver = object : ModuleRootResolver {
            override suspend fun rootContainers(moduleId: String, webId: String): List<String> = emptyList()
        }
    }
}

/** The resolver over the library's two data modules. */
public class DataModuleRootResolver(
    private val contacts: SolidContactsDataModule,
    private val tickets: SolidTicketsDataModule,
) : ModuleRootResolver {

    override suspend fun rootContainers(moduleId: String, webId: String): List<String> = when (moduleId) {
        DataModuleId.CONTACTS -> contacts.rootContainers(webId).getOrDefault(emptyList())
        DataModuleId.TICKETS -> tickets.rootContainers(webId).getOrDefault(emptyList())
        else -> emptyList()
    }
}

/**
 * Remembers a delegate's answers for [ttlMillis], because resolving a module's containers reads
 * the type index and a granted app calls many times a second. A container created through the
 * data-module binder is announced with [invalidate], so the next check sees it.
 */
public class CachingModuleRootResolver(
    private val delegate: ModuleRootResolver,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) : ModuleRootResolver {

    private class Entry(val expiresAt: Long, val roots: List<String>)

    private val entries = ConcurrentHashMap<Pair<String, String>, Entry>()

    override suspend fun rootContainers(moduleId: String, webId: String): List<String> {
        val key = moduleId to webId
        val now = clock()
        entries[key]?.takeIf { it.expiresAt > now }?.let { return it.roots }
        val roots = delegate.rootContainers(moduleId, webId)
        entries[key] = Entry(now + ttlMillis, roots)
        return roots
    }

    override fun invalidate(webId: String) {
        entries.keys.removeAll { it.second == webId }
    }

    public companion object {
        public const val DEFAULT_TTL_MILLIS: Long = 60_000L
    }
}
