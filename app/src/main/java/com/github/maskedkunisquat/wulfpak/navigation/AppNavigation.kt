package com.github.maskedkunisquat.wulfpak.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val PEOPLE_LIST        = "people_list"
    const val PERSON_DETAIL      = "person_detail/{personId}"
    const val ADD_EDIT_PERSON    = "add_edit_person?personId={personId}"
    const val ADD_EDIT_INTERACTION  = "add_edit_interaction/{personId}?interactionId={interactionId}"
    const val ADD_EDIT_NOTE      = "add_edit_note/{personId}?noteId={noteId}"
    const val ADD_EDIT_LIFE_EVENT = "add_edit_life_event/{personId}?lifeEventId={lifeEventId}"
    const val ADD_EDIT_GIFT      = "add_edit_gift/{personId}?giftId={giftId}"
    const val ADD_EDIT_TASK      = "add_edit_task?personId={personId}&taskId={taskId}"
    const val ACTIVITY_FEED      = "activity_feed"
    const val SEARCH             = "search"
    const val TASKS              = "tasks"
    const val SETTINGS           = "settings"

    fun personDetail(personId: String) = "person_detail/$personId"
    fun addEditPerson(personId: String? = null) =
        if (personId != null) "add_edit_person?personId=$personId" else "add_edit_person"
    fun addEditInteraction(personId: String, interactionId: String? = null) =
        "add_edit_interaction/$personId" + (interactionId?.let { "?interactionId=$it" } ?: "")
    fun addEditNote(personId: String, noteId: String? = null) =
        "add_edit_note/$personId" + (noteId?.let { "?noteId=$it" } ?: "")
    fun addEditLifeEvent(personId: String, lifeEventId: String? = null) =
        "add_edit_life_event/$personId" + (lifeEventId?.let { "?lifeEventId=$it" } ?: "")
    fun addEditGift(personId: String, giftId: String? = null) =
        "add_edit_gift/$personId" + (giftId?.let { "?giftId=$it" } ?: "")
    fun addEditTask(personId: String? = null, taskId: String? = null): String {
        val params = listOfNotNull(
            personId?.let { "personId=$it" },
            taskId?.let { "taskId=$it" }
        ).joinToString("&")
        return if (params.isEmpty()) "add_edit_task" else "add_edit_task?$params"
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.PEOPLE_LIST,
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.PEOPLE_LIST) {
            Placeholder("People List")
        }

        composable(
            route = Routes.PERSON_DETAIL,
            arguments = listOf(navArgument("personId") { type = NavType.StringType }),
        ) {
            Placeholder("Person Detail")
        }

        composable(
            route = Routes.ADD_EDIT_PERSON,
            arguments = listOf(navArgument("personId") { nullable = true; defaultValue = null }),
        ) {
            Placeholder("Add / Edit Person")
        }

        composable(
            route = Routes.ADD_EDIT_INTERACTION,
            arguments = listOf(
                navArgument("personId") { type = NavType.StringType },
                navArgument("interactionId") { nullable = true; defaultValue = null },
            ),
        ) {
            Placeholder("Add / Edit Interaction")
        }

        composable(
            route = Routes.ADD_EDIT_NOTE,
            arguments = listOf(
                navArgument("personId") { type = NavType.StringType },
                navArgument("noteId") { nullable = true; defaultValue = null },
            ),
        ) {
            Placeholder("Add / Edit Note")
        }

        composable(
            route = Routes.ADD_EDIT_LIFE_EVENT,
            arguments = listOf(
                navArgument("personId") { type = NavType.StringType },
                navArgument("lifeEventId") { nullable = true; defaultValue = null },
            ),
        ) {
            Placeholder("Add / Edit Life Event")
        }

        composable(
            route = Routes.ADD_EDIT_GIFT,
            arguments = listOf(
                navArgument("personId") { type = NavType.StringType },
                navArgument("giftId") { nullable = true; defaultValue = null },
            ),
        ) {
            Placeholder("Add / Edit Gift")
        }

        composable(
            route = Routes.ADD_EDIT_TASK,
            arguments = listOf(
                navArgument("personId") { nullable = true; defaultValue = null },
                navArgument("taskId") { nullable = true; defaultValue = null },
            ),
        ) {
            Placeholder("Add / Edit Task")
        }

        composable(Routes.ACTIVITY_FEED) { Placeholder("Activity Feed") }
        composable(Routes.SEARCH)        { Placeholder("Search") }
        composable(Routes.TASKS)         { Placeholder("Tasks") }
        composable(Routes.SETTINGS)      { Placeholder("Settings") }
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name)
    }
}
