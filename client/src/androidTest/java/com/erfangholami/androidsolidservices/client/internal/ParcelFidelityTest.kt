package com.erfangholami.androidsolidservices.client.internal

import android.os.Parcel
import android.os.Parcelable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.erfangholami.androidsolidservices.client.internal.fakes.Fixtures
import com.erfangholami.androidsolidservices.shared.model.access.WacAllow
import com.erfangholami.androidsolidservices.shared.model.resource.AccessProbe
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Round-trips every model that crosses the IPC boundary through a real [Parcel].
 *
 * The IPC suites prove these types survive a real transaction; this one localises a failure when
 * they do not. It matters most for the hand-written parcel code — [SolidMetadata] writes nineteen
 * fields in sequence and [AccessProbe] stamps a discriminator its parent reads back — where a
 * single misordered read or write corrupts every field after it and produces plausible garbage
 * rather than an error.
 */
@RunWith(AndroidJUnit4::class)
class ParcelFidelityTest {

    @Test
    fun SolidMetadata_survives_with_every_field_intact() {
        assertEquals(Fixtures.METADATA, Fixtures.METADATA.roundTrip(SolidMetadata.CREATOR))
    }

    @Test
    fun SolidMetadata_with_everything_absent_survives() {
        assertEquals(SolidMetadata.EMPTY, SolidMetadata.EMPTY.roundTrip(SolidMetadata.CREATOR))
    }

    @Test
    fun WacAllow_survives() {
        val wacAllow = WacAllow(userModes = setOf("read", "append"), publicModes = setOf("read"))
        assertEquals(wacAllow, wacAllow.roundTrip(WacAllow.CREATOR))
    }

    @Test
    fun both_AccessProbe_variants_come_back_as_themselves() {
        assertEquals(Fixtures.ACCESS_PROBE, Fixtures.ACCESS_PROBE.roundTrip(AccessProbe.CREATOR))
        assertEquals(AccessProbe.Denied, AccessProbe.Denied.roundTrip(AccessProbe.CREATOR))
    }

    @Test
    fun an_Accessible_probe_with_no_modes_stays_Accessible() {
        val empty = AccessProbe.Accessible(modes = emptySet(), ownerWebId = null)
        assertEquals(empty, empty.roundTrip(AccessProbe.CREATOR))
    }

    @Test
    fun every_ShareMode_survives_inside_a_probe() {
        val all = AccessProbe.Accessible(modes = ShareMode.entries.toSet(), ownerWebId = Fixtures.WEB_ID)
        assertEquals(all, all.roundTrip(AccessProbe.CREATOR))
    }

    @Test
    fun sharing_models_survive() {
        assertEquals(Fixtures.GIVEN_SHARE, Fixtures.GIVEN_SHARE.roundTripParcelize())
        assertEquals(Fixtures.RECEIVED_SHARE, Fixtures.RECEIVED_SHARE.roundTripParcelize())
        assertEquals(Fixtures.CATALOG_ENTRY, Fixtures.CATALOG_ENTRY.roundTripParcelize())
        assertEquals(Fixtures.ACCESS_GRANT, Fixtures.ACCESS_GRANT.roundTripParcelize())
        assertEquals(Fixtures.SHARE_REQUEST, Fixtures.SHARE_REQUEST.roundTripParcelize())
        assertEquals(Fixtures.SHARE_NOTIFICATION, Fixtures.SHARE_NOTIFICATION.roundTripParcelize())
    }

    @Test
    fun every_ShareReceiver_variant_survives_inside_a_share() {
        listOf(
            ShareReceiver.WebIdReceiver(Fixtures.PEER_WEB_ID),
            ShareReceiver.GroupReceiver(Fixtures.GROUP),
            ShareReceiver.Public,
        ).forEach { receiver ->
            val share = Fixtures.GIVEN_SHARE.copy(receiver = receiver)
            assertEquals(share, share.roundTripParcelize())
        }
    }

    @Test
    fun contact_models_survive() {
        assertEquals(Fixtures.CONTACT_DATA, Fixtures.CONTACT_DATA.roundTripParcelize())
        assertEquals(Fixtures.SOLID_CONTACT, Fixtures.SOLID_CONTACT.roundTripParcelize())
        assertEquals(Fixtures.CONTACT_LIST, Fixtures.CONTACT_LIST.roundTripParcelize())
        assertEquals(Fixtures.ADDRESS_BOOK_MODEL, Fixtures.ADDRESS_BOOK_MODEL.roundTripParcelize())
        assertEquals(Fixtures.ADDRESS_BOOK_LIST, Fixtures.ADDRESS_BOOK_LIST.roundTripParcelize())
        assertEquals(Fixtures.FULL_GROUP, Fixtures.FULL_GROUP.roundTripParcelize())
        assertEquals(Fixtures.CONTACT_MATCH, Fixtures.CONTACT_MATCH.roundTripParcelize())
        assertEquals(Fixtures.CONTACT_PHOTO, Fixtures.CONTACT_PHOTO.roundTripParcelize())
    }

    @Test
    fun ticket_models_survive() {
        assertEquals(Fixtures.NEW_TICKET, Fixtures.NEW_TICKET.roundTripParcelize())
        assertEquals(Fixtures.TICKET_MODEL, Fixtures.TICKET_MODEL.roundTripParcelize())
        assertEquals(Fixtures.TICKET_LIST, Fixtures.TICKET_LIST.roundTripParcelize())
        assertEquals(Fixtures.TICKET_ARTIFACT, Fixtures.TICKET_ARTIFACT.roundTripParcelize())
    }

    @Test
    fun source_references_survive_with_and_without_head_metadata() {
        Fixtures.SOURCE_REFERENCES.forEach { reference ->
            assertEquals(reference, reference.roundTripParcelize())
        }
    }

    @Test
    fun an_RDF_resource_keeps_its_identifier_content_type_and_headers() {
        val source = Fixtures.rdfResource()
        val copy = source.roundTrip(SolidRDFResource.CREATOR)

        assertEquals(source.getIdentifier(), copy.getIdentifier())
        assertEquals(source.getContentType(), copy.getContentType())
        assertEquals(source.getHeaders().toMultimap(), copy.getHeaders().toMultimap())
    }

    @Test
    fun rdf_quads_keep_their_datatype_language_and_graph() {
        val copy = Fixtures.rdfResource().roundTrip(SolidRDFResource.CREATOR)

        assertEquals(Fixtures.QUADS, copy.getAllQuads())
    }

    @Test
    fun a_container_survives_as_a_container() {
        val source = Fixtures.container()
        val copy = source.roundTrip(SolidContainer.CREATOR)

        assertEquals(source.getIdentifier(), copy.getIdentifier())
        assertEquals(Fixtures.QUADS, copy.getAllQuads())
    }

    @Test
    fun a_binary_resource_keeps_its_bytes() {
        val source = Fixtures.nonRdfResource()
        val copy = source.roundTrip(SolidNonRDFResource.CREATOR)

        assertEquals(source.getIdentifier(), copy.getIdentifier())
        assertEquals("image/png", copy.getContentType())
        assertTrue(
            "the body did not survive parcelling",
            Fixtures.PNG_BYTES.contentEquals(copy.getEntity().readBytes()),
        )
    }

    private fun <T : Parcelable> T.roundTrip(creator: Parcelable.Creator<T>): T {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeParcelable(this, 0)
            parcel.setDataPosition(0)
            parcel.readString()
            creator.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    /** For `@Parcelize` types, whose generated CREATOR is easiest to reach through the framework. */
    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    private fun <T : Parcelable> T.roundTripParcelize(): T {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeParcelable(this, 0)
            parcel.setDataPosition(0)
            parcel.readParcelable<T>(javaClass.classLoader) as T
        } finally {
            parcel.recycle()
        }
    }
}
