package dev.sshtunnelexporter.datagrip.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Model-level functional test for the two entry-point actions, on a real (headless) platform.
 *
 * Note the JUnit3 convention required by [BasePlatformTestCase]: methods must be named `testXxx`
 * (the runner discovers them reflectively, so the backtick names used by the pure JUnit4 tests
 * would silently not run here).
 *
 * Scope is the action *contract* that needs the platform but not a live data source:
 * update()/enablement and the empty-candidates path. The positive path (a configured
 * LocalDataSource opening the dialog) needs a heavy DB fixture + shows Swing UI and is left
 * to integration testing.
 */
class ExportSshActionsTest : BasePlatformTestCase() {

    private val fromDbView = ExportSshFromDbViewAction()
    private val fromTools = ExportSshFromToolsAction()

    fun testBothActionsUpdateOnBackgroundThread() {
        assertEquals(ActionUpdateThread.BGT, fromDbView.actionUpdateThread)
        assertEquals(ActionUpdateThread.BGT, fromTools.actionUpdateThread)
    }

    fun testDbViewActionHiddenWhenNothingSelected() {
        // Project in context but no PSI_ELEMENT -> no data source selected -> hidden & disabled.
        val e = TestActionEvent.createTestEvent(fromDbView, SimpleDataContext.getProjectContext(project))
        fromDbView.update(e)
        assertFalse(e.presentation.isEnabledAndVisible)
    }

    fun testToolsActionEnabledWhenProjectPresent() {
        val e = TestActionEvent.createTestEvent(fromTools, SimpleDataContext.getProjectContext(project))
        fromTools.update(e)
        assertTrue(e.presentation.isEnabled)
    }

    fun testToolsActionDisabledWithoutProject() {
        val e = TestActionEvent.createTestEvent(fromTools, SimpleDataContext.EMPTY_CONTEXT)
        fromTools.update(e)
        assertFalse(e.presentation.isEnabled)
    }

    fun testToolsActionWithoutProjectDoesNothing() {
        // No project in context -> the `e.project ?: return` guard fires before any data-source
        // lookup, so the action is a safe no-op (no exception).
        //
        // The populated path (DbPsiFacade.getInstance(project).dataSources) is deliberately NOT
        // covered here: in a light fixture DbPsiFacade.getInstance returns null, because the
        // com.intellij.database project model is not set up. Exercising it needs a heavy DB
        // fixture or an integration test -- exactly the boundary the JetBrains testing docs draw.
        val e = TestActionEvent.createTestEvent(fromTools, SimpleDataContext.EMPTY_CONTEXT)
        fromTools.actionPerformed(e)
    }
}
