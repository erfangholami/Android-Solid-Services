package com.erfangholami.androidsolidservices.shared.model.datamodule

/**
 * The container every data module allocates its own storage inside, relative to the pod root:
 * `{storage}datamodule/{module}/`.
 *
 * One root keeps app data together instead of scattering a folder per module across the pod
 * root, and makes the root a framework decision rather than a per-module one. It applies to
 * **newly allocated** containers only — a module is always discovered through the type index, so
 * pods that registered a container before this existed keep working untouched. Relocating them
 * would change their URIs, which would break every share link and access grant pointing at them.
 */
public const val DATA_MODULE_ROOT: String = "datamodule/"
