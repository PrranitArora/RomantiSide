# RomantiSide: investor and product brief

**Product name:** RomantiSide, a play on *romanticize* and *side quests*. **Initial audience:** students and early-career professionals, initially adults aged 18+ using Android. **Research checked:** September 29, 2026. **Stage:** concept and demonstration, with no verified user traction, revenue, retention, or efficacy results yet.

This memo distinguishes **verified competitor facts** from **product proposals**, **commercial assumptions**, and **validation targets**. Competitor descriptions come from official company pages or developer-maintained store listings; they are not independent tests of those products. Prices and availability can vary by country, promotion, account, and time.

## The investable version of the idea

**RomantiSide gives students and young professionals one unexpected invitation each day to do something worthwhile in the real world.** A positive-psychology side quest arrives at a random moment inside a daytime window the user chooses. The curated activity bank works without a check-in, profile, or cloud service. Optional reflection and AI personalization can make later quests more relevant, and a playful camera helps the user keep a small moment afterward.

The strongest starting problem is the gap between wanting a more interesting or meaningful day and having the energy to choose what to do. The initial proposition is: “One small side quest, arriving as a surprise during your day.” Drained afternoons and feeling disconnected after moving for college or a first job provide concrete contexts to study. Random timing is a product hypothesis, not evidence that the app knows the best moment or that surprise improves wellbeing.

The original problem list spans several businesses. Apartment search, developing sorting algorithms, and capturing workplace institutional knowledge should stay outside the first product. Prioritization and getting started can be addressed through tiny actions, without expanding into a general life-management assistant.

The interesting investor question is whether this experience creates a repeatable behavior and a viable business. A cute interface and an AI conversation are already available in established products. RomantiSide needs evidence that its particular combination improves completion, continued use, or user-rated helpfulness enough to justify switching or paying.

## The product loop to build and demonstrate

1. **Enable daily side quests.** The user allows notifications and chooses a daytime window, initially 9 am–8 pm. Boundaries can be selected between 8 am and 9 pm. No check-in, account, cloud service, camera, or microphone is required for the basic loop.
2. **Receive one unexpected invitation.** Android chooses and saves one random time for the local day. A quest such as noticing an ordinary detail, expressing appreciation, or taking a small constructive step arrives in a notification. App launches and reboots preserve the selected time and do not add another automatic daily quest. Android can delay delivery.
3. **Act from the notification.** The demonstration offers “Done,” “30 minutes later,” and “Skip.” Completion does not require opening the home screen. A requested snooze can repeat the same quest; silence is not a failure.
4. **Keep a tiny wonder.** The optional camera makes noticing a real detail playful through a pastel palette and illustrated stickers. The current demonstration saves styled moments locally; additional postcard and original-image options remain expansion ideas.
5. **Reflect when wanted.** Inside the app, the user can record mood and energy, write a reflection, or start a short Claude conversation. A longer preference reflection can enrich the queue with generated activities. These are optional routes to personalization; no scheduled mood survey or check-in prompt interrupts the day.
6. **Learn what helped.** Ask occasionally in the app whether a quest helped. A future weekly review should report patterns cautiously: “You rated outdoor noticing quests helpful three times,” rather than claiming the activity caused a mood improvement.

The mission is to help the user do something in their life. Time spent chatting, taking photos, and opening the app are secondary measures, not the product's purpose.

## What the kawaii camera could become

The camera has a stronger role as a **savoring aid** than as a generic beauty filter. Its central prompt could be “Find one ordinary thing worth noticing.” A cup, a sunlit wall, or a leaf becomes a collectible postcard. The user supplies the meaning; the visual treatment makes the ritual enjoyable.

Start with original kawaii-inspired artwork, warm colors, rounded shapes, soft grain, and restrained animation. “Kawaii-inspired” describes an aesthetic direction more precisely than implying that Japanese culture has one visual style. Offer a calm, unstyled mode for users who dislike cute presentation.

| Feature proposal | User value | Practical starting point | Evidence still needed |
| --- | --- | --- | --- |
| Live cozy lens | Makes a familiar scene feel worth noticing | Local color and overlay effects | Does it increase completion or merely camera use? |
| Wonder hunt | Helps someone notice details around them | A prompt such as “find a warm color,” with manual completion | Is this enjoyable after the novelty wears off? |
| Scene-aware quest | Adapts a prompt to an optional camera frame | Detect broad objects or scene categories, with user correction | Does computer vision improve relevance over a simple user choice? |
| Memory postcard | Creates a personally meaningful keepsake | Local photo, short caption, optional date and sticker | Do users revisit it, and does it add value? |
| Weekly memory garden | Shows completed moments without punitive streaks | Grow a visual collection from chosen entries | Does it support return visits without obligation or guilt? |
| Optional image transformation | Creates a more stylized keepsake | A separate, explicitly requested still-image action | Will users pay enough to cover processing cost? |

Do not present a live overlay as generative scene transformation. The first is feasible and cheap enough for a simple demonstration; the second has different latency, cost, privacy, and quality requirements. Do not claim that a camera can reliably determine a person's internal emotional state. The user should be the authority on how they feel.

## Where AI and machine learning add value

**Useful first:** enrich an already useful offline quest loop when the user chooses personalization. A model can summarize an optional check-in, turn the user's words into editable context, suggest a suitable curated quest, and explain the recommendation. AI and mood surveys are not prerequisites for the daily invitation. A model should not invent unreviewed psychological prescriptions whenever the user is vulnerable.

**Implemented demonstration:** the Android app offers an optional Claude text conversation through a local development backend. Android voice transcription can supply the same conversation's text input. The backend keeps the provider key off the device and uses a temporary chat session separate from Circle. Ordinary check-in recommendations use the existing curated library. A separate longer reflection extracts a bounded preference profile and builds a visible system prompt for review; confirmed preferences, selected mood/energy, and previous activities can then inform up to three new generated quests. The server validates candidate fields, assigns fixed principle and research metadata, and filters repeated actions and common paraphrases; the client retains local generated-activity history. Generated quests retire after completion or skipping, while the curated offline bank can recur on another day. Semantic duplicate detection is incomplete, and a research link does not establish that a generated activity is clinically reviewed or effective. Users opt into sending text and selected context to the backend and Anthropic, then confirm reflections and ratings saved locally. The backend does not persist chat transcripts or preference profiles. The offline check-in remains available; cloud-model availability, output quality, retention, and clinical benefit are separate questions that the implementation alone does not answer.

**Useful after sufficient data:** estimate which approved quest and delivery window a user is likely to find helpful. Optimize for user-rated usefulness and completed offline action, with notification burden as a constraint. Compare that model against a simple rules-based baseline before claiming an AI advantage.

**Optional computer vision:** detect broad scene elements to support a user-requested noticing exercise, or place visual decoration. Prefer local processing where practical. Avoid continuous camera monitoring, identity recognition, and mood scoring from facial expressions.

**Voice design:** “two-minute call” means an optional in-app interaction the user starts, using Android transcription and text conversation. The app does not schedule check-in invitations, automatically activate the microphone, or place calls. Real phone calls and SMS would be later, separately consented channels with their own operating costs.

**Personalization memory:** let users inspect, edit, and delete remembered preferences. “I prefer indoor quests” is more useful and easier to verify than an opaque label such as “emotionally avoidant.” Keep inferred state separate from explicit self-report.

## Competitor landscape: verified features and implications

All rows below were checked on **September 29, 2026**. “Potential opening” is our hypothesis, not an assertion that a competitor lacks a feature. This is a focused landscape, not an exhaustive app-store survey.

| Product | Verified official positioning and features | Competitive implication for RomantiSide | Potential opening to test |
| --- | --- | --- | --- |
| **Finch** | Its Android listing describes a self-care pet, personalized daily exercises, morning mood checks, goals, journaling, breathing, gratitude, mood trends, and combined insights. The listing displays 10M+ downloads; this is a store download bucket, not active users or paying customers. [Official Google Play listing](https://play.google.com/store/apps/details?id=com.finch.finch) | This is the closest direct competitor. A cute companion plus tiny self-care actions is an established proposition. | Can action completion directly from notifications and real-world visual noticing create a distinct habit people prefer? |
| **Fabulous** | Describes behavioral-science-based habit building, morning/afternoon/evening routines, brief coaching content, community, and optional human coaching. Its page lists multiple subscription durations without a single universal price. [Official product page](https://www.thefabulous.co/landing/) | Routines, behavioral science, and a short daily coaching experience do not establish uniqueness. | Does adapting to today's energy outperform a more planned routine for this audience? |
| **Daylio** | Supports quick mood/activity entries, notes, photos, voice memos, charts and correlations, goals, reminders, and exports. Its site states that journal entries are not sent to its servers. [Official product page](https://daylio.net/) | Low-friction logging, trends, and a privacy-oriented position are already available. | Can the product turn a check-in into an immediately useful next action while remaining comparably simple? |
| **Wysa** | Describes AI conversation plus a library of exercises. Its FAQ distinguishes rule-based and mixed LLM experiences across deployments and lists optional human support in some offerings. It explicitly positions the app as wellbeing support rather than diagnosis. [Official FAQ](https://www.wysa.com/faq) | “AI that listens and suggests coping exercises” is already a developed category. | Can a short, bounded check-in and playful everyday action attract people who do not want extended conversation? |
| **Headspace Ebb** | Offers voice or text conversations, conversation memory, and personalized recommendations for activities and Headspace content. Its page lists paid members aged 18+ in the US, UK, Canada, and Australia, in English. [Official Ebb page](https://www.headspace.com/ai-mental-health-companion) | Voice plus text plus memory is no longer a novel pitch. A substantial existing content library is a competitive strength. | Can a notification-led offline ritual win a narrower recurring moment? |
| **Habitica** | Turns completed tasks into gold, experience, and equipment, with friend parties, quests, challenges, and Android/iOS apps. [Official features](https://habitica.com/static/features) | Gamifying life and calling activities quests are established ideas. | Can gentle, mood-aware actions serve users who prefer a lighter experience and no penalty for skipping? |
| **Snapchat / Lens Studio** | Snap provides AR creation tools, face templates, ML capabilities, and integrations through Camera Kit. It also offers generative tools, with platform limitations. [Official Lens Studio overview](https://developers.snap.com/lens-studio/overview/getting-started/what-is-lens-studio), [AI tools overview](https://developers.snap.com/lens-studio/features/lens-studio-ai/overview) | Visual novelty alone faces powerful existing substitutes and readily available creation tools. | Does connecting a camera ritual to a personally helpful action provide enduring value beyond the effect itself? |

There is material price pressure in the student segment: Headspace's official student page currently advertises **US$9.99 per year** for eligible verified students, with subsequent eligibility and renewal conditions. RomantiSide cannot assume students will pay a large premium for generic wellbeing content. [Headspace student plan](https://www.headspace.com/studentplan)

### Differentiation is a set of hypotheses

1. **Moment:** one random daytime invitation gives an ordinary day a small element of surprise, within a window the user controls.
2. **Interaction:** a useful action arrives and can be completed through a notification without a preceding mood survey.
3. **Experience:** a short real-world ritual ends with something the user noticed or made.
4. **Learning:** the system learns which actions the individual finds helpful, without pretending to read their mind.
5. **Trust:** preferences and records remain understandable and controllable.

Any competitor could copy individual features. A credible longer-term advantage would require retained users, trusted distribution, distinctive original content, and evidence that consented personalization improves useful outcomes. A collection of sensitive conversations does not automatically create a defensible business.

## Android makes a credible initial demonstration

Android supports notification actions, but delivery depends on user permission. New installations on Android 13 and above need the relevant runtime grant before ordinary notifications can be sent. The demonstration includes quest actions and an in-app activity queue when notifications are unavailable; check-ins are in-app only. [Android notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission), [Creating notifications and actions](https://developer.android.com/develop/ui/compose/notifications/create-notification)

The pitch demonstration should show the primary journey first: enable quests, select a daytime window, receive a quest notification, complete or snooze it, and optionally capture a camera moment. Show the separate check-in and personalization flows afterward. Label a test notification as a preview rather than pretending the random schedule has already fired. A curated offline recommendation is sufficient for testing the core experience; it should not be presented as a trained personalization model. The accompanying README defines the implemented capabilities.

A demonstration is separate from a production service. The current Claude integration exchanges text, including optional Android speech transcripts, through a development backend. It does not place calls or provide a real-time audio conversation. Reliable scheduling across real devices, production hosting and access controls, richer voice interaction, billing, account recovery, and policy review require additional engineering and validation before a public launch.

## A practical initial business model

**Proposed:** a useful free daily quest with optional paid personalization, expanded themes, more keepsake formats, and a bounded voice allowance. Keep the curated quest bank, basic check-ins, skipping, privacy controls, and access to help available without a purchase. Test cosmetic purchases separately from subscriptions rather than assuming every user wants recurring billing.

**Candidate price tests, not recommended final prices:** an early-career tier at $39–$59 annually, a lower student offer, and an optional monthly plan. Test actual purchase behavior with clear terms; stated willingness to pay is weak evidence. These price points may fail, especially against student discounts and free alternatives.

Start with native notifications. They avoid per-message carrier costs, fit the core loop, and allow user control. SMS may later help a specific group, but adds cost and sensitive lock-screen content concerns. It should be an intentional opt-in product decision, not the default demonstration mechanism.

Do not build the first model around advertising targeted to someone's mood. A campus or employer can eventually sponsor access, but the individual's private reflections should not become a manager or administrator dashboard. Institutional sales also create a separate buying process that the consumer demo does not validate.

## Bottom-up economics: an illustrative sensitivity model

**Every demand and cost input below is an assumption, not observed performance, market size, a vendor quote, or a forecast.** The point is to expose what must be measured.

Assume 10,000 monthly active users; blended realized subscription price of $48 per paying user per year, equivalent to $4 per month; a 15% store/billing allowance; paid-user serving cost of $1 per month; free-user serving cost of $0.05 per month. Serving cost here includes an assumed allocation for inference and variable support/infrastructure, but excludes salaries, fixed operations, taxes, refunds, acquisition, and research. The very low free cost assumes local/rules-based features and no unrestricted cloud voice.

For context, Google's current fee page describes a 10% subscription service fee plus a 5% billing fee for Google Play Billing in specified markets, with different regional arrangements and a 15% subscription schedule in remaining markets pending rollout. The model's 15% is a simplifying allowance, not a universal statement about all purchases. [Official Google Play fees](https://support.google.com/googleplay/android-developer/answer/112622?hl=en)

| Assumed paying share of MAU | Paying users | Gross subscription run-rate / month | Store/billing allowance | Paid-user serving cost | Free-user serving cost | Contribution / month before excluded costs |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 3% | 300 | $1,200 | $180 | $300 | $485 | **$235** |
| 6% | 600 | $2,400 | $360 | $600 | $470 | **$970** |
| 10% | 1,000 | $4,000 | $600 | $1,000 | $450 | **$1,950** |

Formula: `MAU × paid_share × (monthly_price × (1 − fee) − paid_cost) − MAU × (1 − paid_share) × free_cost`.

In the 6% case, gross annualized subscription run-rate is $28,800 and annualized contribution is $11,640 before all excluded costs. If paid serving cost rises from $1 to $3 per month because of frequent voice use, the same scenario becomes **−$230 per month** before fixed costs. Voice pricing and usage caps are therefore economic design decisions.

At $48 realized annual price, 100,000 paying customers would represent $4.8 million in gross annualized subscription run-rate. At a 6% paid share, that implies roughly **1.67 million monthly active users**. This is arithmetic demonstrating the scale required, not a reachable-market claim. A small campus pilot does not establish a path to that scale.

Measure acquisition cost per **retained paying customer**, not per download. Use actual renewal cohorts before presenting lifetime value. Annual subscriptions, refunds, discounts, churn, free users, and student graduation all make a single simplistic LTV number misleading at this stage.

## Go-to-market: earn one repeatable channel

**Initial cohort proposal:** adults at one or two campuses and early-career communities with familiar, repetitive weekday routines. Recruit around the concrete experience: “one small side quest, arriving as a surprise during your day.” Include users who already use Finch or Daylio as well as people who have abandoned wellness apps.

**Early channels to test:** student clubs, study communities, creator demonstrations of ordinary-to-cozy moments, and small early-career peer groups. Each is a hypothesis. Record recruiter effort, incentives, creator spend, installs, activation, four-week retained users, and purchases. A founder personally reminding users is useful research but must be counted separately from autonomous product use.

**Sharing proposal:** let users export an optional postcard without mood scores, personal reflections, precise location, or pressure to post. Track whether sharing yields activated users rather than merely views. Social sharing should be a user-selected artifact, not a condition for earning a reward.

**Expansion sequence:** validate direct consumer use first; test a repeatable creator or community channel second; explore small sponsored cohorts after retention and trust are credible. A university procurement strategy should follow buyer interviews and a defined budget owner, not substitute for consumer demand.

## Validation milestones and decision gates

These are **proposed internal decision targets**, not universal investor benchmarks. Precommit to definitions and examine uncertainty, subgroup differences, and reasons for dropping out.

| Stage | Work | Suggested gate | What the evidence would establish |
| --- | --- | --- | --- |
| Problem discovery | 25–30 interviews across students and early-career professionals; ask about a recent actual day, current workarounds, and abandoned apps | A repeated, specific trigger and clear dissatisfaction with current options | Whether the proposed problem exists in the intended segment; not market size |
| Usability | 10–15 adults attempt the primary Android flow without coaching | At least 80% enable quests, choose a window, and respond to a test quest; document every blocker | Whether users understand the interaction; not retention |
| Four-week pilot | Around 100 adults, with cohort-level reporting and agreed notification limits | Candidate targets: 60% action activation after the first delivered invitation; 30% of activated users completing at least one quest in days 22–28 | Early usefulness and behavior persistence; estimates will be noisy |
| Test the differentiation | Compare a random time in the chosen window with a user-selected fixed time at the same one-quest daily burden; separately test optional personalization and camera use | Predefine the primary outcome and sample size before collection | Whether surprise, personalization, or camera adds value beyond the basic activity |
| Test willingness to pay | Offer a clearly priced plan after users experience the core value | Observe paid conversion, cancellations, and eventual renewal; do not substitute survey intent | Whether a business model is plausible for each segment |
| Test wellbeing | Use a validated measure, a prespecified follow-up interval, and a suitable comparison design with expert input | Report changes with uncertainty, attrition, missingness, and adverse feedback | Whether there is evidence beyond engagement; a before/after chart alone is insufficient |

**Metric definitions:** action activation = a first completed quest within 24 hours of the first delivered daily invitation; report notification permission and successful delivery separately so excluded users remain visible. A check-in is not required. Four-week action retention = the share of activated users completing at least one quest in days 22–28 after activation. Notification action rate = actions taken divided by successfully delivered notifications, with dismissals and disablement tracked separately. Primary daily usefulness = “Was that worth the interruption?” asked sparingly inside the app. Raw app opens should not determine success.

Run small pilots to discover problems and estimate variability. Do not claim that 100 participants automatically provide enough power to prove a wellbeing effect. The eventual study size should follow the chosen outcome and effect estimate.

**Stop or change direction if:** the camera increases app time without useful action; most users disable reminders; personalization performs no better than a simple menu; people return primarily because of guilt; or willingness to pay cannot cover serving and distribution costs. Removing an attractive feature can be progress if it clarifies the useful product.

## Questions an investor will ask

| Objection | Honest answer now | Evidence to bring next |
| --- | --- | --- |
| “Is this Finch with AI?” | There is real overlap. Our hypothesis concerns the notification-to-real-world-action loop and visual noticing ritual. | Head-to-head user preferences, retained action rates, and switching reasons |
| “Why does this need AI?” | It may not need much initially. AI should improve relevance and reduce effort, measured against rules. | A controlled improvement in usefulness or completion that exceeds extra cost |
| “Will the novelty disappear?” | It could. A first-session reaction cannot answer that. | Four-, eight-, and twelve-week cohorts, including camera-free variants |
| “Do students pay?” | Not yet established, with strong low-price competition. | Actual segmented conversion and renewal, plus affordable distribution |
| “Can you prove people are happier?” | Not from usage, inferred emotion, or an uncontrolled mood trend. | A suitable validated measure and comparison study; transparent attrition |
| “How do you avoid overwhelming users?” | One automatic quest per day, a user-selected daytime window, quiet hours, easy skipping, and in-app-only optional surveys. | Notification disablement, burden reports, and longitudinal usage |
| “What prevents copying?” | No strong moat exists at concept stage. | Trusted distribution, original content, retained community, and demonstrably useful personalization |
| “Is the camera essential?” | That is a testable question. It may be an acquisition feature, a retention feature, or expendable. | Incremental benefit over an otherwise identical experience |
| “What does this become at scale?” | A personalized everyday action companion, if one recurring use case first proves valuable. | Repeatable retention and unit economics before adding new surfaces |

## Expansion features, ordered by what they would teach us

1. **Low-energy mode:** an optional one-tap preference for extremely small quests, without requiring a check-in. Tests whether the product works when motivation is lowest.
2. **Context choices:** “at my desk,” “at home,” “outside,” “with people,” and “only one minute.” Improves relevance without requiring passive surveillance.
3. **Theme packs:** exam week, first job, moving to a new city, Sunday reset, and creative recovery. Tests recurring situations before widening the audience.
4. **Quest swaps with reasons:** “too much effort,” “not accessible,” “wrong time,” or “not for me.” Produces useful preference data and restores user control.
5. **Consent-based buddy quests:** two friends independently accept a walk, gratitude exchange, or shared break. Measure benefit without adding public leaderboards.
6. **Weekly personal experiments:** the user chooses between two preferred activities and reviews their own feedback. Present associations and uncertainty honestly.
7. **Optional calendar windows:** permission-based availability, avoiding storage of sensitive event descriptions where unnecessary.
8. **Widgets and later wearable actions:** reduce interaction cost after notifications prove useful.
9. **Creator or artist packs:** original licensed visual worlds and quest collections, assessed for quality and tone. This could support differentiated content, but is not a marketplace until supply and demand exist.
10. **Sponsored access:** carefully separated payer and user experiences, with no individual emotional records exposed to institutions.

## A sixty-second pitch

“RomantiSide helps students and young professionals make an ordinary day a little more interesting. Once a day, at a random moment inside a window you choose, a notification invites you on one small side quest. It might be noticing something beautiful, appreciating a friend, or taking a small step toward something you care about. You can complete or skip it without opening the app or filling out a mood survey. Optional reflection and AI personalization help shape future quests, and a cozy camera turns a moment into a personal keepsake.

“Apps such as Finch and Headspace show that playful self-care and AI reflection already have competition. Our hypothesis is that one unexpected, manageable invitation into the real world can earn a place in people's day. Our Android demonstration works from an offline activity bank, with personalization available when wanted. Next we will test retained quest completion, notification burden, willingness to pay, and wellbeing separately in a focused pilot.”

## The fundraising story to earn

Pitch the demonstration as the beginning of validation. The strongest early materials will be a crisp problem, an honest competitor comparison, a functioning end-to-end experience, and a plan to test the uncertain parts. Replace hypothetical numbers with measured cohorts as soon as possible.

A funding ask should follow a real operating budget and explicit milestones: reliable Android delivery, a reviewed quest library, a focused pilot, evidence of retention, and an initial paid offer. The amount, runway, and valuation cannot be responsibly derived from the concept alone. The central claim to earn is simple: **people repeatedly find these small actions helpful enough to keep using—and some to pay for—RomantiSide.**
