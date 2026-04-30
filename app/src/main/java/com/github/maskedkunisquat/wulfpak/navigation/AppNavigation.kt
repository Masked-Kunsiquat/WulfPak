package com.github.maskedkunisquat.wulfpak.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.maskedkunisquat.wulfpak.ui.feed.ActivityDetailScreen
import com.github.maskedkunisquat.wulfpak.ui.feed.ActivityDetailViewModel
import com.github.maskedkunisquat.wulfpak.ui.feed.ActivityFeedScreen
import com.github.maskedkunisquat.wulfpak.ui.feed.AddEditActivityScreen
import com.github.maskedkunisquat.wulfpak.ui.feed.InteractionDetailScreen
import com.github.maskedkunisquat.wulfpak.ui.feed.InteractionDetailViewModel
import com.github.maskedkunisquat.wulfpak.ui.merge.MergeContactsScreen
import com.github.maskedkunisquat.wulfpak.ui.people.AddEditPersonScreen
import com.github.maskedkunisquat.wulfpak.ui.people.PeopleListScreen
import com.github.maskedkunisquat.wulfpak.ui.person.AddEditGiftScreen
import com.github.maskedkunisquat.wulfpak.ui.person.AddEditInteractionScreen
import com.github.maskedkunisquat.wulfpak.ui.person.AddEditLifeEventScreen
import com.github.maskedkunisquat.wulfpak.ui.person.AddEditNoteScreen
import com.github.maskedkunisquat.wulfpak.ui.person.AddEditTaskScreen
import com.github.maskedkunisquat.wulfpak.ui.person.PersonDetailScreen
import com.github.maskedkunisquat.wulfpak.ui.person.PersonDetailViewModel
import com.github.maskedkunisquat.wulfpak.ui.search.SearchScreen
import com.github.maskedkunisquat.wulfpak.ui.search.SearchViewModel
import com.github.maskedkunisquat.wulfpak.ui.settings.ContactPickScreen
import com.github.maskedkunisquat.wulfpak.ui.settings.SettingsScreen
import com.github.maskedkunisquat.wulfpak.ui.settings.SettingsViewModel
import com.github.maskedkunisquat.wulfpak.ui.tasks.TasksScreen
import java.util.UUID

object Routes {
    const val PEOPLE_LIST           = "people_list"
    const val PERSON_DETAIL         = "person_detail/{personId}"
    const val ADD_EDIT_PERSON       = "add_edit_person?personId={personId}"
    const val ADD_EDIT_INTERACTION  = "add_edit_interaction/{personId}?interactionId={interactionId}"
    const val ADD_EDIT_ACTIVITY     = "add_edit_activity?personId={personId}&activityId={activityId}"
    const val ADD_EDIT_NOTE         = "add_edit_note/{personId}?noteId={noteId}"
    const val ADD_EDIT_LIFE_EVENT   = "add_edit_life_event/{personId}?lifeEventId={lifeEventId}"
    const val ADD_EDIT_GIFT         = "add_edit_gift/{personId}?giftId={giftId}"
    const val ADD_EDIT_TASK         = "add_edit_task?personId={personId}&taskId={taskId}"
    const val ACTIVITY_FEED         = "activity_feed"
    const val ACTIVITY_DETAIL       = "activity_detail/{activityId}"
    const val INTERACTION_DETAIL    = "interaction_detail/{interactionId}"
    const val SEARCH                = "search"
    const val TASKS                 = "tasks"
    const val SETTINGS              = "settings"
    const val CONTACT_PICK          = "contact_pick"
    const val MERGE_CONTACTS        = "merge_contacts"

    fun personDetail(personId: String) = "person_detail/$personId"
    fun addEditPerson(personId: String? = null) =
        if (personId != null) "add_edit_person?personId=$personId" else "add_edit_person"
    fun addEditInteraction(personId: String, interactionId: String? = null) =
        "add_edit_interaction/$personId" + (interactionId?.let { "?interactionId=$it" } ?: "")
    fun addEditActivity(personId: String? = null, activityId: String? = null): String {
        val params = listOfNotNull(
            personId?.let { "personId=$it" },
            activityId?.let { "activityId=$it" },
        ).joinToString("&")
        return if (params.isEmpty()) "add_edit_activity" else "add_edit_activity?$params"
    }
    fun addEditNote(personId: String, noteId: String? = null) =
        "add_edit_note/$personId" + (noteId?.let { "?noteId=$it" } ?: "")
    fun addEditLifeEvent(personId: String, lifeEventId: String? = null) =
        "add_edit_life_event/$personId" + (lifeEventId?.let { "?lifeEventId=$it" } ?: "")
    fun addEditGift(personId: String, giftId: String? = null) =
        "add_edit_gift/$personId" + (giftId?.let { "?giftId=$it" } ?: "")
    fun addEditTask(personId: String? = null, taskId: String? = null): String {
        val params = listOfNotNull(
            personId?.let { "personId=$it" },
            taskId?.let { "taskId=$it" },
        ).joinToString("&")
        return if (params.isEmpty()) "add_edit_task" else "add_edit_task?$params"
    }

    fun activityDetail(activityId: String)       = "activity_detail/$activityId"
    fun interactionDetail(interactionId: String) = "interaction_detail/$interactionId"
}

private data class TopLevelDest(val route: String, val icon: ImageVector, val label: String)

private val TOP_LEVEL_DESTS = listOf(
    TopLevelDest(Routes.PEOPLE_LIST,   Icons.Default.People,      "People"),
    TopLevelDest(Routes.ACTIVITY_FEED, Icons.Default.DynamicFeed, "Feed"),
    TopLevelDest(Routes.SEARCH,        Icons.Default.Search,       "Search"),
    TopLevelDest(Routes.TASKS,         Icons.Default.Assignment,   "Tasks"),
)

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.PEOPLE_LIST,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute   = backStackEntry?.destination?.route
    val showBottomBar  = TOP_LEVEL_DESTS.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TOP_LEVEL_DESTS.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon  = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        }
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(bottom = outerPadding.calculateBottomPadding())
                .consumeWindowInsets(PaddingValues(bottom = outerPadding.calculateBottomPadding())),
        ) {

            composable(Routes.PEOPLE_LIST) {
                PeopleListScreen(
                    onAddPerson    = { navController.navigate(Routes.addEditPerson()) },
                    onOpenPerson   = { id -> navController.navigate(Routes.personDetail(id.toString())) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(
                route = Routes.PERSON_DETAIL,
                arguments = listOf(navArgument("personId") { type = NavType.StringType }),
            ) { back ->
                val personIdStr = back.arguments!!.getString("personId")!!
                val vm: PersonDetailViewModel = viewModel()
                vm.load(UUID.fromString(personIdStr))
                PersonDetailScreen(
                    viewModel         = vm,
                    onNavigateBack    = { navController.popBackStack() },
                    onEdit            = { navController.navigate(Routes.addEditPerson(personIdStr)) },
                    onAddInteraction  = { navController.navigate(Routes.addEditInteraction(personIdStr)) },
                    onAddActivity     = { navController.navigate(Routes.addEditActivity(personId = personIdStr)) },
                    onAddNote         = { navController.navigate(Routes.addEditNote(personIdStr)) },
                    onAddLifeEvent    = { navController.navigate(Routes.addEditLifeEvent(personIdStr)) },
                    onAddGift         = { navController.navigate(Routes.addEditGift(personIdStr)) },
                    onAddTask         = { navController.navigate(Routes.addEditTask(personId = personIdStr)) },
                    onEditInteraction = { id -> navController.navigate(Routes.addEditInteraction(personIdStr, id.toString())) },
                    onEditActivity    = { id -> navController.navigate(Routes.addEditActivity(activityId = id.toString())) },
                    onEditNote        = { id -> navController.navigate(Routes.addEditNote(personIdStr, id.toString())) },
                    onEditLifeEvent   = { id -> navController.navigate(Routes.addEditLifeEvent(personIdStr, id.toString())) },
                    onEditGift        = { id -> navController.navigate(Routes.addEditGift(personIdStr, id.toString())) },
                    onEditTask        = { id -> navController.navigate(Routes.addEditTask(personId = personIdStr, taskId = id.toString())) },
                )
            }

            composable(
                route = Routes.ADD_EDIT_PERSON,
                arguments = listOf(navArgument("personId") { nullable = true; defaultValue = null }),
            ) { back ->
                AddEditPersonScreen(
                    personId       = back.arguments?.getString("personId")?.let { UUID.fromString(it) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.ADD_EDIT_INTERACTION,
                arguments = listOf(
                    navArgument("personId")       { type = NavType.StringType },
                    navArgument("interactionId")  { nullable = true; defaultValue = null },
                ),
            ) { back ->
                AddEditInteractionScreen(
                    personId       = back.arguments!!.getString("personId")!!,
                    interactionId  = back.arguments?.getString("interactionId")?.let { UUID.fromString(it) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.ADD_EDIT_ACTIVITY,
                arguments = listOf(
                    navArgument("personId")    { nullable = true; defaultValue = null },
                    navArgument("activityId")  { nullable = true; defaultValue = null },
                ),
            ) { back ->
                AddEditActivityScreen(
                    personId       = back.arguments?.getString("personId"),
                    activityId     = back.arguments?.getString("activityId")?.let { UUID.fromString(it) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.ADD_EDIT_NOTE,
                arguments = listOf(
                    navArgument("personId") { type = NavType.StringType },
                    navArgument("noteId")   { nullable = true; defaultValue = null },
                ),
            ) { back ->
                AddEditNoteScreen(
                    personId       = back.arguments!!.getString("personId")!!,
                    noteId         = back.arguments?.getString("noteId")?.let { UUID.fromString(it) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.ADD_EDIT_LIFE_EVENT,
                arguments = listOf(
                    navArgument("personId")    { type = NavType.StringType },
                    navArgument("lifeEventId") { nullable = true; defaultValue = null },
                ),
            ) { back ->
                AddEditLifeEventScreen(
                    personId       = back.arguments!!.getString("personId")!!,
                    lifeEventId    = back.arguments?.getString("lifeEventId")?.let { UUID.fromString(it) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.ADD_EDIT_GIFT,
                arguments = listOf(
                    navArgument("personId") { type = NavType.StringType },
                    navArgument("giftId")   { nullable = true; defaultValue = null },
                ),
            ) { back ->
                AddEditGiftScreen(
                    personId       = back.arguments!!.getString("personId")!!,
                    giftId         = back.arguments?.getString("giftId")?.let { UUID.fromString(it) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.ADD_EDIT_TASK,
                arguments = listOf(
                    navArgument("personId") { nullable = true; defaultValue = null },
                    navArgument("taskId")   { nullable = true; defaultValue = null },
                ),
            ) { back ->
                AddEditTaskScreen(
                    personId       = back.arguments?.getString("personId"),
                    taskId         = back.arguments?.getString("taskId")?.let { UUID.fromString(it) },
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(Routes.ACTIVITY_FEED) {
                ActivityFeedScreen(
                    onAddActivity     = { navController.navigate(Routes.addEditActivity()) },
                    onViewActivity    = { id -> navController.navigate(Routes.activityDetail(id.toString())) },
                    onViewInteraction = { id -> navController.navigate(Routes.interactionDetail(id.toString())) },
                )
            }

            composable(
                route = Routes.ACTIVITY_DETAIL,
                arguments = listOf(navArgument("activityId") { type = NavType.StringType }),
            ) { back ->
                val activityIdStr = back.arguments!!.getString("activityId")!!
                val vm: ActivityDetailViewModel = viewModel()
                ActivityDetailScreen(
                    activityId    = UUID.fromString(activityIdStr),
                    onNavigateBack = { navController.popBackStack() },
                    onEdit         = { id -> navController.navigate(Routes.addEditActivity(activityId = id.toString())) },
                    onOpenPerson   = { id -> navController.navigate(Routes.personDetail(id.toString())) },
                    viewModel      = vm,
                )
            }

            composable(
                route = Routes.INTERACTION_DETAIL,
                arguments = listOf(navArgument("interactionId") { type = NavType.StringType }),
            ) { back ->
                val interactionIdStr = back.arguments!!.getString("interactionId")!!
                val vm: InteractionDetailViewModel = viewModel()
                InteractionDetailScreen(
                    interactionId  = UUID.fromString(interactionIdStr),
                    onNavigateBack = { navController.popBackStack() },
                    onOpenPerson   = { id -> navController.navigate(Routes.personDetail(id.toString())) },
                    viewModel      = vm,
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenPerson      = { id -> navController.navigate(Routes.personDetail(id.toString())) },
                    onOpenActivity    = { id -> navController.navigate(Routes.activityDetail(id.toString())) },
                    onOpenInteraction = { id -> navController.navigate(Routes.interactionDetail(id.toString())) },
                    onOpenSettings    = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(Routes.TASKS) {
                TasksScreen(
                    onAddTask = { navController.navigate(Routes.addEditTask()) },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateBack        = { navController.popBackStack() },
                    onNavigateMerge       = { navController.navigate(Routes.MERGE_CONTACTS) },
                    onNavigateContactPick = { navController.navigate(Routes.CONTACT_PICK) },
                )
            }

            composable(Routes.CONTACT_PICK) {
                val settingsEntry = remember(navController) {
                    navController.getBackStackEntry(Routes.SETTINGS)
                }
                val vm: SettingsViewModel = viewModel(settingsEntry)
                ContactPickScreen(
                    viewModel      = vm,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable(Routes.MERGE_CONTACTS) {
                MergeContactsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
