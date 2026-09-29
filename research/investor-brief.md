# RomantiSide: investor and product brief

**Product name:** RomantiSide, a play on *romanticize* and *side quests*. **Initial audience:** students and early-career professionals, initially adults aged 18+ using Android. **Research checked:** September 29, 2026. **Stage:** concept and demonstration, with no verified user traction, revenue, retention, or efficacy results yet.

This memo distinguishes **verified competitor facts** from **product proposals**, **commercial assumptions**, and **validation targets**. Competitor descriptions come from official company pages or developer-maintained store listings; they are not independent tests of those products. Prices and availability can vary by country, promotion, account, and time.

## The investable version of the idea

**RomantiSide helps a drained student or young professional take one small, personally relevant action that makes an ordinary day feel more meaningful.** A brief check-in informs a queue of optional real-world side quests. The action arrives in a notification; a playful camera helps the user notice and keep a small moment afterward.

The strongest starting problem is the gap between wanting to feel better and having the energy to choose what to do. The initial proposition is: “When your day feels flat, we give you one achievable next step.” The afternoon slump is a useful recurring entry point. Feeling disconnected after moving for college or a first job provides a second concrete context.

The original problem list spans several businesses. Apartment search, developing sorting algorithms, and capturing workplace institutional knowledge should stay outside the first product. Prioritization and getting started can be addressed through tiny actions, without expanding into a general life-management assistant.

The interesting investor question is whether this experience creates a repeatable behavior and a viable business. A cute interface and an AI conversation are already available in established products. RomantiSide needs evidence that its particular combination improves completion, continued use, or user-rated helpfulness enough to justify switching or paying.

## The product loop to build and demonstrate

1. **Choose the moment.** The user selects a check-in window, quiet hours, preferred tone, and a daily notification limit. They can start without granting camera or microphone access.
2. **Check in for up to two minutes.** A few text prompts or a user-initiated voice conversation ask about mood, energy, and the main friction today. The user confirms or edits the app's summary. A twenty-second alternative is always available.
3. **Receive one suitable quest.** The system picks from a reviewed library using the user's reported state, time available, accessibility preferences, and past feedback. A low-energy day could produce “Notice one pleasing color near your desk for thirty seconds.” A lonely day could offer “Send someone one specific thank-you.”
4. **Act from the notification.** Useful options are “Done,” “Later,” “Swap,” and “Skip.” Completion should not require opening the home screen. A short reflection can be optional; silence is not a failure.
5. **Keep a tiny wonder.** When appropriate, the camera adds a pastel palette, gentle sparkles, illustrated stickers, or a postcard frame to an ordinary scene. The user can save the original, the styled image, both, or neither.
6. **Learn what helped.** Ask occasionally whether a quest helped and how the person feels afterward. A weekly review reports patterns cautiously: “You rated outdoor noticing quests helpful three times,” rather than claiming the activity caused a mood improvement.

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

**Useful first:** summarize a check-in, turn the user's words into editable context, retrieve a suitable reviewed quest, vary its wording, and explain why it was offered. Rules can do much of the first version. A model should not invent unreviewed psychological prescriptions whenever the user is vulnerable.

**Useful after sufficient data:** estimate which approved quest and delivery window a user is likely to find helpful. Optimize for user-rated usefulness and completed offline action, with notification burden as a constraint. Compare that model against a simple rules-based baseline before claiming an AI advantage.

**Optional computer vision:** detect broad scene elements to support a user-requested noticing exercise, or place visual decoration. Prefer local processing where practical. Avoid continuous camera monitoring, identity recognition, and mood scoring from facial expressions.

**Voice design:** “two-minute call” should initially mean an optional in-app voice session initiated by the user. Automatic check-in invitations do not imply automatically activating a microphone or placing a telephone call. Real phone calls and SMS would be a later, separately consented channel with its own operating costs.

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

1. **Moment:** drained afternoons and everyday disconnection provide a focused entry point.
2. **Interaction:** useful actions can arrive and be completed through notifications.
3. **Experience:** a short real-world ritual ends with something the user noticed or made.
4. **Learning:** the system learns which actions the individual finds helpful, without pretending to read their mind.
5. **Trust:** preferences and records remain understandable and controllable.

Any competitor could copy individual features. A credible longer-term advantage would require retained users, trusted distribution, distinctive original content, and evidence that consented personalization improves useful outcomes. A collection of sensitive conversations does not automatically create a defensible business.

## Android makes a credible initial demonstration

Android can support notification actions, including direct reply, but notifications depend on user permission. New installations on Android 13 and above need the relevant runtime grant before ordinary notifications can be sent. The demonstration should visibly include permission handling and a useful fallback when it is denied. [Android notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission), [Creating notifications and actions](https://developer.android.com/develop/ui/compose/notifications/create-notification)

The pitch demonstration should show a single complete journey: a check-in, a matched quest, its notification, completion or snoozing, and a camera moment. Show actual implemented behavior and label simulations. A rules-based recommendation is perfectly acceptable for testing whether the experience is wanted; it should not be presented as a trained personalization model. The accompanying project README should define exactly which capabilities the delivered build implements.

A demonstration is separate from a production service. Reliable scheduling across real devices, actual cloud voice conversations, billing, account recovery, and policy review would require additional engineering and validation before a public launch.

## A practical initial business model

**Proposed:** a useful free core with optional paid personalization, expanded themes, more keepsake formats, and a bounded voice allowance. Keep core check-ins, skipping, privacy controls, and access to help available without a purchase. Test cosmetic purchases separately from subscriptions rather than assuming every user wants recurring billing.

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

**Initial cohort proposal:** adults at one or two campuses and early-career communities experiencing similar afternoon routines. Recruit around a specific moment—“make your 3 p.m. reset a little better”—rather than broad mental-health promises. Include users who already use Finch or Daylio as well as people who have abandoned wellness apps.

**Early channels to test:** student clubs, study communities, creator demonstrations of ordinary-to-cozy moments, and small early-career peer groups. Each is a hypothesis. Record recruiter effort, incentives, creator spend, installs, activation, four-week retained users, and purchases. A founder personally reminding users is useful research but must be counted separately from autonomous product use.

**Sharing proposal:** let users export an optional postcard without mood scores, personal reflections, precise location, or pressure to post. Track whether sharing yields activated users rather than merely views. Social sharing should be a user-selected artifact, not a condition for earning a reward.

**Expansion sequence:** validate direct consumer use first; test a repeatable creator or community channel second; explore small sponsored cohorts after retention and trust are credible. A university procurement strategy should follow buyer interviews and a defined budget owner, not substitute for consumer demand.

## Validation milestones and decision gates

These are **proposed internal decision targets**, not universal investor benchmarks. Precommit to definitions and examine uncertainty, subgroup differences, and reasons for dropping out.

| Stage | Work | Suggested gate | What the evidence would establish |
| --- | --- | --- | --- |
| Problem discovery | 25–30 interviews across students and early-career professionals; ask about a recent actual day, current workarounds, and abandoned apps | A repeated, specific trigger and clear dissatisfaction with current options | Whether the proposed problem exists in the intended segment; not market size |
| Usability | 10–15 adults attempt the full Android flow without coaching | At least 80% complete a check-in and respond to a quest; document every blocker | Whether users understand the interaction; not retention |
| Four-week pilot | Around 100 adults, with cohort-level reporting and agreed notification limits | Candidate targets: 60% first-day activation; 30% of activated users completing at least one quest in days 22–28 | Early usefulness and behavior persistence; estimates will be noisy |
| Test the differentiation | Randomize a generic quest notification versus a state-matched quest; separately test whether the camera ritual adds value | Predefine the primary outcome and sample size before collection | Whether personalization or camera adds benefit beyond the basic reminder |
| Test willingness to pay | Offer a clearly priced plan after users experience the core value | Observe paid conversion, cancellations, and eventual renewal; do not substitute survey intent | Whether a business model is plausible for each segment |
| Test wellbeing | Use a validated measure, a prespecified follow-up interval, and a suitable comparison design with expert input | Report changes with uncertainty, attrition, missingness, and adverse feedback | Whether there is evidence beyond engagement; a before/after chart alone is insufficient |

**Metric definitions:** activation = a completed check-in plus a completed first quest within 24 hours. Four-week action retention = the share of activated users completing at least one quest in days 22–28. Notification action rate = actions taken divided by successfully delivered notifications, with dismissals and disablement tracked separately. Primary daily usefulness = “Was that worth the interruption?” asked sparingly. Raw app opens should not determine success.

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
| “How do you avoid overwhelming users?” | User-selected windows, a small queue, quiet hours, easy skipping, and feedback on interruptions. | Notification disablement, burden reports, and longitudinal usage |
| “What prevents copying?” | No strong moat exists at concept stage. | Trusted distribution, original content, retained community, and demonstrably useful personalization |
| “Is the camera essential?” | That is a testable question. It may be an acquisition feature, a retention feature, or expendable. | Incremental benefit over an otherwise identical experience |
| “What does this become at scale?” | A personalized everyday action companion, if one recurring use case first proves valuable. | Repeatable retention and unit economics before adding new surfaces |

## Expansion features, ordered by what they would teach us

1. **Low-energy mode:** a thirty-second check-in and one extremely small quest. Tests whether the product works when motivation is lowest.
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

“RomantiSide helps students and young professionals turn flat, disconnected moments into small actions that feel worthwhile. You check in for up to two minutes by voice or text, confirm how you're feeling, and receive one achievable side quest through a notification. It might be noticing something beautiful, connecting with a friend, or taking a small step toward something you care about. An optional cozy camera turns an ordinary moment into a personal keepsake.

“Apps such as Finch and Headspace show that playful self-care and AI reflection already have competition. Our hypothesis is that timely, personally relevant actions in the real world can become a useful daily habit. We're building an Android demonstration and will test retained quest completion, willingness to pay, and wellbeing separately. The next milestone is a focused pilot that tells us whether this experience earns a lasting place in people's day.”

## The fundraising story to earn

Pitch the demonstration as the beginning of validation. The strongest early materials will be a crisp problem, an honest competitor comparison, a functioning end-to-end experience, and a plan to test the uncertain parts. Replace hypothetical numbers with measured cohorts as soon as possible.

A funding ask should follow a real operating budget and explicit milestones: reliable Android delivery, a reviewed quest library, a focused pilot, evidence of retention, and an initial paid offer. The amount, runway, and valuation cannot be responsibly derived from the concept alone. The central claim to earn is simple: **people repeatedly find these small actions helpful enough to keep using—and some to pay for—RomantiSide.**
