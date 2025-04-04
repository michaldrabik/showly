package com.michaldrabik.data_local.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
  tableName = "shows_discover",
  foreignKeys = [
    ForeignKey(
      entity = Show::class,
      parentColumns = arrayOf("id_trakt"),
      childColumns = arrayOf("id_trakt"),
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class DiscoverShow(
  @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
  @ColumnInfo(name = "id_trakt", defaultValue = "-1", index = true) val idTrakt: Long,
  @ColumnInfo(name = "created_at", defaultValue = "-1") val createdAt: Long,
  @ColumnInfo(name = "updated_at", defaultValue = "-1") val updatedAt: Long,
)
