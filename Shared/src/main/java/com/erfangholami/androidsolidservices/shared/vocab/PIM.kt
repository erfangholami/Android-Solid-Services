package com.erfangholami.androidsolidservices.shared.vocab

/**
 * Personal Information Management (PIM) space vocabulary.
 * http://www.w3.org/ns/pim/space#
 * Used by Solid for storage and preferences.
 */
public object PIM {
    public const val NAMESPACE: String = "http://www.w3.org/ns/pim/space#"

    /** A Solid pod storage root container. */
    public const val STORAGE_TYPE: String = "${NAMESPACE}Storage"

    /** A configuration / preferences file. */
    public const val CONFIGURATION_FILE: String = "${NAMESPACE}ConfigurationFile"

    /** A workspace for personal data. */
    public const val WORKSPACE_TYPE: String = "${NAMESPACE}Workspace"

    public const val CONTROLLED_STORAGE: String = "${NAMESPACE}ControlledStorage"
    public const val PERSONAL_STORAGE: String = "${NAMESPACE}PersonalStorage"

    /** Links a WebID to its storage root(s). */
    public const val STORAGE: String = "${NAMESPACE}storage"

    /** Links a WebID to its preferences / configuration file. */
    public const val PREFERENCES_FILE: String = "${NAMESPACE}preferencesFile"

    public const val WORKSPACE: String = "${NAMESPACE}workspace"
    public const val MASTER_WORKSPACE: String = "${NAMESPACE}masterWorkspace"
}
