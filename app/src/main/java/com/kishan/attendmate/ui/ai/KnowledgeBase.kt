package com.kishan.attendmate.ui.ai

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * On-device knowledge base for attendance-related Q&A.
 * Uses TF-IDF-like scoring to match user queries to the best answer.
 * No external API — fully offline.
 */
object KnowledgeBase {

    data class QaEntry(
        val keywords: List<String>,   // terms for matching
        val question: String,         // human-readable question
        val answer: String            // detailed answer
    )

    private val ENTRIES = listOf(
        QaEntry(
            listOf("why", "important", "attendance", "matter"),
            "Why is attendance important?",
            "📚 **Why Attendance Matters**\n\n" +
            "• Most universities require **75% minimum** attendance to be eligible for exams.\n" +
            "• Regular attendance improves understanding — studies show students who attend 90%+ score **15-20% higher** on average.\n" +
            "• It builds discipline and preparation habits that benefit your career.\n" +
            "• Many companies check college attendance records during placements."
        ),
        QaEntry(
            listOf("75", "percent", "rule", "minimum", "requirement", "threshold"),
            "What is the 75% attendance rule?",
            "📏 **The 75% Rule**\n\n" +
            "• Most Indian universities mandate a **minimum of 75%** attendance in each subject.\n" +
            "• Falling below 75% can result in:\n  — Being **debarred from exams**\n  — **Losing grades** or getting detained\n  — Extra assignments or re-enrollment\n" +
            "• Some institutions allow medical exemptions with valid documentation."
        ),
        QaEntry(
            listOf("what", "happens", "low", "below", "less", "drop", "consequence"),
            "What happens if attendance drops below threshold?",
            "⚠️ **Consequences of Low Attendance**\n\n" +
            "• **Exam debarment** — You may not be allowed to sit for exams.\n" +
            "• **Grade penalty** — Some colleges deduct internal marks.\n" +
            "• **Detention** — In severe cases, you may need to repeat the year.\n" +
            "• **Placement issues** — Companies see attendance as a reliability indicator.\n\n" +
            "💡 *Use the **prediction** feature here to forecast your attendance and plan ahead!*"
        ),
        QaEntry(
            listOf("how", "improve", "increase", "raise", "better", "boost"),
            "How can I improve my attendance?",
            "📈 **Tips to Improve Attendance**\n\n" +
            "1. **Set daily alarms** 30 mins before your first class.\n" +
            "2. **Sit in the front row** — it increases engagement significantly.\n" +
            "3. **Find a study buddy** — peer accountability works.\n" +
            "4. **Track your streaks** — use this app to see your streak and keep it going.\n" +
            "5. **Plan your leaves** — use the what-if calculator before bunking.\n" +
            "6. **Review notes before class** — it makes attending feel worthwhile.\n" +
            "7. **Reward yourself** — set milestone rewards for attendance goals."
        ),
        QaEntry(
            listOf("bunk", "safe", "miss", "skip", "safely"),
            "Is it safe to bunk/miss a class?",
            "🤔 **Should You Bunk?**\n\n" +
            "• Use the **\"How many can I miss?\"** command to check your exact safe-miss count.\n" +
            "• General rule: If you're above **80%**, you usually have some buffer.\n" +
            "• Below 75%? Every class counts — **don't risk it**.\n" +
            "• Between 75-80%? You have very little margin. Think carefully.\n\n" +
            "💡 *Ask me: \"How many can I miss in [Subject]?\" for a personalized calculation.*"
        ),
        QaEntry(
            listOf("streak", "maintain", "consecutive", "row"),
            "How do streaks work?",
            "🔥 **Attendance Streaks**\n\n" +
            "• A streak counts **consecutive present classes** (or consecutive absences).\n" +
            "• Maintaining a streak is psychologically motivating — it creates positive momentum.\n" +
            "• Research shows that **habits form after ~21 consecutive days** of repetition.\n" +
            "• Try to maintain at least a **5-class streak** in each subject.\n\n" +
            "💡 *Ask me: \"Show my trend\" or \"Attendance pattern\" to see your streaks!*"
        ),
        QaEntry(
            listOf("semester", "end", "pass", "fail", "exam", "eligibility"),
            "Will I pass the semester attendance requirement?",
            "🎓 **Semester Attendance Check**\n\n" +
            "• Use the **\"Predict my attendance\"** command — I'll use your data to forecast your end-of-semester percentage.\n" +
            "• The prediction uses **linear regression** on your actual attendance history.\n" +
            "• I'll also show you which subjects are at risk and how many more classes you need.\n\n" +
            "💡 *Try: \"Predict my attendance\" for a full forecast report!*"
        ),
        QaEntry(
            listOf("medical", "leave", "sick", "absent", "exemption", "certificate"),
            "Does medical leave count as attendance?",
            "🏥 **Medical Leave & Attendance**\n\n" +
            "• Most colleges accept medical certificates to **condone absences**.\n" +
            "• You typically need a **valid medical certificate** from a registered doctor.\n" +
            "• The leave must usually be **applied within 7 days** of returning.\n" +
            "• Check your college's specific policy — rules vary by institution.\n\n" +
            "💡 *In this app, medical leaves are counted as absences. Factor them into your planning.*"
        ),
        QaEntry(
            listOf("internal", "marks", "assessment", "grade", "affect"),
            "Does attendance affect internal marks?",
            "📝 **Attendance & Internal Assessment**\n\n" +
            "• Many colleges allocate **5-15 marks** of internal assessment based on attendance.\n" +
            "• Typical grade scale:\n  — 90%+ attendance → Full marks\n  — 80-90% → 80% of marks\n  — 75-80% → 60% of marks\n  — Below 75% → Zero or debarment\n" +
            "• These marks directly affect your GPA — don't underestimate them!"
        ),
        QaEntry(
            listOf("placement", "company", "interview", "job", "career"),
            "Does attendance matter for placements?",
            "💼 **Attendance & Placements**\n\n" +
            "• Many companies check attendance records as a **reliability indicator**.\n" +
            "• Companies like TCS, Infosys, and Wipro have **minimum attendance criteria** for placement eligibility.\n" +
            "• Good attendance → Better internal assessment marks → Higher GPA → Better placements.\n" +
            "• It demonstrates **discipline and commitment** — qualities employers value."
        ),
        QaEntry(
            listOf("app", "attendmate", "use", "work", "feature"),
            "How does AttendMate work?",
            "📱 **About AttendMate**\n\n" +
            "AttendMate is your intelligent attendance companion that:\n" +
            "• 📊 Tracks attendance across all subjects with detailed stats\n" +
            "• 🤖 Uses on-device AI for smart insights and predictions\n" +
            "• 📈 Predicts your end-of-semester attendance percentage\n" +
            "• 💡 Provides personalized study tips and motivation\n" +
            "• 🔮 Calculates safe-to-miss counts per subject\n" +
            "• 🗓️ Shows your timetable and upcoming classes\n" +
            "• 📉 Detects attendance trends and patterns\n" +
            "• 🎯 Helps you set and track attendance goals\n\n" +
            "All AI features run **100% offline** — no internet needed!"
        ),
        QaEntry(
            listOf("calculate", "formula", "how", "computed", "math", "work"),
            "How is attendance percentage calculated?",
            "🔢 **Attendance Calculation**\n\n" +
            "```\nAttendance % = (Classes Attended / Total Classes) × 100\n```\n\n" +
            "• Each lecture slot counts as **one class**.\n" +
            "• Both present and absent count toward total.\n" +
            "• The what-if calculator uses: `(attended ÷ total) ≥ 0.75` to check safety.\n" +
            "• Safe misses = `floor((attended - 0.75 × total) / 0.75)`"
        ),
        QaEntry(
            listOf("proxy", "fake", "friend", "someone", "else", "mark", "for"),
            "Can I ask a friend to mark proxy attendance?",
            "🚫 **Proxy Attendance**\n\n" +
            "• Marking proxy attendance (having someone else mark you present) is considered a serious academic offense.\n" +
            "• If caught, you may face disciplinary action, including suspension or cancellation of attendance for the entire semester.\n" +
            "• Many colleges now use biometrics or location-based tracking to prevent this.\n" +
            "• It's not worth the risk!"
        ),
        QaEntry(
            listOf("condonation", "condone", "fine", "fee", "pardon", "leniency"),
            "What is attendance condonation?",
            "⚖️ **Attendance Condonation**\n\n" +
            "• Condonation is a formal pardon granted for a slight shortage in attendance (usually between 65% and 75%).\n" +
            "• It typically requires a valid reason (like a medical issue) and approval from the Head of Department or Principal.\n" +
            "• Often, you have to pay a condonation fee.\n" +
            "• It is not guaranteed and is usually granted only once or twice during your degree."
        ),
        QaEntry(
            listOf("debar", "debarred", "detained", "exam", "not", "allowed"),
            "What does exam debarment mean?",
            "🛑 **Exam Debarment**\n\n" +
            "• If your attendance is below the required threshold (usually 75%) and cannot be condoned, you will be debarred.\n" +
            "• This means you cannot sit for the current semester's final exams.\n" +
            "• You may have to repeat the semester or clear the subjects as arrears/backlogs later.\n" +
            "• Keep tracking your attendance to avoid this!"
        ),
        QaEntry(
            listOf("semester", "duration", "how", "long", "weeks", "months"),
            "What is the typical semester duration?",
            "⏱️ **Semester Duration**\n\n" +
            "• A typical college semester lasts for about **14 to 16 weeks** of actual teaching.\n" +
            "• This usually translates to around 90 working days.\n" +
            "• Exams and holidays are usually outside of this 14-16 week period.\n" +
            "• This means you have a limited number of classes per subject to maintain your 75%!"
        ),

        // ═══════════════════ EXPANDED ENTRIES ═══════════════════

        // --- Attendance Policies & Rules ---

        QaEntry(
            listOf("minimum", "attendance", "requirement", "university", "different", "college"),
            "What is the minimum attendance requirement in different universities?",
            "🏛️ **Minimum Attendance Requirements**\n\n" +
            "• **Most Indian universities** mandate **75%** minimum attendance.\n" +
            "• **IITs & NITs** — Usually 75%, but some departments enforce **80%** for labs.\n" +
            "• **Private universities** (SRM, VIT, Manipal) — Often enforce **75-85%** depending on the program.\n" +
            "• **Autonomous colleges** may set their own thresholds, sometimes as low as 65% or as high as 85%.\n" +
            "• **UGC Guidelines** recommend 75% but each university's Academic Council has the final say.\n\n" +
            "💡 *Always check your specific university ordinance for the exact requirement.*"
        ),
        QaEntry(
            listOf("75", "rule", "dtu", "ggsipu", "ipu", "mumbai", "vtu", "university", "differ"),
            "How does the 75% rule differ across universities?",
            "📊 **75% Rule — University Comparison**\n\n" +
            "• **DTU (Delhi Technological University)** — Strict 75% rule; attendance condonation up to 5% for medical reasons.\n" +
            "• **GGSIPU / IPU** — 75% mandatory; below 65% means direct debarment, 65-75% eligible for condonation with fee.\n" +
            "• **Mumbai University** — 75% overall; some colleges enforce subject-wise 75%.\n" +
            "• **VTU (Visvesvaraya)** — 85% required in CIE; condonation available for 75-85% range.\n" +
            "• **Anna University** — 75% with no condonation in many affiliated colleges.\n" +
            "• **JNTU** — 75%, with 10% relaxation possible for medical/extenuating reasons.\n\n" +
            "⚠️ *Rules change frequently — verify with your college office every semester.*"
        ),
        QaEntry(
            listOf("condonation", "work", "process", "apply", "fee", "eligibility"),
            "How does attendance condonation work in detail?",
            "📋 **Attendance Condonation Process**\n\n" +
            "• **Step 1:** Check if your attendance is in the condonable range (typically **65-74%**).\n" +
            "• **Step 2:** Obtain a condonation application form from your department.\n" +
            "• **Step 3:** Attach supporting documents — medical certificates, family emergency proof, etc.\n" +
            "• **Step 4:** Get recommendations from your class advisor and HOD.\n" +
            "• **Step 5:** Pay the condonation fee (ranges from ₹500 to ₹5,000 depending on university).\n" +
            "• **Step 6:** Submit to the Dean/Principal for final approval.\n\n" +
            "⚠️ Condonation is usually allowed **only once per semester** and a maximum of **2-3 times** during your entire degree."
        ),
        QaEntry(
            listOf("audit", "university", "inspection", "verification", "check"),
            "What happens during a university audit of attendance?",
            "🔍 **University Attendance Audits**\n\n" +
            "• Universities periodically **audit attendance registers** to ensure accuracy and prevent manipulation.\n" +
            "• Auditors cross-check **physical registers with digital records** and student submissions.\n" +
            "• If discrepancies are found, **both faculty and students** may face disciplinary action.\n" +
            "• Some universities send **surprise inspection teams** to verify physical presence in classrooms.\n" +
            "• Biometric/RFID-based systems have reduced fraud but audits still happen.\n\n" +
            "💡 *Keep personal attendance records in AttendMate as backup documentation!*"
        ),
        QaEntry(
            listOf("teacher", "modify", "change", "edit", "correction", "after", "marking"),
            "Can teachers modify attendance after marking?",
            "✏️ **Attendance Modification Policies**\n\n" +
            "• Most colleges allow faculty to **correct attendance within 24-48 hours** of the class.\n" +
            "• After the correction window, changes usually require **HOD approval** with written justification.\n" +
            "• In biometric systems, manual overrides need **admin-level access** and are logged.\n" +
            "• Students can request corrections by filing a written application with proof (e.g., assignment submission, witness).\n" +
            "• At the end of semester, attendance is **frozen** before being sent to the exam branch.\n\n" +
            "💡 *Track your attendance daily in AttendMate so you can spot and report errors quickly!*"
        ),
        QaEntry(
            listOf("lecture", "lab", "practical", "difference", "theory", "count"),
            "What is the difference between lecture and lab attendance?",
            "🔬 **Lecture vs. Lab Attendance**\n\n" +
            "• **Lectures** are typically 1 hour each and counted as **1 class** per slot.\n" +
            "• **Labs/Practicals** usually run for 2-3 hours but may count as **1, 2, or 3 classes** depending on your college.\n" +
            "• Missing a lab is often **more damaging** to your attendance because it may deduct multiple classes at once.\n" +
            "• Some colleges track **lab and theory attendance separately** — you must meet the threshold in both.\n" +
            "• Labs may also have a **mandatory minimum of 80-100%** attendance for practical exam eligibility.\n\n" +
            "⚠️ *Always confirm how your college counts lab slots before planning any skips!*"
        ),
        QaEntry(
            listOf("tutorial", "practical", "counted", "tut", "extra", "session"),
            "How are tutorials and practicals counted for attendance?",
            "📝 **Tutorials & Practicals Attendance**\n\n" +
            "• **Tutorials** are usually counted as **separate classes** alongside lectures.\n" +
            "• Some universities club tutorial attendance with lecture attendance; others track them independently.\n" +
            "• **Practicals** often carry a **separate attendance percentage** requirement.\n" +
            "• Missing tutorials is risky because they're fewer in number — each absence has a **larger impact** on your percentage.\n" +
            "• In VTU and some other universities, CIE (Continuous Internal Evaluation) includes tutorial/practical attendance.\n\n" +
            "💡 *In AttendMate, add tutorials and practicals as separate subjects to track them accurately.*"
        ),

        // --- Academic Impact ---

        QaEntry(
            listOf("cgpa", "gpa", "grade", "affect", "impact", "relationship"),
            "How does attendance affect CGPA/GPA?",
            "📊 **Attendance & CGPA Relationship**\n\n" +
            "• **Direct impact:** Many universities allocate **5-15%** of internal assessment marks based on attendance.\n" +
            "• **Indirect impact:** Regular attendance correlates with better understanding → better exam scores → higher GPA.\n" +
            "• Studies show that students with **90%+ attendance** have an average CGPA **0.5-1.0 points higher** than those below 75%.\n" +
            "• Some universities have **attendance-based grade bumps** — e.g., 95%+ attendance may earn bonus marks.\n" +
            "• Low attendance → exam debarment → SGPA of 0 for that semester → drags down CGPA significantly.\n\n" +
            "🎯 *Track your attendance trend using AttendMate to see the projected impact on your grades!*"
        ),
        QaEntry(
            listOf("exam", "performance", "score", "marks", "correlation", "study"),
            "What is the relationship between attendance and exam performance?",
            "📈 **Attendance vs. Exam Performance**\n\n" +
            "• Research from IITs and NITs shows a **strong positive correlation** (r ≈ 0.6-0.8) between attendance and exam scores.\n" +
            "• Students with **85%+ attendance** score on average **18-25% higher** than those with 60-70% attendance.\n" +
            "• Regular attendance means **consistent exposure** to topics, reducing last-minute cramming.\n" +
            "• You catch important hints about **exam patterns and expected questions** from professors in class.\n" +
            "• Even if you self-study, missing classes means missing **announcements, deadline changes, and bonus opportunities**.\n\n" +
            "💡 *Use the prediction feature: \"Predict my attendance\" to stay ahead!*"
        ),
        QaEntry(
            listOf("backlog", "re-exam", "arrear", "supplementary", "reappear"),
            "How do backlogs and re-exams relate to attendance?",
            "🔄 **Backlogs & Attendance**\n\n" +
            "• If you're **debarred from exams** due to low attendance, those subjects automatically become **backlogs**.\n" +
            "• You must clear them in subsequent semesters as **supplementary/re-appear** exams.\n" +
            "• Some universities require you to **re-attend classes** before allowing re-examination.\n" +
            "• Accumulated backlogs can lead to a **year-back or academic probation**.\n" +
            "• Many companies during placements have a **zero-backlog policy** — attendance-caused backlogs hurt equally.\n\n" +
            "⚠️ *Prevention is better than cure. Use AttendMate's what-if calculator before skipping classes!*"
        ),
        QaEntry(
            listOf("year", "back", "detained", "repeat", "fail", "semester"),
            "Can I get a year-back due to low attendance?",
            "🛑 **Year-Back Due to Attendance**\n\n" +
            "• **Yes, it is possible.** If you are debarred from all or most exams in a semester, you effectively lose that semester.\n" +
            "• Accumulating more than a certain number of backlogs (often **4-6 subjects**) may trigger an **automatic year-back**.\n" +
            "• Some universities have a rule: if **detained in consecutive semesters**, you must repeat the lower semester.\n" +
            "• A year-back adds an extra year to your degree, costing **time, money, and mental stress**.\n" +
            "• It also affects your **graduation timeline** and eligibility for age-restricted exams/jobs.\n\n" +
            "💡 *Set up your attendance goals in AttendMate and check predictions regularly to avoid this situation.*"
        ),
        QaEntry(
            listOf("scholarship", "eligibility", "financial", "aid", "merit", "stipend"),
            "How does attendance affect scholarship eligibility?",
            "🎓 **Attendance & Scholarships**\n\n" +
            "• Most merit and need-based scholarships require a **minimum of 75-80%** attendance.\n" +
            "• Government scholarships (Post-Matric, National Scholarship Portal) often mandate **75% attendance** for renewal.\n" +
            "• University merit scholarships may require **85%+ attendance** along with GPA criteria.\n" +
            "• Private scholarships and corporate sponsorships frequently include attendance as an **eligibility criterion**.\n" +
            "• Losing a scholarship due to attendance means losing **₹10,000 to ₹2,00,000+** per year depending on the program.\n\n" +
            "💡 *Use AttendMate to ensure you never dip below scholarship thresholds!*"
        ),

        // --- Strategies & Tips ---

        QaEntry(
            listOf("time", "management", "schedule", "routine", "organize", "tips"),
            "Best time management tips for maintaining attendance?",
            "⏰ **Time Management for Attendance**\n\n" +
            "1. **Create a fixed weekly schedule** — Block class times as non-negotiable.\n" +
            "2. **Prepare the night before** — Lay out clothes, pack your bag, charge devices.\n" +
            "3. **Use the 2-minute rule** — If a task takes less than 2 minutes, do it immediately (e.g., setting alarms).\n" +
            "4. **Buffer time** — Arrive 10 minutes early instead of just on time.\n" +
            "5. **Batch errands** — Do shopping, printing, etc. between classes, not instead of them.\n" +
            "6. **Track your time** — Use a simple app or journal to see where your hours actually go.\n" +
            "7. **Sleep schedule** — Fix your sleep time; most attendance issues start with poor sleep.\n\n" +
            "💡 *Check your timetable in AttendMate to plan your week efficiently!*"
        ),
        QaEntry(
            listOf("competitive", "exam", "gate", "cat", "gre", "upsc", "preparation", "balance"),
            "How to balance attendance with competitive exam preparation?",
            "📚 **Balancing Attendance & Competitive Exams**\n\n" +
            "• **Don't sacrifice attendance entirely** — aim for 80-85% while preparing.\n" +
            "• Use the **what-if calculator** to figure out exactly how many classes you can strategically miss.\n" +
            "• **Prioritize overlap** — Attend classes whose topics align with your competitive exam syllabus.\n" +
            "• **Study during free periods** between classes instead of going home.\n" +
            "• **Weekends and evenings** should be your primary competitive exam prep time.\n" +
            "• **Communicate with professors** — Some may be flexible if they know you're preparing for GATE/CAT/GRE.\n" +
            "• **Front-load attendance** early in the semester to build buffer for exam season.\n\n" +
            "🎯 *Try asking: \"How many can I miss?\" to calculate your skip budget for exam prep weeks.*"
        ),
        QaEntry(
            listOf("internship", "work", "office", "industrial", "training", "absent"),
            "How to handle attendance when doing internships?",
            "💼 **Internships & Attendance**\n\n" +
            "• **University-approved internships** usually come with an **attendance exemption** or duty leave.\n" +
            "• Submit the internship offer letter and company certificate to your department **in advance**.\n" +
            "• Some colleges grant **On-Duty (OD)** status — you're marked present even while interning.\n" +
            "• For **self-arranged internships**, check if your college policy allows attendance relaxation.\n" +
            "• Maintain contact with classmates to get **notes and assignment updates** while away.\n" +
            "• Plan internships during **semester breaks** whenever possible to avoid attendance conflicts.\n\n" +
            "💡 *Mark internship days in AttendMate to keep your records accurate!*"
        ),
        QaEntry(
            listOf("below", "75", "already", "low", "recover", "salvage", "fix"),
            "What to do when you're already below 75%?",
            "🆘 **Already Below 75%? Here's Your Plan**\n\n" +
            "1. **Don't panic** — Calculate exactly where you stand using AttendMate's stats.\n" +
            "2. **Attend every single remaining class** — no more skips, period.\n" +
            "3. **Check condonation eligibility** — If you're above 65%, you may qualify.\n" +
            "4. **Meet your class advisor/HOD** — Explain your situation and ask for guidance.\n" +
            "5. **Gather medical/emergency documents** — If you have valid reasons for absences, submit them ASAP.\n" +
            "6. **Request extra classes** — Some professors offer makeup sessions near semester end.\n" +
            "7. **Use the prediction feature** — Ask \"Predict my attendance\" to see if recovery is possible.\n\n" +
            "⚠️ *Every class you attend from now directly improves your percentage. Stay committed!*"
        ),
        QaEntry(
            listOf("project", "submission", "week", "deadline", "assignment", "balance"),
            "How to maintain attendance during project submission week?",
            "📂 **Attendance During Project Weeks**\n\n" +
            "• **Start projects early** — Don't wait until the last week; this is the #1 reason students skip classes.\n" +
            "• **Attend classes and work on projects between classes** — Use free slots productively.\n" +
            "• **Divide work** if it's a group project — Not everyone needs to skip for the same task.\n" +
            "• **Ask professors for extensions** before skipping — Many will grant 2-3 extra days.\n" +
            "• **Night sessions** — It's better to lose sleep for 2-3 nights than lose attendance permanently.\n" +
            "• Use the **what-if calculator** to check if you can afford any misses during this period.\n\n" +
            "🎯 *Pro tip: Track project deadlines alongside your timetable for better planning!*"
        ),
        QaEntry(
            listOf("morning", "early", "class", "wake", "alarm", "8am", "9am"),
            "Tips for attending early morning classes?",
            "🌅 **Surviving Early Morning Classes**\n\n" +
            "1. **Fix your sleep schedule** — Go to bed by 11 PM to wake up fresh for 8 AM classes.\n" +
            "2. **Multiple alarms** — Set 3 alarms at 5-minute intervals. Place one across the room.\n" +
            "3. **Prep the night before** — Shower bag, iron clothes, charge phone — all done before sleeping.\n" +
            "4. **Morning motivation** — Keep breakfast ready or plan a coffee stop on the way.\n" +
            "5. **Accountability partner** — Find a classmate who'll call you if you don't show up.\n" +
            "6. **No all-nighters before morning classes** — It's a trap that ruins the entire next day.\n" +
            "7. **Sit near the window** — Natural light helps you stay awake during early lectures.\n\n" +
            "💡 *Check your timetable in AttendMate to know which days have early classes!*"
        ),
        QaEntry(
            listOf("track", "tracking", "monitor", "record", "effectively", "method"),
            "How to track attendance effectively?",
            "📋 **Effective Attendance Tracking**\n\n" +
            "• **Mark attendance immediately** after each class — don't rely on memory at the end of the day.\n" +
            "• **Use AttendMate** — It calculates your percentage automatically across all subjects.\n" +
            "• **Cross-verify** with your college portal or attendance register weekly.\n" +
            "• **Set weekly review reminders** — Every Sunday, check your stats and plan the coming week.\n" +
            "• **Track subject-wise** — Some subjects may need more attention than others.\n" +
            "• **Screenshot official records** — Keep proof in case of disputes.\n\n" +
            "💡 *AttendMate's trend analysis shows your attendance pattern over time — try asking \"Show my trend\"!*"
        ),

        // --- Technical & App Features ---

        QaEntry(
            listOf("predict", "prediction", "forecast", "algorithm", "regression", "ai"),
            "How does AttendMate predict attendance?",
            "🤖 **AttendMate's Prediction Engine**\n\n" +
            "• AttendMate uses **linear regression** on your historical attendance data.\n" +
            "• It analyzes your **attending pattern** (which days you tend to miss, recent trends).\n" +
            "• The model considers **remaining classes** in the semester to project your final percentage.\n" +
            "• Predictions update **dynamically** as you mark more classes.\n" +
            "• It flags subjects where you're **at risk** of falling below the threshold.\n" +
            "• All computation runs **100% on-device** — your data never leaves your phone.\n\n" +
            "🎯 *Try asking: \"Predict my attendance\" to see your personalized forecast!*"
        ),
        QaEntry(
            listOf("trend", "analysis", "pattern", "graph", "history", "chart"),
            "How does the trend analysis work?",
            "📉 **Trend Analysis in AttendMate**\n\n" +
            "• AttendMate tracks your **daily attendance marks** and plots them over time.\n" +
            "• It identifies patterns like: **\"You tend to miss Monday classes\"** or **\"Your attendance drops after holidays.\"**\n" +
            "• The trend line shows whether your attendance is **improving, stable, or declining**.\n" +
            "• It uses a **moving average** to smooth out day-to-day fluctuations.\n" +
            "• Streak data (consecutive present/absent) is also factored into the analysis.\n\n" +
            "💡 *Ask me: \"Show my trend\" or \"Analyze my pattern\" to see your detailed trends!*"
        ),
        QaEntry(
            listOf("skip", "budget", "miss", "allowance", "safe", "count", "remaining"),
            "What is the skip budget feature?",
            "🎫 **Skip Budget (Safe Misses)**\n\n" +
            "• The skip budget tells you **exactly how many classes you can miss** while staying above 75% (or your target).\n" +
            "• Formula: `Skip Budget = floor((Attended - Target% × Total) / Target%)`\n" +
            "• It's calculated **per subject** so you can plan strategically.\n" +
            "• When your skip budget is **0 or negative**, you cannot miss any more classes.\n" +
            "• The budget updates in real-time as you mark attendance.\n\n" +
            "💡 *Ask me: \"How many can I miss in [Subject]?\" or \"What's my skip budget?\"*"
        ),
        QaEntry(
            listOf("what-if", "calculator", "scenario", "simulate", "hypothetical"),
            "How does the what-if calculator work?",
            "🔮 **What-If Calculator**\n\n" +
            "• The what-if calculator lets you **simulate scenarios** before making decisions.\n" +
            "• Example: \"What if I miss 3 more classes in Math?\" → It shows your projected percentage.\n" +
            "• You can also simulate: \"What if I attend the next 10 classes?\" → Shows recovery path.\n" +
            "• It uses your **current attendance data** as the baseline for all calculations.\n" +
            "• Great for deciding whether to skip a class for exam prep, projects, or events.\n\n" +
            "🎯 *Try asking: \"What if I miss 2 classes?\" or \"What if I attend everything?\"*"
        ),
        QaEntry(
            listOf("ai", "chat", "assistant", "ask", "query", "chatbot", "help"),
            "How to use the AI chat effectively?",
            "💬 **Using AttendMate's AI Chat**\n\n" +
            "• The AI chat is your **personal attendance assistant** — it works 100% offline.\n" +
            "• **Best queries to try:**\n" +
            "  — \"Predict my attendance\" — Get a full semester forecast.\n" +
            "  — \"How many can I miss?\" — Check your skip budget per subject.\n" +
            "  — \"Show my trend\" — See your attendance pattern.\n" +
            "  — \"Motivate me\" — Get a personalized pep talk.\n" +
            "  — \"Tips for [topic]\" — Get specific advice.\n" +
            "• The AI uses **TF-IDF matching** to find the best answer from its knowledge base.\n" +
            "• Be **specific** in your questions for better results.\n\n" +
            "💡 *The more data you track in AttendMate, the smarter the AI responses become!*"
        ),
        QaEntry(
            listOf("college", "sync", "cloud", "backup", "data", "transfer"),
            "How does college sync work?",
            "🔄 **College Sync Feature**\n\n" +
            "• College sync allows you to **sync your attendance data** so it's backed up and accessible.\n" +
            "• Your timetable, subjects, and attendance records are preserved.\n" +
            "• Syncing ensures you **don't lose data** if you switch phones or reinstall the app.\n" +
            "• The sync process is designed to be **lightweight and fast**.\n" +
            "• Always sync after making significant attendance updates.\n\n" +
            "💡 *Keep your data safe by syncing regularly!*"
        ),
        QaEntry(
            listOf("streak", "importance", "benefit", "motivation", "reward", "gamify"),
            "What are attendance streaks and why do they matter?",
            "🔥 **Why Streaks Matter**\n\n" +
            "• Streaks track your **consecutive days of attendance** — like a mini-challenge.\n" +
            "• **Psychological benefit:** Maintaining a streak creates a **\"don't break the chain\"** motivation.\n" +
            "• Research shows habit formation takes **21-66 days** of consistent behavior.\n" +
            "• A 5-day streak might seem small, but it means you attended a **full week** — that's discipline!\n" +
            "• Longer streaks directly improve your percentage and build academic momentum.\n" +
            "• Breaking a streak hurts both your stats and your motivation — use it as fuel to stay consistent.\n\n" +
            "🎯 *Check your current streak in AttendMate and try to beat your personal record!*"
        ),

        // --- Mental Health & Motivation ---

        QaEntry(
            listOf("anxiety", "stress", "worried", "nervous", "panic", "attendance"),
            "How to deal with attendance anxiety?",
            "🧘 **Dealing with Attendance Anxiety**\n\n" +
            "• **Acknowledge the feeling** — Attendance anxiety is real and common among students.\n" +
            "• **Get the facts** — Use AttendMate to see your exact numbers. Often, reality is better than your fears.\n" +
            "• **Make a plan** — Anxiety reduces when you have a clear, actionable recovery plan.\n" +
            "• **Talk to someone** — Discuss with your class advisor, a counselor, or a trusted friend.\n" +
            "• **Avoid avoidance** — Skipping classes because you're anxious about attendance creates a vicious cycle.\n" +
            "• **One class at a time** — Don't think about the entire semester. Focus on attending the **next class**.\n" +
            "• **Celebrate small wins** — Attended 3 days in a row? That's progress!\n\n" +
            "💡 *Remember: Your worth is not defined by a percentage. But taking action will help you feel better.*"
        ),
        QaEntry(
            listOf("burnout", "exhaustion", "tired", "fatigue", "overwhelmed", "high"),
            "How to handle burnout from maintaining high attendance?",
            "😩 **Attendance Burnout**\n\n" +
            "• **It's valid** — Attending every single class while managing academics, social life, and personal growth is exhausting.\n" +
            "• **Use your skip budget wisely** — You don't need 100%. If 75% is the target, **strategic rest** is okay.\n" +
            "• **Quality over quantity** — Being physically present but mentally absent isn't productive. Take a planned day off.\n" +
            "• **Self-care days** — Plan one skip day per month (if your attendance allows) for recharging.\n" +
            "• **Vary your routine** — Sit in different spots, study in different places, take different routes.\n" +
            "• **Stay nourished** — Dehydration, poor diet, and sleep deprivation amplify burnout.\n\n" +
            "🎯 *Check your skip budget in AttendMate before planning a rest day!*"
        ),
        QaEntry(
            listOf("mental", "health", "day", "self-care", "rest", "break", "okay"),
            "Is it okay to take mental health days?",
            "💚 **Mental Health & Attendance**\n\n" +
            "• **Yes, your mental health matters** — sometimes a day off is necessary for long-term sustainability.\n" +
            "• **Check your skip budget first** — Make sure you can afford the absence without falling below threshold.\n" +
            "• **Make it intentional** — A mental health day should involve rest, self-care, or seeking help — not just scrolling social media.\n" +
            "• **Don't make it a habit** — Occasional rest is healthy; frequent avoidance may indicate a deeper issue.\n" +
            "• **Seek help if needed** — Most colleges have **free counseling services**. Use them.\n" +
            "• **Communicate** — If possible, inform your class advisor about extended mental health challenges.\n\n" +
            "⚠️ *If you're frequently struggling to attend, please reach out to a counselor. You don't have to fight alone.*"
        ),
        QaEntry(
            listOf("motivate", "motivation", "boring", "class", "uninteresting", "dull"),
            "How to motivate yourself to attend boring classes?",
            "🎯 **Motivation for Boring Classes**\n\n" +
            "1. **Reframe the purpose** — You're not attending for the topic; you're attending for your degree, career, and future.\n" +
            "2. **Challenge yourself** — Try to find ONE interesting thing per lecture. Make it a game.\n" +
            "3. **Sit in the front** — You'll be more engaged and less likely to zone out.\n" +
            "4. **Take creative notes** — Use mind maps, doodles, or color coding instead of plain notes.\n" +
            "5. **Think about attendance marks** — Those 5-15 internal marks could be the difference between grades.\n" +
            "6. **Reward system** — \"If I attend all classes today, I'll watch an episode of my favorite show.\"\n" +
            "7. **Study buddy** — Sit next to someone who keeps you engaged.\n\n" +
            "💡 *Track your streaks in AttendMate — don't break the chain!*"
        ),
        QaEntry(
            listOf("peer", "pressure", "bunk", "friends", "group", "skip", "influence"),
            "How to deal with peer pressure to bunk?",
            "👥 **Handling Peer Pressure to Bunk**\n\n" +
            "• **Know your numbers** — Use AttendMate to know exactly where you stand. Facts beat peer pressure.\n" +
            "• **Be honest** — \"I can't afford to miss this one\" is a perfectly valid reason. Real friends will understand.\n" +
            "• **Propose alternatives** — \"I'll come after class\" or \"Let's hang out during the free period instead.\"\n" +
            "• **Find like-minded friends** — Surround yourself with people who value their academics.\n" +
            "• **Remember the cost** — Your friends won't pay your condonation fee or attend your backlog exams.\n" +
            "• **Lead by example** — Often, your discipline inspires others too.\n\n" +
            "⚠️ *Check your skip budget before giving in: \"How many can I miss?\"*"
        ),

        // --- Miscellaneous ---

        QaEntry(
            listOf("online", "hybrid", "virtual", "zoom", "meet", "remote", "class"),
            "How do online/hybrid classes affect attendance?",
            "💻 **Online & Hybrid Class Attendance**\n\n" +
            "• **Online classes** are usually tracked via **login timestamps, poll responses, or camera checks**.\n" +
            "• **Hybrid mode** means some classes are online and some offline — attendance rules may differ for each.\n" +
            "• Common tracking methods: Zoom attendance reports, Google Meet logs, LMS login records.\n" +
            "• Some colleges require **camera ON** during online sessions for attendance to count.\n" +
            "• Just logging in is often not enough — you may need to **respond to random polls** or answer questions.\n" +
            "• The 75% rule still applies in most cases — online attendance is treated the same as physical.\n\n" +
            "💡 *Track online classes in AttendMate just like regular ones for accurate calculations!*"
        ),
        QaEntry(
            listOf("detained", "debarred", "status", "appeal", "review", "petition"),
            "What is detained/debarred status and how to appeal?",
            "🛑 **Detained/Debarred Status & Appeals**\n\n" +
            "• **Detained** = You cannot progress to the next semester; must repeat the current one.\n" +
            "• **Debarred** = You cannot appear for the current semester's exams.\n" +
            "• **How to appeal:**\n" +
            "  1. Write a formal **appeal letter** to the HOD/Dean explaining your circumstances.\n" +
            "  2. Attach **supporting documents** — medical certificates, family emergency proof, etc.\n" +
            "  3. Get **faculty recommendations** from professors who can vouch for you.\n" +
            "  4. Submit within the **appeal deadline** (usually 7-15 days after notification).\n" +
            "  5. Some universities have an **attendance review committee** that hears appeals.\n" +
            "• Appeals are not guaranteed to succeed — the committee decision is usually **final**.\n\n" +
            "⚠️ *Prevention is always better. Use AttendMate to never reach this stage!*"
        ),
        QaEntry(
            listOf("rights", "student", "legal", "rules", "regulation", "unfair"),
            "What are students' rights regarding attendance?",
            "⚖️ **Student Rights & Attendance**\n\n" +
            "• You have the right to **know the attendance policy** at the start of the semester.\n" +
            "• Colleges must **display attendance records** periodically (monthly or mid-semester).\n" +
            "• You can **challenge incorrect records** with evidence within the correction window.\n" +
            "• Medical and emergency absences must be considered if proper documentation is submitted.\n" +
            "• The **UGC mandates transparency** — you can request your attendance data under RTI if denied.\n" +
            "• **Anti-ragging committees** can intervene if attendance is affected by ragging/bullying.\n" +
            "• Some states have student **grievance cells** where you can file complaints about unfair practices.\n\n" +
            "💡 *Keep your own records in AttendMate as personal documentation.*"
        ),
        QaEntry(
            listOf("extra", "curricular", "sports", "nss", "ncc", "cultural", "exemption"),
            "Can I get attendance exemption for extra-curricular activities?",
            "🏅 **Extra-Curricular & Sports Exemptions**\n\n" +
            "• **University-level sports** participants usually get **On-Duty (OD) leave** for tournaments.\n" +
            "• **NCC and NSS** activities often carry attendance exemptions — typically 10-15 days per semester.\n" +
            "• **Cultural fests and technical events** may grant OD if you're an organizing committee member.\n" +
            "• **Process:** Get a prior approval letter signed by the activity coordinator and your HOD.\n" +
            "• OD status means you're **marked present** for those days — it doesn't count as absence.\n" +
            "• Keep **participation certificates** and event letters as proof.\n\n" +
            "💡 *Mark OD days as present in AttendMate to keep your records accurate!*"
        ),
        QaEntry(
            listOf("medical", "leave", "documentation", "certificate", "hospital", "doctor", "proof"),
            "What medical leave documentation is required?",
            "🏥 **Medical Leave Documentation**\n\n" +
            "• **Medical certificate** from a registered medical practitioner (MBBS minimum in most colleges).\n" +
            "• The certificate must include: **date of illness, diagnosis, and recommended rest period**.\n" +
            "• For extended leaves (7+ days), some colleges require a certificate from a **government hospital**.\n" +
            "• **Prescription and diagnostic reports** (blood tests, X-rays, etc.) strengthen your case.\n" +
            "• Submit documents **within 3-7 days** of returning to college (varies by institution).\n" +
            "• Some colleges have their own **medical leave application form** — fill it completely.\n" +
            "• For chronic conditions, get a **long-term medical certificate** and submit it to the Dean's office.\n\n" +
            "⚠️ *Even with medical leave, your attendance may not be marked 'present' — it's usually condoned, not counted.*"
        ),
        QaEntry(
            listOf("calculate", "safe", "bunk", "miss", "formula", "accurate", "number"),
            "How to calculate safe bunks accurately?",
            "🧮 **Accurate Safe Bunk Calculation**\n\n" +
            "• **Formula:** `Safe Bunks = floor((Attended - Required% × Total) / Required%)`\n" +
            "• **Example:** If you attended 45 out of 55 classes (81.8%) with a 75% requirement:\n" +
            "  — Safe Bunks = floor((45 - 0.75 × 55) / 0.75) = floor((45 - 41.25) / 0.75) = floor(5) = **5 classes**\n" +
            "• **Important:** This assumes the total also increases when you miss — so actual safe misses may be slightly less.\n" +
            "• **More accurate formula:** `Safe Bunks = floor((Attended / Required%) - Total)`\n" +
            "  — = floor(45/0.75 - 55) = floor(60 - 55) = **5 classes**\n" +
            "• **AttendMate uses this exact formula** to calculate your skip budget per subject.\n\n" +
            "💡 *Don't do math manually — just ask: \"How many can I miss in [Subject]?\"*"
        ),
        QaEntry(
            listOf("absent", "on-duty", "od", "present", "difference", "status"),
            "What is the difference between absent and on-duty?",
            "📌 **Absent vs. On-Duty (OD)**\n\n" +
            "• **Absent** — You were not in class and have **no approved reason**. Counts against your attendance.\n" +
            "• **On-Duty (OD)** — You were away for an **approved institutional activity**. Counts as **present**.\n" +
            "• Examples of OD-eligible activities: sports tournaments, NCC/NSS camps, paper presentations, hackathons, industrial visits.\n" +
            "• OD requires **prior approval** from your HOD with supporting documents.\n" +
            "• **Leave** — Different from both; it's a planned absence that may or may not affect attendance depending on type.\n" +
            "• Some colleges also have **Compensatory Off** — if you attend extra workshops, you get equivalent present marks.\n\n" +
            "💡 *In AttendMate, mark OD days as present to keep your calculations accurate!*"
        )
    )

    /**
     * Find the best matching answer for a given user query using TF-IDF-like scoring.
     */
    fun findBestAnswer(query: String): Pair<String, Float>? {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return null

        // Compute IDF: how rare each keyword is across all entries
        val totalDocs = ENTRIES.size
        val idf = mutableMapOf<String, Float>()
        val allKeywords = ENTRIES.flatMap { it.keywords }.toSet()
        for (kw in allKeywords) {
            val docsContaining = ENTRIES.count { entry -> entry.keywords.contains(kw) }
            idf[kw] = ln((totalDocs + 1f) / (docsContaining + 1f)) + 1f
        }

        // Score each entry
        var bestScore = 0f
        var bestEntry: QaEntry? = null

        for (entry in ENTRIES) {
            var score = 0f
            for (qt in queryTokens) {
                for (kw in entry.keywords) {
                    val matchScore = when {
                        qt == kw            -> 1.0f             // exact match
                        qt.startsWith(kw) || kw.startsWith(qt) -> 0.7f  // prefix match
                        levenshtein(qt, kw) <= 2 && kw.length > 3 -> 0.5f // fuzzy match
                        else -> 0f
                    }
                    if (matchScore > 0) {
                        score += matchScore * (idf[kw] ?: 1f)
                    }
                }
            }
            // Normalize by query size to avoid bias toward longer queries
            score /= queryTokens.size

            if (score > bestScore) {
                bestScore = score; bestEntry = entry
            }
        }

        val threshold = 0.4f
        return if (bestScore >= threshold && bestEntry != null) {
            val ans = if (bestScore >= 0.8f) "Confidence: High\n" + bestEntry.answer else bestEntry.answer
            Pair(ans, bestScore)
        } else null
    }

    /* ═══════════════════ HELPERS ═══════════════════ */

    private val STOP_WORDS = setOf("a", "an", "the", "is", "are", "i", "my", "me", "do", "does",
        "can", "will", "to", "of", "in", "for", "and", "or", "it", "what", "why", "how", "when")

    private fun tokenize(text: String): List<String> =
        text.lowercase().replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in STOP_WORDS }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        var cost = IntArray(lhs.length + 1) { it }
        var newCost = IntArray(lhs.length + 1) { 0 }
        for (i in 1..rhs.length) {
            newCost[0] = i
            for (j in 1..lhs.length) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                newCost[j] = minOf(cost[j] + 1, newCost[j - 1] + 1, cost[j - 1] + match)
            }
            val swap = cost; cost = newCost; newCost = swap
        }
        return cost[lhs.length]
    }
}
