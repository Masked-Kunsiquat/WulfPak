package com.github.maskedkunisquat.wulfpak.ui.people

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelationLabel
import kotlinx.coroutines.launch
import java.util.UUID

class AddEditPersonViewModel(app: Application) : AndroidViewModel(app) {

    private val personDao = getApplication<AppApplication>().db.personDao()

    var firstName       by mutableStateOf("")
    var lastName        by mutableStateOf("")
    var nickname        by mutableStateOf("")
    var jobTitle        by mutableStateOf("")
    var company         by mutableStateOf("")
    var relationLabel   by mutableStateOf(RelationLabel.FRIEND)
    var closenessRating by mutableStateOf<Int?>(null)
    var photoUri        by mutableStateOf<String?>(null)
    var isMe            by mutableStateOf(false)
        private set

    private var existingId: UUID? = null

    fun load(personId: UUID) {
        viewModelScope.launch {
            val p = personDao.getById(personId) ?: return@launch
            existingId      = p.id
            firstName       = p.firstName
            lastName        = p.lastName ?: ""
            nickname        = p.nickname ?: ""
            jobTitle        = p.jobTitle ?: ""
            company         = p.company ?: ""
            relationLabel   = p.relationLabel
            closenessRating = p.closenessRating
            photoUri        = p.photoUri
            isMe            = p.isMe
        }
    }

    val isValid: Boolean get() = firstName.isNotBlank()

    fun save(onDone: () -> Unit) {
        if (!isValid) return
        viewModelScope.launch {
            val id = existingId
            if (id == null) {
                personDao.insert(Person(
                    firstName       = firstName.trim(),
                    lastName        = lastName.trim().ifEmpty { null },
                    nickname        = nickname.trim().ifEmpty { null },
                    jobTitle        = jobTitle.trim().ifEmpty { null },
                    company         = company.trim().ifEmpty { null },
                    relationLabel   = relationLabel,
                    closenessRating = closenessRating,
                    photoUri        = photoUri,
                ))
            } else {
                personDao.getById(id)?.let { existing ->
                    personDao.update(existing.copy(
                        firstName       = firstName.trim(),
                        lastName        = lastName.trim().ifEmpty { null },
                        nickname        = nickname.trim().ifEmpty { null },
                        jobTitle        = jobTitle.trim().ifEmpty { null },
                        company         = company.trim().ifEmpty { null },
                        relationLabel   = relationLabel,
                        closenessRating = closenessRating,
                        photoUri        = photoUri,
                    ))
                }
            }
            onDone()
        }
    }
}
