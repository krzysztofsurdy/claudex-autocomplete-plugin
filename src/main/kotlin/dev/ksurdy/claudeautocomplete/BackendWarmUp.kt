package dev.ksurdy.claudeautocomplete

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class BackendWarmUp : ProjectActivity {
    override suspend fun execute(project: Project) {
        BackendService.getInstance().warmUpIfActive()
    }
}
