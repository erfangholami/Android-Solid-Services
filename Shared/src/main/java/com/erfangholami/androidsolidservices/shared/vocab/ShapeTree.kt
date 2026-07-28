package com.erfangholami.androidsolidservices.shared.vocab

/**
 * Shape Trees vocabulary constants.
 * http://www.w3.org/ns/shapetrees#
 * Spec: https://shapetrees.org/TR/specification/
 *
 * Shape Trees define the expected structure and shape-validation rules
 * for composite data in a Solid pod. They are used by SAI to describe
 * what data an application needs and how it is organized.
 */
public object ShapeTree {
    public const val NAMESPACE: String = "http://www.w3.org/ns/shapetrees#"

    /**
     * Defines a composite data structure: a tree of resources and validation
     * criteria (linked shape, expected resource type, child shape trees).
     */
    public const val SHAPE_TREE: String = "${NAMESPACE}ShapeTree"

    /** A shape tree that expects an LDP container resource. */
    public const val CONTAINER: String = "${NAMESPACE}Container"

    /** A shape tree that expects an RDF resource (non-container). */
    public const val RESOURCE: String = "${NAMESPACE}Resource"

    /** A shape tree that expects a binary (non-RDF) resource. */
    public const val NON_RDF_RESOURCE: String = "${NAMESPACE}NonRDFResource"

    /** Describes how one shape tree references another. */
    public const val REFERENCE: String = "${NAMESPACE}Reference"

    /**
     * Associates a managed resource with one or more shape tree assignments.
     * Stored in a shape tree manager auxiliary resource.
     */
    public const val MANAGER: String = "${NAMESPACE}Manager"

    /**
     * Identifies a shape tree, the managed resource it is assigned to,
     * the focus node for validation, and the navigation path to reach it.
     */
    public const val ASSIGNMENT: String = "${NAMESPACE}Assignment"

    /** An index of SKOS poly-hierarchies used to describe shape trees. */
    public const val DESCRIPTION_SET: String = "${NAMESPACE}DescriptionSet"

    /** A human-readable description of a shape tree. */
    public const val DESCRIPTION: String = "${NAMESPACE}Description"

    /**
     * Describes the expected resource type for this shape tree node
     * (one of `st:Container`, `st:Resource`, `st:NonRDFResource`).
     */
    public const val EXPECTS_TYPE: String = "${NAMESPACE}expectsType"

    /**
     * Links a shape tree to another shape tree directly contained within
     * its planted container hierarchy.
     */
    public const val CONTAINS: String = "${NAMESPACE}contains"

    /** Links a shape tree to a Shape Tree Reference (for traversal via predicates). */
    public const val REFERENCES: String = "${NAMESPACE}references"

    /** Links a shape tree to a Shape (ShEx or SHACL) for instance validation. */
    public const val SHAPE: String = "${NAMESPACE}shape"

    /** Points to the shape tree being referenced in a Reference node. */
    public const val REFERENCES_SHAPE_TREE: String = "${NAMESPACE}referencesShapeTree"

    /**
     * Describes traversal to a referenced shape tree via a ShapePath expression
     * through the RDF graph of an instance.
     */
    public const val VIA_SHAPE_PATH: String = "${NAMESPACE}viaShapePath"

    /**
     * Describes traversal to a referenced shape tree by following a specific
     * RDF predicate in the instance data.
     */
    public const val VIA_PREDICATE: String = "${NAMESPACE}viaPredicate"

    /** Links a shape tree manager to a shape tree assignment. */
    public const val HAS_ASSIGNMENT: String = "${NAMESPACE}hasAssignment"

    /** Links a shape tree manager to the managed resource. */
    public const val MANAGES: String = "${NAMESPACE}manages"

    /** Links a managed resource back to the shape tree manager. */
    public const val MANAGED_BY: String = "${NAMESPACE}managedBy"

    /** Identifies the root assignment within a planted shape tree hierarchy. */
    public const val HAS_ROOT_ASSIGNMENT: String = "${NAMESPACE}hasRootAssignment"

    /**
     * Identifies the focus node (subject IRI) in the instance data
     * against which the linked shape is validated.
     */
    public const val FOCUS_NODE: String = "${NAMESPACE}focusNode"

    /** The IRI of the shape tree assigned to the managed resource. */
    public const val ASSIGNS: String = "${NAMESPACE}assigns"

    /** Links a description to the shape tree it describes. */
    public const val DESCRIBES: String = "${NAMESPACE}describes"

    /** The description set this description belongs to. */
    public const val IN_DESCRIPTION_SET: String = "${NAMESPACE}inDescriptionSet"

    /** The language used by the associated description resource. */
    public const val USES_LANGUAGE: String = "${NAMESPACE}usesLanguage"
}
