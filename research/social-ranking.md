# Private side quest rankings

This feature adds an optional social loop to the RomantiSide demo. A user can compare completed side quests with friends who explicitly join and exchange private codes. The purpose is encouragement and shared activity; completion counts do not measure happiness, health, or personal worth.

## Product behavior

The Circle tab offers two views: the last seven days and all time. People with equal counts receive the same rank. There are no lost streaks or penalties for quiet days. The user can leave and delete their shared profile, events, and friend relationships.

A native contact picker lets the user choose one existing contact as a local display label. The app does not read the whole address book, upload phone numbers, send invitations, or silently add people. The selected person's own private friend code is still required. A code connects both participants, and each participant's connected friends can see their chosen display name and completion counts. The interface explains this before joining or adding someone. Possession of a code is the invitation mechanism; this prototype does not verify phone ownership or contact identity.

Only a display name and completion event identifiers/timestamps reach the development service. Mood, energy, journals, voice transcripts, photos, and contact labels remain outside the social API. Counts refresh when a participant opens or refreshes Circle, including completions made from notifications. The app shows an explicitly labelled fictional preview if the user chooses to see sample rankings.

## Investor rationale and uncertainty

A proposed growth loop is: a user enjoys a quest, voluntarily connects with a friend, and each sees a gentle reason to return. This may make the experience easier to share and help retention, but it is a hypothesis. The current implementation has no growth or effectiveness results.

Social comparison can plausibly motivate some users and discourage others. Do not infer that a leaderboard improves wellbeing merely because it increases app opens or quest counts. Offer the feature as optional, keep private check-ins out of it, and test an alternative that shows shared group progress without ranking people.

## Proposed validation experiment

Randomize consenting groups of friends to a ranking view or a nonranked shared-progress view. Measure completed offline activities, perceived encouragement, pressure or guilt, muting/leaving, and retention. Account for friendship-group clustering in the analysis. Gather qualitative feedback from people who stop using the feature. Predefine success criteria and stop criteria with a research advisor before recruitment; do not optimize for maximum competition alone.

Before a public launch, add recoverable accounts, stronger invitation and removal controls, rate-limit monitoring, HTTPS operations, deletion verification, and abuse/cheating controls. The development API trusts authenticated client-reported completions and therefore supports product demonstration rather than prizes or competitive rewards.
