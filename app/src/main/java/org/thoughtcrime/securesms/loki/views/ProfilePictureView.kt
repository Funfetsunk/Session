package org.thoughtcrime.securesms.loki.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.annotation.DimenRes
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.android.synthetic.main.view_profile_picture.view.*
import network.loki.messenger.R
import org.session.libsession.avatars.ProfileContactPhoto
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.mentions.MentionsManager
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.TextSecurePreferences
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.loki.utilities.AvatarPlaceholderGenerator
import org.thoughtcrime.securesms.mms.GlideRequests

class ProfilePictureView : RelativeLayout {
    lateinit var glide: GlideRequests
    var publicKey: String? = null
    var displayName: String? = null
    var additionalPublicKey: String? = null
    var additionalDisplayName: String? = null
    var isLarge = false

    private val profilePicturesCache = mutableMapOf<String, String?>()

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) { initialize() }

    private fun initialize() {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val contentView = inflater.inflate(R.layout.view_profile_picture, null)
        addView(contentView)
    }
    // endregion

    // region Updating
    fun update(recipient: Recipient, threadID: Long) {
        fun getUserDisplayName(publicKey: String): String {
            val contact = DatabaseFactory.getSessionContactDatabase(context).getContactWithSessionID(publicKey)
            return contact?.displayName(Contact.ContactContext.REGULAR) ?: publicKey
        }
        fun isOpenGroupWithProfilePicture(recipient: Recipient): Boolean {
            return recipient.isOpenGroupRecipient && recipient.groupAvatarId != null
        }
        if (recipient.isGroupRecipient && !isOpenGroupWithProfilePicture(recipient)) {
            val users = MentionsManager.userPublicKeyCache[threadID]?.toMutableList() ?: mutableListOf()
            users.remove(TextSecurePreferences.getLocalNumber(context))
            val randomUsers = users.sorted().toMutableList() // Sort to provide a level of stability
            if (users.count() == 1) {
                val userPublicKey = TextSecurePreferences.getLocalNumber(context)!!
                randomUsers.add(0, userPublicKey) // Ensure the current user is at the back visually
            }
            val pk = randomUsers.getOrNull(0) ?: ""
            publicKey = pk
            displayName = getUserDisplayName(pk)
            val apk = randomUsers.getOrNull(1) ?: ""
            additionalPublicKey = apk
            additionalDisplayName = getUserDisplayName(apk)
        } else {
            val publicKey = recipient.address.toString()
            this.publicKey = publicKey
            displayName = getUserDisplayName(publicKey)
            additionalPublicKey = null
        }
        update()
    }

    fun update() {
        val publicKey = publicKey ?: return
        val additionalPublicKey = additionalPublicKey
        if (additionalPublicKey != null) {
            setProfilePictureIfNeeded(doubleModeImageView1, publicKey, displayName, R.dimen.small_profile_picture_size)
            setProfilePictureIfNeeded(doubleModeImageView2, additionalPublicKey, additionalDisplayName, R.dimen.small_profile_picture_size)
            doubleModeImageViewContainer.visibility = View.VISIBLE
        } else {
            glide.clear(doubleModeImageView1)
            glide.clear(doubleModeImageView2)
            doubleModeImageViewContainer.visibility = View.INVISIBLE
        }
        if (additionalPublicKey == null && !isLarge) {
            setProfilePictureIfNeeded(singleModeImageView, publicKey, displayName, R.dimen.medium_profile_picture_size)
            singleModeImageView.visibility = View.VISIBLE
        } else {
            glide.clear(singleModeImageView)
            singleModeImageView.visibility = View.INVISIBLE
        }
        if (additionalPublicKey == null && isLarge) {
            setProfilePictureIfNeeded(largeSingleModeImageView, publicKey, displayName, R.dimen.large_profile_picture_size)
            largeSingleModeImageView.visibility = View.VISIBLE
        } else {
            glide.clear(largeSingleModeImageView)
            largeSingleModeImageView.visibility = View.INVISIBLE
        }
    }

    private fun setProfilePictureIfNeeded(imageView: ImageView, publicKey: String, displayName: String?, @DimenRes sizeResId: Int) {
        if (publicKey.isNotEmpty()) {
            val recipient = Recipient.from(context, Address.fromSerialized(publicKey), false)
            if (profilePicturesCache.containsKey(publicKey) && profilePicturesCache[publicKey] == recipient.profileAvatar) return
            val signalProfilePicture = recipient.contactPhoto
            val avatar = (signalProfilePicture as? ProfileContactPhoto)?.avatarObject
            if (signalProfilePicture != null && avatar != "0" && avatar != "") {
                glide.clear(imageView)
                glide.load(signalProfilePicture).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC).circleCrop().into(imageView)
                profilePicturesCache[publicKey] = recipient.profileAvatar
            } else {
                val sizeInPX = resources.getDimensionPixelSize(sizeResId)
                glide.clear(imageView)
                glide.load(AvatarPlaceholderGenerator.generate(context, sizeInPX, publicKey, displayName))
                    .diskCacheStrategy(DiskCacheStrategy.ALL).circleCrop().into(imageView)
                profilePicturesCache[publicKey] = recipient.profileAvatar
            }
        } else {
            imageView.setImageDrawable(null)
        }
    }

    fun recycle() {
        profilePicturesCache.clear()
    }
    // endregion
}
