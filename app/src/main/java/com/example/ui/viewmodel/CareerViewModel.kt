package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.StructuredAnalysisResult
import com.example.data.database.AppDatabase
import com.example.data.database.CuratedJobEntity
import com.example.data.database.JobScanEntity
import com.example.data.database.StudyTaskEntity
import com.example.data.database.UserProfileEntity
import com.example.data.database.JobApplicationEntity
import com.example.data.database.ResumeEntity
import com.example.data.database.ResumeJobAnalysisEntity
import com.example.data.repository.CareerRepository
import com.example.util.ResumeTextExtractor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context

class CareerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CareerRepository
    
    val userProfile: StateFlow<UserProfileEntity?>
    val allScans: StateFlow<List<JobScanEntity>>
    val allTasks: StateFlow<List<StudyTaskEntity>>
    val curatedJobs: StateFlow<List<CuratedJobEntity>>
    val allApplications: StateFlow<List<JobApplicationEntity>>
    val allResumes: StateFlow<List<ResumeEntity>>
    val allResumeAnalyses: StateFlow<List<ResumeJobAnalysisEntity>>

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val _selectedScanResult = MutableStateFlow<JobScanEntity?>(null)
    val selectedScanResult: StateFlow<JobScanEntity?> = _selectedScanResult.asStateFlow()

    private val _isAnalyzingResume = MutableStateFlow(false)
    val isAnalyzingResume: StateFlow<Boolean> = _isAnalyzingResume.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    private val _activeAnalysisResult = MutableStateFlow<com.example.data.api.StructuredResumeAnalysis?>(null)
    val activeAnalysisResult: StateFlow<com.example.data.api.StructuredResumeAnalysis?> = _activeAnalysisResult.asStateFlow()

    private val _isRewritingResume = MutableStateFlow(false)
    val isRewritingResume: StateFlow<Boolean> = _isRewritingResume.asStateFlow()

    private val _rewriteError = MutableStateFlow<String?>(null)
    val rewriteError: StateFlow<String?> = _rewriteError.asStateFlow()

    private val _activeRewriteResult = MutableStateFlow<com.example.data.api.StructuredResumeRewrite?>(null)
    val activeRewriteResult: StateFlow<com.example.data.api.StructuredResumeRewrite?> = _activeRewriteResult.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = CareerRepository(database)

        userProfile = repository.userProfile.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        allScans = repository.allScans.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allTasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        curatedJobs = repository.curatedJobs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allApplications = repository.allApplications.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allResumes = repository.allResumes.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allResumeAnalyses = repository.allResumeAnalyses.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Safe self-healing startup seed on background Dispatcher
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profileDao = database.userProfileDao()
                val profile = profileDao.getProfileSync()
                if (profile == null) {
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
                    profileDao.insertProfile(initialProfile)

                    val jobsDao = database.curatedJobDao()
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
                    jobsDao.insertJobs(initialJobs)

                    val taskDao = database.studyTaskDao()
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
                    taskDao.insertAll(initialTasks)

                    val appDao = database.jobApplicationDao()
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
                    appDao.insertAll(initialApplications)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectHistoricalScan(scan: JobScanEntity?) {
        _selectedScanResult.value = scan
    }

    fun clearScanError() {
        _scanError.value = null
    }

    fun triggerAtsScan(jobDescriptionText: String) {
        if (jobDescriptionText.isBlank()) {
            _scanError.value = "Job Description cannot be empty!"
            return
        }

        viewModelScope.launch {
            _isScanning.value = true
            _scanError.value = null
            try {
                val result: StructuredAnalysisResult? = repository.runAatsScan(jobDescriptionText)
                if (result != null) {
                    // Map StructuredAnalysisResult back to database JobScanEntity
                    val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                    val questionsAdapter = moshi.adapter(List::class.java) // For generic questions mapping
                    
                    val parsedQuestions = moshi.adapter(List::class.java).toJson(result.interviewQuestions.map {
                        mapOf(
                            "question" to it.question,
                            "type" to it.type,
                            "modelAnswer" to it.modelAnswer,
                            "suggestion" to it.suggestion
                        )
                    })

                    val parsedLearning = moshi.adapter(Map::class.java).toJson(mapOf(
                        "days7" to result.recommendedLearning.days7,
                        "days30" to result.recommendedLearning.days30,
                        "days60" to result.recommendedLearning.days60,
                        "days90" to result.recommendedLearning.days90
                    ))

                    val scanEntity = JobScanEntity(
                        jobTitle = result.jobTitle,
                        company = result.company,
                        location = result.location,
                        jdText = jobDescriptionText,
                        matchScore = result.matchScore,
                        summary = result.summary,
                        matchingSkillsRaw = result.matchingSkills.joinToString(", "),
                        missingSkillsRaw = result.missingSkills.joinToString(", "),
                        atsKeywordsRaw = result.atsKeywords.joinToString(", "),
                        resumeImprovementsRaw = result.resumeImprovements.joinToString("|"),
                        customResumeDraft = result.customResumeDraft,
                        coverLetterDraft = result.coverLetterDraft,
                        interviewQuestionsRaw = parsedQuestions,
                        recommendedLearningJson = parsedLearning
                    )

                    val savedId = repository.saveScan(scanEntity)
                    _selectedScanResult.value = scanEntity.copy(id = savedId)
                } else {
                    _scanError.value = "Failed to compile AI response. Check your parameters."
                }
            } catch (e: Exception) {
                _scanError.value = "Communication issue: ${e.localizedMessage ?: "Unknown network error"}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun toggleTask(task: StudyTaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun addCustomStudyTask(title: String, category: String, durationType: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.addTask(
                StudyTaskEntity(
                    title = title,
                    category = category.ifBlank { "General" },
                    durationType = durationType
                )
            )
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            repository.deleteTask(id)
        }
    }

    fun deleteHistoricalScan(id: Long) {
        viewModelScope.launch {
            repository.deleteScan(id)
            if (_selectedScanResult.value?.id == id) {
                _selectedScanResult.value = null
            }
        }
    }

    fun addApplication(
        appliedDate: String,
        company: String,
        role: String,
        location: String,
        source: String,
        jobLink: String,
        matchScore: Int,
        resumeVersion: String,
        status: String,
        notes: String
    ) {
        viewModelScope.launch {
            val duplicate = repository.checkDuplicate(company, role)
            if (duplicate != null) {
                val updated = duplicate.copy(
                    appliedDate = appliedDate,
                    location = location,
                    source = source,
                    jobLink = jobLink,
                    matchScore = matchScore,
                    resumeVersion = resumeVersion,
                    status = status,
                    notes = notes,
                    lastUpdate = System.currentTimeMillis()
                )
                repository.updateApplication(updated)
            } else {
                val newApp = JobApplicationEntity(
                    appliedDate = appliedDate,
                    company = company,
                    role = role,
                    location = location,
                    source = source,
                    jobLink = jobLink,
                    matchScore = matchScore,
                    resumeVersion = resumeVersion,
                    status = status,
                    notes = notes,
                    lastUpdate = System.currentTimeMillis()
                )
                repository.addApplication(newApp)
            }
        }
    }

    fun updateApplication(app: JobApplicationEntity) {
        viewModelScope.launch {
            repository.updateApplication(app.copy(lastUpdate = System.currentTimeMillis()))
        }
    }

    fun deleteApplication(id: Long) {
        viewModelScope.launch {
            repository.deleteApplication(id)
        }
    }

    fun parseAndProcessAssistantMessage(messageText: String, onResult: (String) -> Unit) {
        if (messageText.isBlank()) {
            onResult("Please type an update command (e.g. 'I applied to Saudi Aramco')")
            return
        }

        viewModelScope.launch {
            val text = messageText.trim().lowercase()
            
            if (text.contains("rejected") || text.contains("rejection") || text.contains("no longer considered") || text.contains("did not pass")) {
                var found = false
                val currentApps = allApplications.value
                for (app in currentApps) {
                    if (text.contains(app.company.lowercase())) {
                        val updated = app.copy(
                            status = "Rejected", 
                            notes = "${app.notes}\n[AI Update]: Set to 'Rejected' from voice text command.", 
                            lastUpdate = System.currentTimeMillis()
                        )
                        repository.updateApplication(updated)
                        onResult("Successfully processed! Set ${app.company} status to Rejected.")
                        found = true
                        break
                    }
                }
                if (!found && currentApps.isNotEmpty()) {
                    // Try to extract name of any company in text
                    onResult("Could not match existing recorded application tracker entry for that company name. Please verify spelling.")
                } else if (currentApps.isEmpty()) {
                    onResult("No tracked applications found to update. Try adding an entry first!")
                }
            } 
            else if (text.contains("interview") || text.contains("called") || text.contains("scheduled") || text.contains("invite")) {
                var found = false
                val currentApps = allApplications.value
                for (app in currentApps) {
                    if (text.contains(app.company.lowercase())) {
                        val updated = app.copy(
                            status = "Interview", 
                            notes = "${app.notes}\n[AI Update]: Set to 'Interview' from text command.", 
                            lastUpdate = System.currentTimeMillis()
                        )
                        repository.updateApplication(updated)
                        onResult("Great news! Updated ${app.company} status to 'Interview'. Check Prep Coach.")
                        found = true
                        break
                    }
                }
                if (!found) {
                    // Create standard entry
                    var companyName = "Target Partner"
                    val known = listOf("aramco", "qnb", "adnoc", "google", "amazon", "emirates", "microsoft")
                    for (k in known) {
                        if (text.contains(k)) {
                            companyName = k.replaceFirstChar { it.uppercase() }
                            break
                        }
                    }
                    val newApp = JobApplicationEntity(
                        appliedDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
                        company = companyName,
                        role = "Associate Data Analyst",
                        location = "Global",
                        source = "Search",
                        jobLink = "",
                        matchScore = 80,
                        resumeVersion = "v1_Standard",
                        status = "Interview",
                        notes = "[AI Auto-Created on Interview Alert]: $messageText",
                        lastUpdate = System.currentTimeMillis()
                    )
                    repository.addApplication(newApp)
                    onResult("Could not find a tracker match, so a NEW entry for '$companyName' has been created as standard Interview status.")
                }
            }
            else if (text.contains("applied") || text.contains("submitted") || text.contains("sent application")) {
                var found = false
                val currentApps = allApplications.value
                for (app in currentApps) {
                    if (text.contains(app.company.lowercase())) {
                        val updated = app.copy(
                            status = "Applied", 
                            notes = "${app.notes}\n[AI Update]: Set to 'Applied' from text command.", 
                            lastUpdate = System.currentTimeMillis()
                        )
                        repository.updateApplication(updated)
                        onResult("Updated tracker: ${app.company} status set to 'Applied'.")
                        found = true
                        break
                    }
                }
                if (!found) {
                    var companyName = "New Company"
                    val known = listOf("aramco", "qnb", "adnoc", "google", "amazon", "emirates", "microsoft")
                    for (k in known) {
                        if (text.contains(k)) {
                            companyName = k.replaceFirstChar { it.uppercase() }
                            break
                        }
                    }
                    if (companyName == "New Company") {
                        val parts = text.split("applied to ")
                        if (parts.size > 1) {
                            companyName = parts[1].split(" ").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "New Company"
                        }
                    }
                    val newApp = JobApplicationEntity(
                        appliedDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()),
                        company = companyName,
                        role = "Data Operations Analyst",
                        location = "Remote",
                        source = "Direct",
                        jobLink = "",
                        matchScore = 80,
                        resumeVersion = "v1_Standard",
                        status = "Applied",
                        notes = "[AI Auto-Created on Apply Alert]: $messageText",
                        lastUpdate = System.currentTimeMillis()
                    )
                    repository.addApplication(newApp)
                    onResult("Recognized action! Logged a brand-new Application for $companyName (status: Applied)!")
                }
            } else {
                onResult("I didn't recognize a specific status changes. Try saying: \"applied to [Company]\", \"rejected from [Company]\" or \"scheduled interview at [Company]\"!")
            }
        }
    }

    fun updateProfile(
        name: String,
        education: String,
        skills: String,
        projectsJson: String,
        certifications: String,
        locations: String,
        interests: String
    ) {
        viewModelScope.launch {
            val updated = UserProfileEntity(
                id = 1,
                name = name,
                education = education,
                skillsRaw = skills,
                projectsRaw = projectsJson,
                certificationsRaw = certifications,
                preferredLocationsRaw = locations,
                careerInterestsRaw = interests
            )
            repository.updateProfile(updated)
        }
    }

    // ==========================================
    // GEMINI MULTI-TURN CHAT INTERFACE & FEATURES
    // ==========================================
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage(
            text = "Hello! I am CareerOS AI, your personal elite career coach and ATS strategist. How can I help you design, optimize, or refine your professional journey today?",
            isUser = false
        )
    ))
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    fun clearChatHistory() {
        _chatMessages.value = listOf(
            ChatMessage(
                text = "Hello! I am CareerOS AI, your personal career coach and ATS strategist. How can I help you design, optimize, or refine your professional journey today?",
                isUser = false
            )
        )
        _chatError.value = null
    }

    fun sendChatMessage(
        messageText: String,
        useGoogleSearch: Boolean,
        enableHighThinking: Boolean
    ) {
        val trimmed = messageText.trim()
        if (trimmed.isBlank()) return
        
        val userMsg = ChatMessage(text = trimmed, isUser = true)
        val currentMsgs = _chatMessages.value.toMutableList()
        currentMsgs.add(userMsg)
        _chatMessages.value = currentMsgs
        _isChatLoading.value = true
        _chatError.value = null

        viewModelScope.launch {
            try {
                val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        text = "I couldn't look up an API key, but here is a simulated support response: That's a great question about data science fields! To prepare, practice Python, SQL, and Machine Learning models like Random Forests.",
                        isUser = false
                    )
                    return@launch
                }

                // Compile system instruction and user profile context
                val profile = userProfile.value
                val userProfileContext = if (profile != null) {
                    """
                    Candidate Profile context:
                    - Name: ${profile.name}
                    - Education: ${profile.education}
                    - Skills: ${profile.skillsRaw}
                    - Projects: ${profile.projectsRaw}
                    - Career Interests: ${profile.careerInterestsRaw}
                    """.trimIndent()
                } else {
                    "No candidate profile context completed yet."
                }

                val systemInstructionText = """
                    You are CareerOS AI - an elite personal career coach, resume strategist, and ATS score assistant.
                    Your role is to offer highly tactical, professional, motivating, and honest guidance.
                    
                    Here is the candidate's current profile:
                    $userProfileContext
                    
                    Adhere to these core coaching rules:
                    - Give concise, actionable advice. Highlight exact skills, formats, and structural tweaks.
                    - Always maintain 100% honesty: never suggest the user lie or exaggerate.
                    - Keep a supportive, professional, and mentoring tone.
                """.trimIndent()

                // Map message history to Gemini API Content array
                val contentsList = currentMsgs.map {
                    com.example.data.api.Content(
                        parts = listOf(com.example.data.api.Part(text = it.text)),
                        role = if (it.isUser) "user" else "model"
                    )
                }

                // Choose model and configuration
                val modelName = if (enableHighThinking) "gemini-3.1-pro-preview" else "gemini-3.5-flash"
                val toolsConfig = if (useGoogleSearch) {
                    listOf(com.example.data.api.GeminiTool(googleSearch = com.example.data.api.GoogleSearchTool()))
                } else {
                    null
                }

                val thinkingConfig = if (enableHighThinking) {
                    com.example.data.api.ThinkingConfig(thinkingLevel = "high")
                } else {
                    null
                }

                val request = com.example.data.api.GeminiRequest(
                    contents = contentsList,
                    generationConfig = com.example.data.api.GenerationConfig(
                        temperature = if (enableHighThinking) null else 0.4,
                        thinkingConfig = thinkingConfig
                    ),
                    systemInstruction = com.example.data.api.Content(
                        parts = listOf(com.example.data.api.Part(text = systemInstructionText))
                    ),
                    tools = toolsConfig
                )

                val response = com.example.data.api.GeminiClient.service.generateContentDynamic(modelName, apiKey, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "I received an empty response. Please try again."

                _chatMessages.value = _chatMessages.value + ChatMessage(text = responseText, isUser = false)
            } catch (e: Exception) {
                e.printStackTrace()
                _chatError.value = "Failed to communicate: ${e.localizedMessage ?: "Unknown error"}"
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    text = "Sorry, I ran into an issue connecting to the servers: ${e.localizedMessage ?: "Connection Timeout"}. Please check your internet connection or API settings.",
                    isUser = false
                )
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun parseResumeSections(text: String): Map<String, String> {
        val sections = mutableMapOf(
            "skills" to "",
            "education" to "",
            "experience" to "",
            "certifications" to "",
            "projects" to ""
        )
        val lines = text.lines()
        var currentSection = "summary"
        val sectionBuilders = mutableMapOf<String, StringBuilder>()
        sections.keys.forEach { sectionBuilders[it] = StringBuilder() }
        sectionBuilders["summary"] = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim().lowercase()
            when {
                trimmed.contains("skills") || trimmed.contains("technical skills") || trimmed.contains("core competencies") -> currentSection = "skills"
                trimmed.contains("education") || trimmed.contains("academic") -> currentSection = "education"
                trimmed.contains("experience") || trimmed.contains("employment") || trimmed.contains("work history") || trimmed.contains("professional experience") -> currentSection = "experience"
                trimmed.contains("certifications") || trimmed.contains("credentials") || trimmed.contains("licenses") -> currentSection = "certifications"
                trimmed.contains("projects") || trimmed.contains("featured projects") || trimmed.contains("academic projects") -> currentSection = "projects"
                else -> {
                    sectionBuilders[currentSection]?.append(line)?.append("\n")
                }
            }
        }
        return mapOf(
            "skills" to sectionBuilders["skills"].toString().trim(),
            "education" to sectionBuilders["education"].toString().trim(),
            "experience" to sectionBuilders["experience"].toString().trim(),
            "certifications" to sectionBuilders["certifications"].toString().trim(),
            "projects" to sectionBuilders["projects"].toString().trim()
        )
    }

    fun uploadAndSaveResume(context: Context, uri: android.net.Uri, name: String) {
        viewModelScope.launch {
            _isAnalyzingResume.value = true
            _analysisError.value = null
            try {
                val rawText = withContext(Dispatchers.IO) {
                    ResumeTextExtractor.extractText(context, uri)
                }
                if (rawText.isBlank() || rawText.startsWith("Error extracting text")) {
                    _analysisError.value = "Failed to extract text from resume: $rawText"
                    return@launch
                }
                
                val sections = parseResumeSections(rawText)
                val maxVer = repository.getMaxResumeVersion()
                val nextVer = maxVer + 1
                
                val resume = ResumeEntity(
                    name = name,
                    version = nextVer,
                    rawText = rawText,
                    skillsRaw = sections["skills"] ?: "",
                    educationRaw = sections["education"] ?: "",
                    experienceRaw = sections["experience"] ?: "",
                    certificationsRaw = sections["certifications"] ?: "",
                    projectsRaw = sections["projects"] ?: ""
                )
                repository.saveResume(resume)
            } catch (e: Exception) {
                _analysisError.value = "Failed to upload resume: ${e.localizedMessage}"
            } finally {
                _isAnalyzingResume.value = false
            }
        }
    }

    fun analyzeResumeMatch(
        resumeId: Long,
        jobTitle: String,
        companyName: String,
        jobUrl: String,
        jobPostingContent: String,
        jobDescriptionText: String
    ) {
        if (jobDescriptionText.isBlank()) {
            _analysisError.value = "Job Description cannot be empty!"
            return
        }
        viewModelScope.launch {
            _isAnalyzingResume.value = true
            _analysisError.value = null
            try {
                val resume = repository.getResumeById(resumeId)
                if (resume == null) {
                    _analysisError.value = "Resume not found!"
                    return@launch
                }
                val result = repository.runResumeMatchAnalysis(
                    resumeText = resume.rawText,
                    jdText = jobDescriptionText,
                    jobTitle = jobTitle,
                    companyName = companyName,
                    jobUrl = jobUrl,
                    jobPostingContent = jobPostingContent
                )
                if (result != null) {
                    _activeAnalysisResult.value = result
                    // Save to local database
                    val analysisEntity = ResumeJobAnalysisEntity(
                        resumeId = resume.id,
                        resumeVersion = resume.version,
                        jobTitle = jobTitle.ifBlank { "Job Candidate" },
                        companyName = companyName.ifBlank { "Unknown Company" },
                        jobUrl = jobUrl,
                        jobDescriptionText = jobDescriptionText,
                        jobPostingContent = jobPostingContent,
                        matchScore = result.matchScore,
                        skillsMatchScore = result.skillsMatchScore,
                        keywordMatchScore = result.keywordMatchScore,
                        educationMatch = result.educationMatch,
                        experienceMatch = result.experienceMatch,
                        missingSkillsRaw = result.missingSkills.joinToString(", "),
                        missingKeywordsRaw = result.missingKeywords.joinToString(", "),
                        strengthsRaw = result.strengths.joinToString(", "),
                        weaknessesRaw = result.weaknesses.joinToString(", "),
                        interviewReadinessScore = result.interviewReadinessScore
                    )
                    repository.saveResumeAnalysis(analysisEntity)
                } else {
                    _analysisError.value = "Failed to run match analysis."
                }
            } catch (e: Exception) {
                _analysisError.value = "Error: ${e.localizedMessage}"
            } finally {
                _isAnalyzingResume.value = false
            }
        }
    }

    fun rewriteResume(
        resumeId: Long,
        instructions: String,
        jdText: String?
    ) {
        viewModelScope.launch {
            _isRewritingResume.value = true
            _rewriteError.value = null
            try {
                val resume = repository.getResumeById(resumeId)
                if (resume == null) {
                    _rewriteError.value = "Resume not found!"
                    return@launch
                }
                val result = repository.runResumeRewrite(
                    resumeText = resume.rawText,
                    instructions = instructions,
                    jdText = jdText
                )
                if (result != null) {
                    _activeRewriteResult.value = result
                } else {
                    _rewriteError.value = "Failed to rewrite resume."
                }
            } catch (e: Exception) {
                _rewriteError.value = "Error: ${e.localizedMessage}"
            } finally {
                _isRewritingResume.value = false
            }
        }
    }

    fun approveSectionRewrite(
        resumeId: Long,
        sectionName: String,
        rewrittenText: String
    ) {
        viewModelScope.launch {
            try {
                val resume = repository.getResumeById(resumeId) ?: return@launch
                
                // Determine which section to update in our structured fields
                val cleanSection = sectionName.lowercase()
                var skills = resume.skillsRaw
                var education = resume.educationRaw
                var experience = resume.experienceRaw
                var certifications = resume.certificationsRaw
                var projects = resume.projectsRaw
                
                when {
                    cleanSection.contains("skill") -> skills = rewrittenText
                    cleanSection.contains("education") -> education = rewrittenText
                    cleanSection.contains("experience") || cleanSection.contains("work") || cleanSection.contains("history") || cleanSection.contains("employment") -> experience = rewrittenText
                    cleanSection.contains("certification") || cleanSection.contains("credential") -> certifications = rewrittenText
                    cleanSection.contains("project") -> projects = rewrittenText
                    else -> experience = rewrittenText // fallback to experience
                }
                
                // Re-compile raw text from sections
                val newRawText = """
                    ${resume.name.substringBeforeLast(".")} - Updated Version
                    
                    EXPERIENCE
                    $experience
                    
                    SKILLS
                    $skills
                    
                    PROJECTS
                    $projects
                    
                    EDUCATION
                    $education
                    
                    CERTIFICATIONS
                    $certifications
                """.trimIndent()
                
                val maxVer = repository.getMaxResumeVersion()
                val nextVer = maxVer + 1
                
                val updatedResume = ResumeEntity(
                    name = "${resume.name.substringBeforeLast(".")}_v$nextVer.txt",
                    version = nextVer,
                    rawText = newRawText,
                    skillsRaw = skills,
                    educationRaw = education,
                    experienceRaw = experience,
                    certificationsRaw = certifications,
                    projectsRaw = projects
                )
                
                repository.saveResume(updatedResume)
                
                // Remove the approved section from active rewrite result list so it doesn't show as pending
                val currentRewrite = _activeRewriteResult.value
                if (currentRewrite != null) {
                    val updatedSections = currentRewrite.sectionBySectionRewrite.filter { it.sectionName != sectionName }
                    _activeRewriteResult.value = currentRewrite.copy(sectionBySectionRewrite = updatedSections)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun approveFutureSkill(skillName: String) {
        viewModelScope.launch {
            try {
                // 1. Add as a study task to learn this skill
                repository.addTask(
                    StudyTaskEntity(
                        title = "Learn and master $skillName",
                        category = "Skill Recommendation",
                        durationType = "DAILY",
                        isCompleted = false
                    )
                )
                
                // 2. Append to profile skills
                val profile = userProfile.value
                if (profile != null) {
                    val currentSkills = profile.skillsRaw.split(",").map { it.trim() }.toMutableList()
                    if (!currentSkills.contains(skillName)) {
                        currentSkills.add(skillName)
                        val updatedProfile = profile.copy(skillsRaw = currentSkills.joinToString(", "))
                        repository.updateProfile(updatedProfile)
                    }
                }
                
                // Remove from active rewrite list
                val currentRewrite = _activeRewriteResult.value
                if (currentRewrite != null) {
                    val updatedSkills = currentRewrite.recommendedFutureSkills.filter { it.skillName != skillName }
                    _activeRewriteResult.value = currentRewrite.copy(recommendedFutureSkills = updatedSkills)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteResume(id: Long) {
        viewModelScope.launch {
            repository.deleteResume(id)
        }
    }

    fun clearAnalysisResult() {
        _activeAnalysisResult.value = null
    }

    fun clearRewriteResult() {
        _activeRewriteResult.value = null
    }

    // A factory to cleanly provision CareerViewModel
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CareerViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CareerViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
