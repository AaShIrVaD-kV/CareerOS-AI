package com.example.data.database

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// ==========================================
// ROOM ENTITIES
// ==========================================

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val education: String,
    val skillsRaw: String, // Comma-separated list
    val projectsRaw: String, // JSON serialization of List<Project>
    val certificationsRaw: String, // Comma-separated or JSON
    val preferredLocationsRaw: String, // Comma-separated
    val careerInterestsRaw: String // Comma-separated
)

@Entity(tableName = "job_scans")
data class JobScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobTitle: String,
    val company: String,
    val location: String,
    val jdText: String,
    val matchScore: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val summary: String,
    val matchingSkillsRaw: String,  // Comma-separated
    val missingSkillsRaw: String,   // Comma-separated
    val atsKeywordsRaw: String,     // Comma-separated
    val resumeImprovementsRaw: String, // Bullet points separated by | or JSON
    val customResumeDraft: String, // Custom text proposed
    val coverLetterDraft: String,  // String text block
    val interviewQuestionsRaw: String, // JSON serialization of List<InterviewQuestion>
    val recommendedLearningJson: String // String JSON
)

@Entity(tableName = "study_tasks")
data class StudyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String, // Python, SQL, Excel, General, Daily
    val durationType: String, // 7_DAYS, 30_DAYS, DAILY
    val isCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "curated_jobs")
data class CuratedJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobTitle: String,
    val company: String,
    val location: String,
    val category: String, // Perfect Match, Good Match, Stretch Opportunity
    val matchScore: Int,
    val whyMatches: String,
    val skillGaps: String, // Comma-separated
    val appLink: String,
    val jdText: String
)

@Entity(tableName = "job_applications")
data class JobApplicationEntity(
    @PrimaryKey(autoGenerate = true) val applicationId: Long = 0,
    val appliedDate: String,
    val company: String,
    val role: String,
    val location: String,
    val source: String,
    val jobLink: String,
    val matchScore: Int,
    val resumeVersion: String,
    val status: String, // Saved, Applied, Assessment, Interview, Rejected, Offer, Withdrawn
    val notes: String,
    val lastUpdate: Long = System.currentTimeMillis()
)

@Entity(tableName = "resumes")
data class ResumeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val version: Int,
    val rawText: String,
    val skillsRaw: String,
    val educationRaw: String,
    val experienceRaw: String,
    val certificationsRaw: String,
    val projectsRaw: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "resume_job_analyses")
data class ResumeJobAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val resumeId: Long,
    val resumeVersion: Int,
    val jobTitle: String,
    val companyName: String,
    val jobUrl: String,
    val jobDescriptionText: String,
    val jobPostingContent: String,
    val matchScore: Int,
    val skillsMatchScore: Int,
    val keywordMatchScore: Int,
    val educationMatch: String,
    val experienceMatch: String,
    val missingSkillsRaw: String,
    val missingKeywordsRaw: String,
    val strengthsRaw: String,
    val weaknessesRaw: String,
    val interviewReadinessScore: Int,
    val timestamp: Long = System.currentTimeMillis()
)

// ==========================================
// TYPE CONVERTERS
// ==========================================

class Converters {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        if (value == null) return null
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        val adapter = moshi.adapter<List<String>>(type)
        return adapter.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        val type = Types.newParameterizedType(List::class.java, String::class.java)
        val adapter = moshi.adapter<List<String>>(type)
        return adapter.fromJson(value)
    }
}

// ==========================================
// ROOM DAOS
// ==========================================

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfileSync(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)
}

@Dao
interface JobScanDao {
    @Query("SELECT * FROM job_scans ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<JobScanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: JobScanEntity): Long

    @Query("DELETE FROM job_scans WHERE id = :id")
    suspend fun deleteScan(id: Long)
}

@Dao
interface StudyTaskDao {
    @Query("SELECT * FROM study_tasks ORDER BY timestamp DESC")
    fun getAllTasks(): Flow<List<StudyTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: StudyTaskEntity): Long

    @Update
    suspend fun updateTask(task: StudyTaskEntity)

    @Query("DELETE FROM study_tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM study_tasks WHERE durationType = :type")
    suspend fun clearTasksByType(type: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<StudyTaskEntity>)
}

@Dao
interface CuratedJobDao {
    @Query("SELECT * FROM curated_jobs ORDER BY matchScore DESC")
    fun getCuratedJobs(): Flow<List<CuratedJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobs(jobs: List<CuratedJobEntity>)
}

@Dao
interface JobApplicationDao {
    @Query("SELECT * FROM job_applications ORDER BY lastUpdate DESC")
    fun getAllApplications(): Flow<List<JobApplicationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApplication(app: JobApplicationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<JobApplicationEntity>)

    @Update
    suspend fun updateApplication(app: JobApplicationEntity)

    @Query("DELETE FROM job_applications WHERE applicationId = :id")
    suspend fun deleteApplication(id: Long)

    @Query("SELECT * FROM job_applications WHERE company = :company AND role = :role LIMIT 1")
    suspend fun findDuplicate(company: String, role: String): JobApplicationEntity?

    @Query("SELECT * FROM job_applications WHERE company = :company LIMIT 1")
    suspend fun findFirstByCompany(company: String): JobApplicationEntity?
}

@Dao
interface ResumeDao {
    @Query("SELECT * FROM resumes ORDER BY timestamp DESC")
    fun getAllResumes(): Flow<List<ResumeEntity>>

    @Query("SELECT * FROM resumes WHERE id = :id LIMIT 1")
    suspend fun getResumeById(id: Long): ResumeEntity?

    @Query("SELECT * FROM resumes ORDER BY version DESC")
    suspend fun getAllResumesSync(): List<ResumeEntity>

    @Query("SELECT MAX(version) FROM resumes")
    suspend fun getMaxVersionSync(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResume(resume: ResumeEntity): Long

    @Query("DELETE FROM resumes WHERE id = :id")
    suspend fun deleteResume(id: Long)
    
    @Query("DELETE FROM resumes")
    suspend fun deleteAll()
}

@Dao
interface ResumeJobAnalysisDao {
    @Query("SELECT * FROM resume_job_analyses ORDER BY timestamp DESC")
    fun getAllAnalyses(): Flow<List<ResumeJobAnalysisEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: ResumeJobAnalysisEntity): Long

    @Query("DELETE FROM resume_job_analyses WHERE id = :id")
    suspend fun deleteAnalysis(id: Long)

    @Query("DELETE FROM resume_job_analyses")
    suspend fun deleteAll()
}

// ==========================================
// APPDATABASE
// ==========================================

@Database(
    entities = [
        UserProfileEntity::class,
        JobScanEntity::class,
        StudyTaskEntity::class,
        CuratedJobEntity::class,
        JobApplicationEntity::class,
        ResumeEntity::class,
        ResumeJobAnalysisEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun jobScanDao(): JobScanDao
    abstract fun studyTaskDao(): StudyTaskDao
    abstract fun curatedJobDao(): CuratedJobDao
    abstract fun jobApplicationDao(): JobApplicationDao
    abstract fun resumeDao(): ResumeDao
    abstract fun resumeJobAnalysisDao(): ResumeJobAnalysisDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "careeros_ai_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabasePrepopulationCallback(context))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabasePrepopulationCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                val database = getDatabase(context)
                
                // Prepopulate user profile
                val initialProfile = UserProfileEntity(
                    id = 1,
                    name = "Aashirvad K Varghese",
                    education = "B.Com Accounts and Data Science",
                    skillsRaw = "Python, Pandas, NumPy, Machine Learning, Data Analysis, Data Visualization, Excel, SQL, Streamlit, Scikit-learn, Statistics",
                    projectsRaw = """
                        [
                          {
                            "title": "Resume AI – ATS Resume Analyzer",
                            "description": "An automated candidate screening web app using NLP to analyze metrics, parse resumes, and score alignment."
                          },
                          {
                            "title": "Salary Predictor",
                            "description": "Predictive modeling application leveraging multiple regression algorithms to forecast industry salaries based on credentials."
                          },
                          {
                            "title": "Fraud Detection System",
                            "description": "A Machine Learning pipeline analyzing transactional logs to identify anomalies and flag potential financial breaches."
                          },
                          {
                            "title": "Weather Forecasting System",
                            "description": "Time-series predictive analyzer utilizing statistical methods to compute thermal and humidity fluctuations."
                          },
                          {
                            "title": "Emergency Triage Predictor",
                            "description": "Operational care classification engine to expedite high-risk patient routing based on physiological telemetry."
                          }
                        ]
                    """.trimIndent(),
                    certificationsRaw = "Data Science & Machine Learning related certifications, Forage Virtual Internships",
                    preferredLocationsRaw = "India, Qatar, UAE, Saudi Arabia, Oman, Kuwait, Bahrain, Remote",
                    careerInterestsRaw = "Data Analyst, Associate Analyst, Reporting Analyst, MIS Analyst, Data Operations Analyst, Junior Data Scientist, Business Intelligence Analyst, Research Analyst"
                )
                database.userProfileDao().insertProfile(initialProfile)

                // Prepopulate Curated Jobs
                val initialJobs = listOf(
                    CuratedJobEntity(
                        jobTitle = "Junior Data Analyst",
                        company = "Saudi Aramco",
                        location = "Dhahran, Saudi Arabia (Hybrid)",
                        category = "Perfect Match",
                        matchScore = 95,
                        whyMatches = "Requires Python, SQL, Excel and Data Visualization. Candidate fits beautifully with their B.Com Data Science background and strong financial fraud analytic projects.",
                        skillGaps = "Tableau (Recommended), Power BI (Recommended)",
                        appLink = "https://www.aramco.jobs",
                        jdText = "Saudi Aramco is seeking a Junior Accounting & Data Analyst. Responsibilities: clean, structure and investigate corporate accounting ledgers, analyze transaction anomalies, compile executive compliance dashboards. Mandatory: Python (Pandas/NumPy), SQL databases, Microsoft Excel. Highly preferred: Tableau or Power BI."
                    ),
                    CuratedJobEntity(
                        jobTitle = "Business Intelligence Associate",
                        company = "Qatar National Bank (QNB)",
                        location = "Doha, Qatar",
                        category = "Good Match",
                        matchScore = 85,
                        whyMatches = "Capitalizes heavily on corporate reporting, Excel modeling, and SQL database querying. Perfect fit for B.Com Accounts & Data Science graduate.",
                        skillGaps = "Advanced SQL (Recommended), Snowflake (Recommended)",
                        appLink = "https://www.qnb.com/careers",
                        jdText = "QNB is hiring a Business Intelligence Associate to join our central MIS team. Responsibilities: generate daily and monthly commercial reports using SQL & Excel, track banking product metrics, automate manual reporting processes, support analytical modeling with Python. Requirements: Bachelor's degree (B.Com/B.Sc) with numeric focus, fluency in Python, standard SQL proficiency."
                    ),
                    CuratedJobEntity(
                        jobTitle = "Junior Data Scientist",
                        company = "Emirates Group",
                        location = "Dubai, UAE",
                        category = "Stretch Opportunity",
                        matchScore = 72,
                        whyMatches = "Leverages Machine Learning algorithms, Scikit-learn models, and predictive analytics. Highly aligns with candidate's Emergency Triage Predictor project.",
                        skillGaps = "AWS SageMaker (Recommended), Docker containers (Recommended)",
                        appLink = "https://www.emiratesgroupcareers.com",
                        jdText = "Emirates Group is looking for an ambitious Junior Data Scientist to Join aviation operations optimization. Responsibilities: train, test, and deploy predictive classification models; optimize flight catering logistics and passenger booking demands; collaborate on Python codebases. Mandatory: Machine Learning, Scikit-learn, Pandas, Statistics. Experience with AWS is a plus."
                    )
                )
                database.curatedJobDao().insertJobs(initialJobs)

                // Prepopulate Study Tasks
                val initialTasks = listOf(
                    StudyTaskEntity(
                        title = "Practice core SQL Joins and Window Functions",
                        category = "SQL",
                        durationType = "DAILY",
                        isCompleted = false
                    ),
                    StudyTaskEntity(
                        title = "Solve Pandas data fabrication & cleaning exercises",
                        category = "Python",
                        durationType = "7_DAYS",
                        isCompleted = false
                    ),
                    StudyTaskEntity(
                        title = "Explore the Forage Virtual Internship for Business Analytics",
                        category = "General",
                        durationType = "DAILY",
                        isCompleted = true
                    ),
                    StudyTaskEntity(
                        title = "Design a clean streamlit template for resume analysis",
                        category = "Python",
                        durationType = "30_DAYS",
                        isCompleted = false
                    )
                )
                database.studyTaskDao().insertAll(initialTasks)

                // Prepopulate career CRM Job Applications with dynamic mock dates
                val now = System.currentTimeMillis()
                val oneDay = 86400000L
                val initialApplications = listOf(
                    JobApplicationEntity(
                        appliedDate = "2026-06-10",
                        company = "Saudi Aramco",
                        role = "Junior Data Analyst",
                        location = "Dhahran, Saudi Arabia",
                        source = "Company Website",
                        jobLink = "https://www.aramco.jobs",
                        matchScore = 95,
                        resumeVersion = "v2_DataAnalyst_Aramco",
                        status = "Interview",
                        notes = "First round technical screen completed successfully! Next behavioral manager session scheduled on study roadmap.",
                        lastUpdate = now - oneDay * 2
                    ),
                    JobApplicationEntity(
                        appliedDate = "2026-06-12",
                        company = "Qatar National Bank (QNB)",
                        role = "Business Intelligence Associate",
                        location = "Doha, Qatar",
                        source = "LinkedIn",
                        jobLink = "https://www.qnb.com/careers",
                        matchScore = 85,
                        resumeVersion = "v1_Fintech_Standard",
                        status = "Assessment",
                        notes = "Completed SQL / Excel model automated skills dashboard on 2026-06-14.",
                        lastUpdate = now - oneDay
                    ),
                    JobApplicationEntity(
                        appliedDate = "2026-06-02",
                        company = "Abu Dhabi National Oil Company (ADNOC)",
                        role = "MIS Analyst",
                        location = "Abu Dhabi, UAE",
                        source = "Indeed",
                        jobLink = "",
                        matchScore = 88,
                        resumeVersion = "v1_Standard",
                        status = "Offer",
                        notes = "Received verbal and written offer list! Model and spreadsheet skills praised.",
                        lastUpdate = now - oneDay * 5
                    ),
                    JobApplicationEntity(
                        appliedDate = "2026-05-28",
                        company = "Amazon",
                        role = "Data Operations Analyst",
                        location = "Bangalore, India",
                        source = "LinkedIn",
                        jobLink = "https://amazon.jobs",
                        matchScore = 79,
                        resumeVersion = "v1_Standard",
                        status = "Rejected",
                        notes = "Completed Online Assessment. Automated feedback indicates role filled.",
                        lastUpdate = now - oneDay * 12
                    ),
                    JobApplicationEntity(
                        appliedDate = "2026-06-15",
                        company = "Emirates Group",
                        role = "Junior Data Scientist",
                        location = "Dubai, UAE",
                        source = "Company Website",
                        jobLink = "https://www.emiratesgroupcareers.com",
                        matchScore = 72,
                        resumeVersion = "v3_DataScience_Emirates",
                        status = "Saved",
                        notes = "Analyzing skills roadmap before formal submission.",
                        lastUpdate = now
                    )
                )
                database.jobApplicationDao().insertAll(initialApplications)
            }
        }
    }
}
