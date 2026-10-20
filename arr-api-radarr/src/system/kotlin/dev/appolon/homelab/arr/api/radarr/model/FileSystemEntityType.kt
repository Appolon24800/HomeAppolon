package dev.appolon.homelab.arr.api.radarr.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Radarr FileSystemEntityType wire enum, constants in spec order. */
@Serializable
enum class FileSystemEntityType {
    @SerialName("parent") PARENT,
    @SerialName("drive") DRIVE,
    @SerialName("folder") FOLDER,
    @SerialName("file") FILE,
    ;

    override fun toString(): String = when (this) {
        PARENT -> "parent"
        DRIVE -> "drive"
        FOLDER -> "folder"
        FILE -> "file"
    }
}
