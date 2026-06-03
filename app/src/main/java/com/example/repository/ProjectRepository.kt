package com.example.repository

import com.example.database.ProjectDao
import com.example.database.SavedProject
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {

    val allProjects: Flow<List<SavedProject>> = projectDao.getAllProjects()

    suspend fun saveProject(project: SavedProject) {
        projectDao.insertProject(project)
    }

    suspend fun deleteProject(id: String) {
        projectDao.deleteProjectById(id)
    }

    suspend fun clearProjects() {
        projectDao.clearAllProjects()
    }
}
