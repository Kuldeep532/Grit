/*
 * Copyright (C) 2026  Shubham Gorai
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.shub39.grit.shared.ui.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.shub39.grit.core.settings.Sections
import com.shub39.grit.shared.ui.LocalWindowSizeClass
import com.shub39.grit.shared.ui.WindowSize.Companion.isWidthCompact
import com.shub39.grit.shared.ui.app.MainAppState
import com.shub39.grit.shared.ui.habit.HabitsAction
import com.shub39.grit.shared.ui.habit.ui.sections.AnalyticsPage
import com.shub39.grit.shared.ui.habit.ui.sections.Calendar
import com.shub39.grit.shared.ui.habit.ui.sections.CalendarHeatMap
import com.shub39.grit.shared.ui.habit.ui.sections.HabitsList
import com.shub39.grit.shared.ui.habit.ui.sections.OverallAnalytics
import com.shub39.grit.shared.ui.setting.ui.section.About
import com.shub39.grit.shared.ui.setting.ui.section.BackupPage
import com.shub39.grit.shared.ui.setting.ui.section.Changelog
import com.shub39.grit.shared.ui.setting.ui.section.LookAndFeelPage
import com.shub39.grit.shared.ui.setting.ui.section.LegalInfoPage
import com.shub39.grit.shared.ui.setting.ui.section.RootPage
import com.shub39.grit.shared.ui.task.ui.TasksPage
import com.shub39.grit.shared.ui.theme.GritTheme
import com.shub39.grit.shared.ui.viewmodel.HabitViewModel
import com.shub39.grit.shared.ui.viewmodel.MainViewModel
import com.shub39.grit.shared.ui.viewmodel.SettingsViewModel
import com.shub39.grit.shared.ui.viewmodel.TasksViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GritNavDisplay(
    modifier: Modifier = Modifier,
    paywall: @Composable (Boolean, () -> Unit) -> Unit,
) {
    val globalVM = koinViewModel<MainViewModel>()
    val globalState by globalVM.state.collectAsStateWithLifecycle()

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val topLevelBackStack =
        retain(globalState.startingSection) {
            TopLevelBackStack(
                startKey =
                    when (globalState.startingSection) {
                        Sections.Tasks -> TaskList
                        Sections.Habits -> HabitsList
                    }
            )
        }

    CompositionLocalProvider(LocalWindowSizeClass provides adaptiveInfo.windowSizeClass) {
        GritTheme(theme = globalState.theme) {
            SharedTransitionLayout(modifier = modifier.background(colorScheme.background)) {
                val windowSizeClass = LocalWindowSizeClass.current

                val listDetailSceneStrategy =
                    remember(windowSizeClass) { ListDetailSceneStrategy<Routes>(windowSizeClass) }
                val twoPaneSceneStrategy =
                    remember(windowSizeClass) { TwoPaneSceneStrategy<Routes>(windowSizeClass) }
                val responsiveNavigationSceneDecoratorStrategy =
                    rememberResponsiveNavigationSceneDecoratorStrategy<Routes>(
                        navBar = {
                            NavBar(
                                currentRoute = topLevelBackStack.backStack.lastOrNull(),
                                onNavigate = { topLevelBackStack.add(it) },
                            )
                        },
                        navRail = {
                            NavRail(
                                currentRoute = topLevelBackStack.backStack.lastOrNull(),
                                onNavigate = { topLevelBackStack.add(it) },
                            )
                        },
                        sharedTransitionScope = this,
                    )

                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    NavDisplay(
                        backStack = topLevelBackStack.backStack,
                        sharedTransitionScope = this,
                        sceneDecoratorStrategies =
                            listOf(responsiveNavigationSceneDecoratorStrategy),
                        sceneStrategies = listOf(twoPaneSceneStrategy, listDetailSceneStrategy),
                        onBack = { topLevelBackStack.removeLast() },
                        entryProvider =
                            entryProvider {
                                entry<TaskList>(metadata = fadeTransitionMetadata()) {
                                    val viewModel = koinViewModel<TasksViewModel>()
                                    val state by viewModel.state.collectAsStateWithLifecycle()

                                    TasksPage(
                                        state = state,
                                        onAction = viewModel::onAction,
                                        isPlusUser = globalState.isUserSubscribed,
                                        onOpenPaywall = { topLevelBackStack.add(Paywall) },
                                    )
                                }

                                habitsScreens(
                                    topLevelBackStack = topLevelBackStack,
                                    globalState = globalState,
                                    isExpanded = !windowSizeClass.isWidthCompact(),
                                )

                                entry<Paywall>(metadata = verticalTransitionMetadata()) {
                                    LaunchedEffect(Unit) { globalVM.trackPaywallOpened() }
                                    paywall(globalState.isUserSubscribed) {
                                        globalVM.updateSubscription()
                                        topLevelBackStack.removeLast()
                                    }
                                }

                                settingsScreens(
                                    topLevelBackStack = topLevelBackStack,
                                    globalState = globalState,
                                )
                            },
                    )
                }
            }
        }
    }
}

private fun EntryProviderScope<Routes>.habitsScreens(
    topLevelBackStack: TopLevelBackStack,
    globalState: MainAppState,
    isExpanded: Boolean,
) {
    entry<HabitsList>(metadata = ListDetailScene.listPane() + fadeTransitionMetadata()) {
        val viewModel = koinViewModel<HabitViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) { viewModel.onAction(OnHabitsOpened) }

        HabitsList(
            state = state,
            onAction = viewModel::onAction,
            onNavigateToAnalytics = { topLevelBackStack.add(HabitAnalytics) },
            onNavigateToPaywall = { topLevelBackStack.add(Paywall) },
            onNavigateToOverallAnalytics = { topLevelBackStack.add(OverallAnalytics) },
            isUserSubscribed = globalState.isUserSubscribed,
            modifier = Modifier.background(colorScheme.background),
        )
    }

    entry<HabitAnalytics>(
        metadata =
            TwoPaneScene.twoPane() + ListDetailScene.detailPane() + horizontalTransitionMetadata()
    ) {
        val viewModel = koinViewModel<HabitViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        AnalyticsPage(
            state = state,
            onAction = viewModel::onAction,
            onNavigateBack = { topLevelBackStack.removeLast() },
            onNavigateToPaywall = { topLevelBackStack.add(Paywall) },
            onNavigateToCalendar = { topLevelBackStack.add(Calendar) },
            isUserSubscribed = globalState.isUserSubscribed,
            modifier = Modifier.background(colorScheme.background),
        )
    }

    entry<Calendar>(metadata = TwoPaneScene.twoPane() + horizontalTransitionMetadata()) {
        val viewModel = koinViewModel<HabitViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        Calendar(
            state = state,
            onNavigateBack = { topLevelBackStack.removeLast() },
            onDateClick = { habit, date, value ->
                if (value != null) {
                    viewModel.onAction(HabitsAction.InsertMeasuredStatus(habit, date, value))
                } else {
                    viewModel.onAction(HabitsAction.InsertStatus(habit, date))
                }
            },
            modifier = Modifier.background(colorScheme.background),
        )
    }

    entry<OverallAnalytics>(
        metadata =
            TwoPaneScene.twoPane() +
                ListDetailScene.detailPane() +
                if (isExpanded) {
                    horizontalTransitionMetadata()
                } else {
                    verticalTransitionMetadata()
                }
    ) {
        val viewModel = koinViewModel<HabitViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) { viewModel.onAction(OnOverallAnalyticsViewed) }

        OverallAnalytics(
            state = state,
            onAction = viewModel::onAction,
            onNavigateBack = { topLevelBackStack.removeLast() },
            onNavigateToPaywall = { topLevelBackStack.add(Paywall) },
            isUserSubscribed = globalState.isUserSubscribed,
            onNavigateToCalendarHeatMap = { topLevelBackStack.add(CalendarHeatMap) },
            modifier = Modifier.background(colorScheme.background),
        )
    }

    entry<CalendarHeatMap>(metadata = TwoPaneScene.twoPane() + horizontalTransitionMetadata()) {
        val viewModel = koinViewModel<HabitViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        CalendarHeatMap(
            state = state,
            onNavigateBack = { topLevelBackStack.removeLast() },
            onChangeSelectedDay = {
                viewModel.onAction(HabitsAction.FetchCompletedHabitsForDate(it))
            },
            modifier = Modifier.background(colorScheme.background),
        )
    }
}

private fun EntryProviderScope<Routes>.settingsScreens(
    topLevelBackStack: TopLevelBackStack,
    globalState: MainAppState,
) {
    entry<SettingsHome>(metadata = TwoPaneScene.twoPane() + fadeTransitionMetadata()) {
        val viewModel = koinViewModel<SettingsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) { viewModel.onAction(OnSettingsOpened) }

        RootPage(
            modifier = Modifier.background(colorScheme.background),
            state = state,
            onAction = viewModel::onAction,
            onNavigateToLookAndFeel = { topLevelBackStack.add(LookAndFeel) },
            onNavigateToBackup = { topLevelBackStack.add(Backup) },
            onNavigateToPaywall = { topLevelBackStack.add(Paywall) },
            onNavigateToChangelog = { topLevelBackStack.add(Changelog) },
            onNavigateToAppInfo = { topLevelBackStack.add(About) },
        )
    }

    entry<LookAndFeel>(metadata = TwoPaneScene.twoPane() + horizontalTransitionMetadata()) {
        val viewModel = koinViewModel<SettingsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        LookAndFeelPage(
            state = state,
            onAction = viewModel::onAction,
            isUserSubscribed = globalState.isUserSubscribed,
            onNavigateToPaywall = { topLevelBackStack.add(Paywall) },
            onNavigateBack = { topLevelBackStack.removeLast() },
            modifier = Modifier.background(colorScheme.background),
        )
    }

    entry<Backup>(metadata = TwoPaneScene.twoPane() + horizontalTransitionMetadata()) {
        val viewModel = koinViewModel<SettingsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        BackupPage(
            modifier = Modifier.background(colorScheme.background),
            state = state,
            onAction = viewModel::onAction,
            onNavigateBack = { topLevelBackStack.removeLast() },
        )
    }

    entry<About>(metadata = TwoPaneScene.twoPane() + horizontalTransitionMetadata()) {
        val viewModel = koinViewModel<SettingsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) { viewModel.onAction(OnAboutViewed) }

        About(
            modifier = Modifier.background(colorScheme.background),
            versionName = state.currentVersion ?: "1.0.00-Demo",
            onNavigateBack = { topLevelBackStack.removeLast() },
            onNavigateToPrivacy = { topLevelBackStack.add(PrivacyPolicy) },
            onNavigateToTerms = { topLevelBackStack.add(TermsAndConditions) },
        )
    }

    entry<PrivacyPolicy>(metadata = TwoPaneScene.twoPane() + horizontalTransitionMetadata()) {
        LegalInfoPage(
            title = "Privacy Policy",
            onNavigateBack = { topLevelBackStack.removeLast() },
            sections = listOf(
                "Who operates the app" to "Nexus Life is developed by Kuldeep Kumar Yadav and operated by Kuldeep Digital Services, an independent individual-operated name and not a registered company.",
                "Information stored on your device" to "Tasks, habits, reminders, preferences and related app content are stored locally on your device. Nexus Life does not require you to create an account. If you export a backup, the exported file is saved or shared only through the destination you choose.",
                "Notifications, voice reminders and Charging Focus" to "If enabled, reminders and Charging Focus prompts are scheduled by Android on your device. Voice Announcements use Android's installed text-to-speech service and do not need microphone access. If you create a Charging Focus emergency PIN, the app stores a salted password hash on your device; it does not upload the PIN. Voice processing is handled by the speech service configured on your phone.",
                "Analytics in the Google Play version" to "The Google Play version uses PostHog to record app usage events, app version, event time, subscription status and event-specific settings or action details to understand app usage and improve the experience. The free and open-source FOSS version does not send these analytics events. PostHog may process technical information according to its own privacy policy.",
                "Subscriptions and payments" to "If subscriptions are offered in the Google Play version, purchases are handled through Google Play and RevenueCat. Payment card and bank details are handled by the payment provider, not stored by Nexus Life. Purchase and entitlement information may be processed to verify subscription access.",
                "Information sharing" to "Nexus Life does not sell your personal information. Data may be processed by the service providers described above when you use the corresponding Play version features. Your phone's Android system and installed text-to-speech provider may also process information needed to provide their services.",
                "Your choices" to "You can disable reminders and voice announcements in Settings. You can choose whether to use the Play or FOSS distribution. You can avoid sharing exported backup files. Uninstalling the app removes its locally stored app data unless your device has separately backed it up.",
                "Children and sensitive information" to "Nexus Life is a general productivity app and is not designed to collect sensitive personal information. Please do not enter passwords, financial account details or other highly sensitive information into task or habit descriptions.",
                "Changes and contact" to "This policy may be updated when app features or service providers change. For privacy questions, use the contact or issue options on the official project page: https://github.com/Kuldeep532/Grit."
            ),
        )
    }

    entry<TermsAndConditions>(metadata = TwoPaneScene.twoPane() + horizontalTransitionMetadata()) {
        LegalInfoPage(
            title = "Terms & Conditions",
            onNavigateBack = { topLevelBackStack.removeLast() },
            sections = listOf(
                "Acceptance" to "By installing or using Nexus Life, you agree to these terms. If you do not agree, please stop using the app.",
                "Who provides Nexus Life" to "Nexus Life is developed by Kuldeep Kumar Yadav and operated by Kuldeep Digital Services as an independent individual-operated name. It is not represented as a registered company.",
                "Purpose of the app" to "Nexus Life provides tools for personal task planning, habit tracking, reminders, analytics and backup. You are responsible for the accuracy of the information you enter and for deciding how to use reminders and other features.",
                "No professional advice" to "Nexus Life is a personal organisation tool. It does not provide medical, mental health, legal, financial or emergency services. Do not rely on it as a substitute for professional advice or urgent assistance.",
                "Your data and backups" to "You are responsible for keeping your device secure and maintaining copies of important exported backups. The app cannot guarantee that data will remain available after device loss, app removal, operating system changes or storage failure.",
                "Notifications, Charging Focus and device compatibility" to "Reminder delivery and Charging Focus prompts depend on Android settings, battery restrictions and notification permissions. Charging Focus shows a black screen only inside its own app screen; it does not lock the whole phone, block other apps or replace the phone's emergency features. Android navigation and system controls remain available. Voice announcements also depend on speech voices installed on your device. Delivery cannot be guaranteed in every circumstance.",
                "Open-source license" to "Nexus Life is based on Grit by Shubham Gorai and is distributed under the GNU General Public License version 3. The applicable open-source license governs your rights to use, study, modify and redistribute the covered software. License notices and source credits must be preserved.",
                "Subscriptions" to "Where a paid subscription is offered, its price, renewal, cancellation and refund terms are presented by Google Play at the time of purchase. Google Play and the applicable payment providers process the transaction.",
                "Acceptable use" to "Do not use the app to violate laws, infringe another person's rights, interfere with the app or attempt to misuse its services.",
                "Availability and changes" to "Features may be changed, suspended or discontinued. The app is provided on an as-available basis, to the extent permitted by applicable law. Nothing in these terms excludes rights that cannot legally be excluded.",
                "Contact" to "For questions about these terms, use the contact or issue options on the official project page: https://github.com/Kuldeep532/Grit."
            ),
        )
    }

    entry<Changelog>(metadata = TwoPaneScene.twoPane() + horizontalTransitionMetadata()) {
        val viewModel = koinViewModel<SettingsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) { viewModel.onAction(OnChangelogViewed) }

        Changelog(
            modifier = Modifier.background(colorScheme.background),
            changelog = state.changelog,
            onNavigateBack = { topLevelBackStack.removeLast() },
        )
    }
}
