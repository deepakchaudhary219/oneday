# 05 · Engagement Psychology: Why People Come Back

> **Purpose.** OneDay has to be something people *want* to open every day. That is how it beats Snapchat and the swipe apps. This document explains the human psychology each feature is built on, maps every engagement feature to a specific mechanism, and draws the line between **pull** (people come back because something real is waiting) and **compulsion** (people come back because the product engineered an itch).
>
> It extends blueprint v2 §2 (principles) and §12 (metrics). The psychology principles of the original blueprint (§5–§6, §22–§23, §27, §30) still apply.

---

## 0. The strategic choice: pull, not compulsion

We want users opening OneDay more often and for longer than they open Snapchat or Tinder. There are two ways to get there.

| | **Compulsion design** (slot machine) | **Pull design** (what OneDay builds) |
|---|---|---|
| What brings people back | An itch the product created: streak anxiety, infinite feeds, fake or bait notifications | Something real is waiting: a person, an answer, a plan, a city that is alive tonight |
| Reward | Variable and often empty | Variable, but always *substantive* (a real person, a real answer) |
| How sessions feel | Long and regretted | Shorter, frequent and satisfying, with a natural "caught up" end |
| Long-term retention | Burns out. Users churn and delete, and the brand becomes "toxic". | Compounds: every session creates relationships that bring people back |
| Regulatory exposure | High (see below) | Low |
| Effect on women's safety and trust | Corrosive | Core to the product |

**Why compulsion is the wrong bet for OneDay specifically:**

1. **It is increasingly illegal.**
   - **India:** the CCPA *Guidelines for Prevention and Regulation of Dark Patterns, 2023* name practices such as *false urgency*, *nagging*, *confirm-shaming*, *forced action*, *subscription traps* and *interface interference* as unfair trade practices.
   - **EU:** the *Digital Services Act* (Art. 25) bans interfaces that "deceive or manipulate" users.
   - **Age-related rules:** the UK *Age Appropriate Design Code*, US state laws on addictive feeds for minors, and **DPDP** limits on processing children's data all tighten further around young users.
   - We are global from day one, so we design to the strictest of these once, instead of retrofitting market by market.
2. **The market data already shows it.** Swipe-first, slot-machine dating is shrinking. Tinder DAU is down, while Hinge, the "designed to be deleted" app, is growing payers and revenue (`01-market-and-feasibility.md` §2.1). Users are burned out, not under-stimulated.
3. **Our product is trust.** Women decide whether a social-discovery app lives or dies. A product that feels manipulative feels unsafe.
4. **Relationships are the most powerful retention mechanic.** A person who has three real friends on OneDay opens it every day without being nudged. Every mechanic below exists to create that state faster.

**North-star metric:** *Weekly Meaningful Actives (WMA)*: people who, in a week, had at least one two-way interaction (a reveal, a conversation where both people wrote, a relay answer, a plan). **Session length is reported but never optimised.** Guardrail metrics are listed in §5.

---

## 1. The human mind: what the research tells us

Each principle below lists the research behind it, what it means for OneDay, and where it appears in the product.

### 1.1 Dopamine is about *anticipation*, not pleasure
Wolfram Schultz's work on **reward-prediction error** (1990s) showed that dopamine neurons fire most when a reward is *better than expected*, and shift to firing on the *cue* that predicts the reward. Kent Berridge separates **"wanting"** (dopaminergic, anticipatory) from **"liking"** (the hedonic experience itself).
- **Implication:** the strongest daily pull is a *reliable cue* that something *possibly wonderful* is waiting. The product does not need noise to create it. It needs real, uncertain, positive possibilities.
- **In OneDay:**
  - the **Local Pulse** at the hour the user chose (a stable cue);
  - **Today's Prompt** (what did the city say today?);
  - the **Signal digest** (who resonated with my moment?);
  - **relay answers** (who answered my story?).

  Each one can surprise, and each surprise is a real person.

### 1.2 The need to belong
Baumeister & Leary (1995): belonging is a fundamental human need. Its absence is experienced as pain, and gaining it is intensely rewarding. Internal migrants, international students and expats are in acute belonging deficit.
- **In OneDay:** **Roots** (people from your home region in your city), **Roots prompts** (a taste of home on Onam), language lenses, and **"3 from Kerala"** on Story Map clusters.

### 1.3 Propinquity and mere exposure: proximity makes friends
In Festinger, Schachter & Back's Westgate housing study (1950), the best predictor of friendship was **physical proximity and repeated passive contact**. Zajonc's **mere-exposure effect** (1968) shows that familiarity breeds liking.
- **Implication:** seeing the *same nearby faces and places* again, in a low-stakes way, is the precondition for friendship. Snapchat's map shows people you *already* know. Nobody makes *repeated ambient exposure to new nearby people* safe.
- **In OneDay:** the **Story Map** and the **Constellation** make your neighbourhood's people ambiently familiar (first name, activity, area) before any contact.

### 1.4 Similarity attracts
Byrne's **similarity-attraction** research (1971) and much since: shared attitudes, values, background and activities predict attraction and friendship.
- **In OneDay:**
  - Values Compass, shared activities, Roots and languages produce **narrative explanations** ("You both put kindness first · both up for a trek");
  - similarity raises a person's rank in the digest, the Constellation and the Story Map;
  - Connection Warmth starters draw on what the two people share.

  Similarity is **never** a score, a percentage or an exclusion filter (blueprint v2 §7).

### 1.5 Reciprocity: give to get
Gouldner (1960) described the **norm of reciprocity**, and Cialdini made it famous in persuasion research: people feel obliged to return what they receive. BeReal used a lightweight form of it: you see friends' posts only after you post your own.
- **In OneDay:** **Today's Prompt** shows nearby answers only after you share a public answer. Contribution is the price of access, so lurking never pays, and the map fills with fresh, live content.

### 1.6 The curiosity gap
Loewenstein's **information-gap theory** (1994): curiosity is the pain of a *specific* gap in knowledge, and people act to close it.
- **In OneDay:**
  - the prompt teaser ("6 people near you answered, 2 from your home region") is a specific, truthful gap, and posting closes it;
  - Layer-0 → Signal → Mutual Reveal is a curiosity gap that only *mutual* consent closes (the safety model and the pull mechanic are the same thing).

### 1.7 Open loops (the Zeigarnik effect)
Unfinished things stay mentally active.
- **In OneDay:**
  - a **Story Relay** you joined is an open loop (who answers next?);
  - a pending digest and a planned meet-up are too.

  Every loop *closes*: a relay is capped at 30 links, the digest ends with "you're caught up", and a plan ends.

### 1.8 Self-disclosure builds closeness, step by step
Aron et al. (1997) ("36 questions") showed that **gradually escalating, reciprocal self-disclosure** creates closeness between strangers. Social penetration theory (Altman & Taylor) describes the same layering.
- **In OneDay:** Layer 0 (an activity) → Signal (one of four reactions, no text) → Reveal (chat seeded with shared context) → Warmth starters → Mutual Spark → Date Mode. Each step is reciprocal and slightly deeper.

### 1.9 Rejection hurts like pain, so remove asymmetric rejection
Social-pain research (Eisenberger) shows that rejection activates distress systems that overlap with physical pain. Fear of rejection is the main reason people don't approach.
- **In OneDay:** a **Mutual Reveal**, a **Mutual Spark** and a **Mutual Debrief** reveal only what is mutual. A pass is silent, and the Consent Trail never shows "seen". Approaching costs very little emotionally, so people approach more.

### 1.10 Loss aversion, and why we refuse streaks
Kahneman & Tversky: losses loom roughly twice as large as gains. Snapchat **streaks** weaponise this: people keep "snapping" out of fear of losing a number, not out of desire. This is widely reported as a source of teen anxiety.
- **In OneDay:** **Connection Warmth** keeps what is good about streaks (visible shared rhythm, a reason to say hi) and removes the threat:
  - it is never a number;
  - it never counts down;
  - "quiet" is described kindly ("Quiet lately, and that's okay");
  - quiet comes with a **conversation starter** instead of guilt.

### 1.11 Social proof without social comparison
Cialdini: people follow what others like them do. Festinger's **social comparison theory** (1954), and later research on Instagram and teen wellbeing, show that public metrics (likes, follower counts, rankings) drive envy and anxiety.
- **In OneDay:**
  - counts are **capped at "9+"**: enough to feel that the room is alive, never a scoreboard;
  - there are no public likes, no follower counts and no leaderboards;
  - the Real Value Ledger is private and never compares.

### 1.12 Choice overload and the "caught up" ending
Iyengar & Lepper (2000): too many options reduce satisfaction and commitment. Swipe fatigue is choice overload at scale.
- **In OneDay:** bounded batches (Constellation pages, digest batches, map clusters capped, 8 stories per cluster), each with an explicit **"you're caught up"** end.

### 1.13 Peak-end rule
Kahneman: experiences are remembered by their **peak** and their **end**.
- **In OneDay:**
  - sessions end on a warm "caught up" line, not an exhausted scroll;
  - the Mutual Debrief ends a date on what *both* felt ("You'd both like to meet again");
  - the Ledger ends the month on real outcomes.

### 1.14 Habit: cue → routine → reward
Habit research (Wendy Wood; Duhigg's popular synthesis) shows that habits form around **stable cues**.
- **In OneDay:**
  - the Local Pulse arrives once a day at the **user's chosen hour**;
  - Today's Prompt turns over at the user's local midnight.

  These are predictable rituals, unlike randomised pushes.

### 1.15 Collective effervescence and novelty
Durkheim described the energy of *doing the same thing together*. Hedonic adaptation means that a fixed experience fades.
- **In OneDay:**
  - **one city-wide prompt per day** (everyone answers together);
  - **Roots prompts** for festivals (Onam, Durga Puja, Pongal, Diwali, Eid, Christmas);
  - relays that travel across the map.

  Content is new every day by design, because stories expire in 24 h.

### 1.16 Autonomy, competence, relatedness
Deci & Ryan's **self-determination theory**: motivation that serves these three needs lasts; controlling, extrinsic pressure undermines it.
- **In OneDay:**
  - **autonomy:** users choose their Pulse hour, radius and lenses, and can pause anything;
  - **competence:** "your moment started a relay" and "you're the first near you to answer";
  - **relatedness:** everything else.

---

## 2. Mechanism → feature map

| Mechanism | Feature | Status | Guardrail |
|---|---|---|---|
| Anticipation (1.1), habit cue (1.14) | **Local Pulse** at the chosen hour, now with Today's Prompt and relay answers | ✅ | One push per local day, only when there is something real; Discretion Mode copy |
| Propinquity (1.3), belonging (1.2) | **Story Map** with adaptive k-anonymous clusters | ✅ | No single person ever placed; Safe Zones never placed; 24 h expiry |
| Similarity (1.4) | Roots / Language / Activity lenses, narrative explanations | ✅ | Never a score, never an exclusion filter, never-collect list |
| Reciprocity (1.5), curiosity gap (1.6) | **Today's Prompt** with give-to-get unlock | ✅ | Truthful capped teaser, no countdown, no "last chance" |
| Open loops (1.7), IKEA effect | **Story Relays** | ✅ | Capped at 30 links, one per person, blocks respected per viewer |
| Self-disclosure ladder (1.8) | Layer 0 → Signal → Reveal → starters → Spark | ✅ | No free text before reveal, Signal Budget |
| Rejection avoidance (1.9) | Mutual Reveal / Spark / Debrief, silent pass | ✅ | One-sided states never shown |
| Loss aversion, rejected (1.10) | **Connection Warmth** instead of streaks | ✅ | Never a number or a countdown; kind "quiet" copy |
| Social proof without comparison (1.11) | Capped "9+" counts, private Ledger | ✅ | No likes, followers or leaderboards |
| Choice overload (1.12), peak-end (1.13) | Bounded batches, "caught up", Debrief overlap | ✅ | Pages capped per session |
| Collective effervescence (1.15) | City-wide prompt, Roots festival prompts | ✅ (staff-scheduled) | Prompts never ask for home, street, room, commute or workplace |
| Competence (1.16) | "Your moment started a relay", "first near you" | ✅ | Capped, never ranked |

---

## 3. Feature specifications (engagement layer)

### 3.1 Story Map (`GET /map/stories`): the map nobody else has

Snap Map shows *friends* and pins public Snaps. Instagram Map shows *friends'* last-active location. Neither lets you **safely get to know the people around you**. The Story Map does.

- **What it shows:**
  - public stories placed **where they were captured**;
  - grouped into clusters with a *vibe* (dominant activity), the top activities, people ("9+" max), how many are **from your home region**, and a **live-now** glow (newest story under 30 min old);
  - Layer-0 story cards: first name, activity, an optional low-fi preview, shared roots and languages, and why you're seeing it.
- **Adaptive k-anonymous clustering:**
  1. A ~0.7 km² neighbourhood cell is shown only when at least **k = 3** different people posted there (radius lens only).
  2. Otherwise its stories roll up to the ~5 km area, which needs **k = 2**.
  3. Otherwise they go to an unplaced *"around your city"* shelf.

  Stories posted inside the owner's **Safe Zone** always go to the shelf. City, Roots and Language lenses never place below area level. **No individual is ever pinned**, and the clusters re-form when someone is blocked.
- **Lenses:** Radius · City · **Roots** ("stories from people from Kerala around Bengaluru right now") · **Language** · activity · **Today's Prompt**.
- **Relay threads** are drawn between clusters: a story that travelled across the city.
- **Private accounts** can post public stories: share scope is chosen per story (blueprint v2 §3).
- **Unique angle:** no major platform offers a *stranger-safe*, *roots-aware*, *k-anonymous* living story map that leads to a consented connection (Signal → Mutual Reveal).

### 3.2 Today's Prompt (`GET /prompts/today`)
- One easy, present-tense, place-rooted prompt a day, in the user's own time zone. The whole city answers together.
- Staff can schedule a global prompt or a **Roots prompt** for one home region (e.g. *"Show us a taste of Kerala in your city"* during Onam). Otherwise a curated catalogue rotates.
- **Give to get:** nearby answers unlock after **a public answer**. A friends-only answer does not unlock strangers' answers. Before you answer you see the truthful teaser: how many people answered nearby (capped) and how many are from your home region.
- Answers are ordinary public moments (`promptKey`), so they appear on the Story Map lens and can receive Signals.
- **Prompt-writing rules (§4.2)** keep prompts from mapping anyone's routine.

### 3.3 Story Relays (`replyToMomentId`, `GET /moments/{id}/relay`)
- Answer a stranger's public story **with your own live public story** ("same here", "here's mine"). That forms a relay: a conversation in stories, and still no free text between strangers.
- One link per person per relay, a maximum of 30, and you can't answer yourself. Each viewer sees only the links they're allowed to see (blocks, paused location and Couple Mode are respected).
- The starter's Local Pulse says *"Your moment started a relay: 3 people answered"* (competence and social reward, capped).

### 3.4 Connection Warmth (on `GET /connections`)
- Levels: `NEW` → `KINDLING` → `WARM` → `GLOWING`, based on *days on which both people wrote* in the last 14 days. `QUIET` means nothing mutual lately.
- It is never a number and never a countdown. The copy is always kind.
- When a connection is new, or has been quiet for 4+ days, it shows a **starter** built from what the two people share: an activity, home region or value ("You both love chess. Ask about their favourite spot for it?").

### 3.5 What already existed and why it pulls
- **Signals, digest, reveal:** anticipation, mutuality and the curiosity gap.
- **Mutual Spark, Date Mode, Debrief:** escalation that is safe and mutual.
- **Real Value Ledger:** a private sense of progress, measured in real outcomes.

---

## 4. Lines we do not cross

### 4.1 Never built
| Pattern | Why not |
|---|---|
| Infinite, autoplaying feed | Choice overload, regret, and US/EU scrutiny of addictive feeds. Every surface is bounded with a "caught up" end. |
| Streaks with loss, countdowns, "last chance" | Loss-aversion coercion. *False urgency* is a named dark pattern (CCPA 2023). |
| Fake or bait notifications ("someone likes you 👀") | Deceptive (DSA Art. 25, CCPA 2023). Every push must point to something real. |
| Re-engagement nagging and guilt copy ("we miss you") | *Nagging* and *confirm-shaming* are named dark patterns. "Designed to graduate" (blueprint v2 §2). |
| Public likes, follower counts, leaderboards, attractiveness scores | Social comparison harms, and a harassment vector |
| Paywalled "who liked you", paid visibility boosts | Monetises anxiety; listed in blueprint v2 §7.5 as never sold |
| Variable rewards without substance (slot-machine loot) | Rewards must always be a real person, answer or plan |
| Exact location of strangers, or anything that maps routine | Safety. See the Precision Ladder and §4.2. |

### 4.2 Prompt-writing rules
Prompts must be easy (one photo of something in front of you), present-tense, positive or neutral, and **never ask for anything that reveals a routine or a private place**: home, room, street, commute, workplace, gym times or "where you go every day". "Your favourite spot to think" is allowed only as "as long as it's a public one".

---

## 5. Measuring it honestly

| Metric | Type | Target direction |
|---|---|---|
| **Weekly Meaningful Actives (WMA)** | North star | ↑ |
| Prompt participation (share of DAU answering publicly) | Engagement | ↑ |
| Relay answers per relay; share of relays crossing ≥ 2 clusters | Engagement | ↑ |
| Signal → reveal rate; reveals → warm (≥ KINDLING) within 14 days | Connection | ↑ |
| D1 / D7 / D30 retention of **verified** users | Retention | ↑ |
| "Was your time on OneDay well spent?" (in-app, 1 in 50 sessions, never blocking) | Guardrail | ≥ 80% yes |
| Median session length | Guardrail | Reported, **not optimised**. Alert if it rises while WMA falls (a compulsion signal). |
| Reports per 1,000 signals; inbound-signal load on the busiest decile | Safety guardrail | ↓ |
| Late-night sessions (00:00–05:00 local) among 18–21-year-olds | Wellbeing guardrail | Watch; never optimise up |

**Experiment rule:** no experiment may ship if it raises session length while lowering WMA or the well-spent score, whatever it does to revenue.

---

## 6. Next engagement features (backlog, in order)

1. ✅ **Festival Seasons:** dated windows per home region (Onam, Durga Puja, Pongal, Bihu, Diwali, Eid, Christmas, Lunar New Year for global diasporas). They drive the day's prompt for that region and label Story Map clusters ("Onam"). Staff enter the dates each year, because lunar dates move.
2. ✅ **Right Now** (blueprint v2 §6.1, behind a flag until density Gate 2): opt in to a named activity for 30–120 min. It shows at band precision. "I'm up for it too" is limited to 5 a day with no free text, and only the poster's acceptance connects; a decline is silent.
3. ✅ **Weekly Recap** (`GET /ledger/week`): built on the peak-end rule. One highlight, a few facts, a kind ending; on Mondays the Local Pulse says it is ready.
4. ✅ **Wellbeing guardrail and WMA** (§5): the one-tap question and the admin dashboard are live.
5. **Plans & Rooms:** turn a relay or a busy cluster into a small public meet-up at a Meeting Point.
6. **Memory Trail** (private): your own past moments and the relays you joined, on your own map only. It needs an opt-in to keep media beyond the 14-day retention.
7. **Trusted Vouch:** friends vouch for you, which transfers trust.
