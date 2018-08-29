// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Consumer
import software.aws.toolkits.core.credentials.CredentialProviderNotFound
import software.aws.toolkits.core.credentials.ToolkitCredentialsProvider
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.jetbrains.core.credentials.CredentialManager
import software.aws.toolkits.jetbrains.core.credentials.ProjectAccountSettingsManager
import software.aws.toolkits.jetbrains.core.credentials.ProjectAccountSettingsManager.AccountSettingsChangedNotifier
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.resources.message
import java.awt.event.MouseEvent
import javax.swing.JComponent

class AwsSettingsPanelInstaller : StartupActivity {
    override fun runActivity(project: Project) {
        WindowManager.getInstance().getStatusBar(project).addWidget(AwsSettingsPanel(project), project)
    }
}

private class AwsSettingsPanel(private val project: Project) : StatusBarWidget,
    StatusBarWidget.MultipleTextValuesPresentation,
    AccountSettingsChangedNotifier {
    private val accountSettingsManager = ProjectAccountSettingsManager.getInstance(project)
    private var statusBar: StatusBar? = null

    @Suppress("FunctionName")
    override fun ID(): String = "AwsSettingsPanel"

    override fun getTooltipText() = message("settings.title")

    override fun getSelectedValue(): String {
        val statusLine = try {
            val displayName = accountSettingsManager.activeCredentialProvider.displayName
            "$displayName@${accountSettingsManager.activeRegion.name}"
        } catch (_: CredentialProviderNotFound) {
            // TODO: Need to better handle the case where they have no valid profile selected
            message("settings.credentials.none_selected")
        }

        return "AWS: $statusLine"
    }

    @Suppress("OverridingDeprecatedMember") // No choice, part of interface contract with no default
    override fun getMaxValue(): String {
        TODO("not implemented")
    }

    override fun getPopupStep(): ListPopup? {
        val dataContext = DataManager.getInstance().getDataContext(statusBar!!.component)
        return JBPopupFactory.getInstance().createActionGroupPopup(
            tooltipText,
            ChangeAccountSettingsAction(accountSettingsManager).createPopupActionGroup(),
            dataContext,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            true
        )
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? = null

    override fun getPresentation(type: StatusBarWidget.PlatformType) = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        project.messageBus.connect().subscribe(ProjectAccountSettingsManager.ACCOUNT_SETTINGS_CHANGED, this)
        updateWidget()
    }

    override fun activeRegionChanged(value: AwsRegion) {
        updateWidget()
    }

    override fun activeCredentialsChanged(credentialsProvider: ToolkitCredentialsProvider) {
        updateWidget()
    }

    private fun updateWidget() {
        statusBar?.updateWidget(ID())
    }

    override fun dispose() {
        ApplicationManager.getApplication().invokeLater({ statusBar?.removeWidget(ID()) }, { project.isDisposed })
    }
}

private class ChangeAccountSettingsAction(private val accountSettingsManager: ProjectAccountSettingsManager) :
    ComboBoxAction(), DumbAware {
    fun createPopupActionGroup(): DefaultActionGroup {
        return createPopupActionGroup(null)
    }

    override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
        val group = DefaultActionGroup()

        val usedRegions = accountSettingsManager.recentlyUsedRegions()
        if (usedRegions.isEmpty()) {
            group.addAll(createShowAllRegions())
        } else {
            group.addSeparator(message("settings.regions.recent"))
            usedRegions.forEach {
                group.add(ChangeRegionAction(it))
            }
            group.add(createShowAllRegions())
        }

        val usedCredentials = accountSettingsManager.recentlyUsedCredentials()
        if (usedCredentials.isEmpty()) {
            group.addSeparator(message("settings.credentials"))
            group.addAll(createShowAllCredentials())
        } else {
            group.addSeparator(message("settings.credentials.recent"))
            usedCredentials.forEach {
                group.add(ChangeCredentialsAction(it))
            }
            group.add(createShowAllCredentials())
        }

        return group
    }

    private fun createShowAllRegions(): ActionGroup {
        val regions = AwsRegionProvider.getInstance().regions().values.groupBy { it.category }
        val showAll = DefaultActionGroup(message("settings.regions.region_sub_menu"), true)

        regions.forEach { (category, subRegions) ->
            showAll.addSeparator(category)
            subRegions.forEach {
                showAll.add(ChangeRegionAction(it))
            }
        }

        return showAll
    }

    private fun createShowAllCredentials(): ActionGroup {
        val credentialManager = CredentialManager.getInstance()
        val showAll = DefaultActionGroup(message("settings.credentials.profile_sub_menu"), true)

        credentialManager.getCredentialProviders().forEach {
            showAll.add(ChangeCredentialsAction(it))
        }

        return showAll
    }
}

private class ChangeRegionAction(val region: AwsRegion) : AnAction(region.displayName), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val accountSettingsManager = ProjectAccountSettingsManager.getInstance(e.getRequiredData(DataKeys.PROJECT))
        accountSettingsManager.activeRegion = region
    }
}

private class ChangeCredentialsAction(val credentialsProvider: ToolkitCredentialsProvider) :
    AnAction(credentialsProvider.displayName), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val accountSettingsManager = ProjectAccountSettingsManager.getInstance(e.getRequiredData(DataKeys.PROJECT))
        accountSettingsManager.activeCredentialProvider = credentialsProvider
    }
}