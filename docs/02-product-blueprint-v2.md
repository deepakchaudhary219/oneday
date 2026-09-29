# 02 · Product Blueprint v2: "The Future of Snapchat, with Dating, Roots & Real Values"

> Enhances and **consolidates** the *Product & Psychology Blueprint* (sections 1–47). Earlier sections of that document were superseded piece by piece (§25.3 → §34 → §37 → §45). This document states the **single current rule set**, and it adds what the September 2026 market check and the new requirements call for:
> 1. an explicit **dating feature**,
> 2. an **advanced, region-aware map** for meeting people, and
> 3. **real values**.
>
> Where this document is silent, the original blueprint still applies.
> Its psychology principles (§5–§6, §22–§23, §27, §30) are **kept unchanged**. They are the foundation, and nothing here weakens them.

---

## 0. What changed, and why

### 0.1 Contradictions in v1 and how they are resolved

| # | Conflict in v1 | Resolution in v2 |
|---|---|---|
| R1 | §26.1 "No *looking-for* field anywhere" vs. §3/§8.1 "dating intent" and the new explicit dating requirement | **Dating Lens**: a *private* switch that nobody else can see. Romantic interest is expressed only as a **Mutual Spark** inside an existing Connection, and only a mutual spark is ever revealed (§5). The public surface still never shows a "looking for" label. |
| R2 | §34.2 says real content unlocks at Layer 2, but §34.5 says video fully unlocks at Layer 1 | **Safer rule wins.** Before a Mutual Reveal, the only media anyone sees is the optional Layer-0 *silent low-fi preview* the poster chose to allow. Full content unlocks at Layer 2. |
| R3 | §33.1 "competitors have no location discovery" | **Withdrawn** (Instagram Map, Snap Map 2.0, Snapchat Plans). The claim is restated as **"competitors map friends; we make meeting strangers safe"**. |
| R4 | §21.4 liveness as a differentiator | Liveness is now the **minimum requirement** (Tinder Face Check is mandatory in India). Differentiation comes from Trusted Vouch, live capture and graded location precision. |
| R5 | Tech §4.4 / §21.3 "randomized offset within band" on every read | Replaced by **cell-snapped distances + stable per-pair jitter + probe budgets**. Noise that changes on every read can be averaged away by repeated queries (Tech Arch v2 §5). |
| R6 | §39.1 cites "Section 34.9", which does not exist | The expiry rule for Pulse Status is **24 h, the same as Stories** (§44 table). |
| R7 | §21.7 defers Date Mode to v2 | With dating now explicit, **Date Mode safety moves to v1.5**. It ships *before* dating is marketed. |
| R8 | MySQL (blueprint §10) vs PostgreSQL/PostGIS (tech §2) | **MySQL 8** (already in the codebase). Geospatial work is done in the application using geohash cells, so no DB extension is needed (Tech Arch v2 §2). |

### 0.2 What's new in v2

| Pillar | New or upgraded capability | Section |
|---|---|---|
| Snapchat DNA | Camera-first **Live Capture**, **Honest Lens rule**, Friend Mode that feels like Snapchat | §3, §8 |
| Dating | **Dating Lens**, **Mutual Spark**, Date Mode (v1.5), Mutual Debrief | §5 |
| Advanced map | **Precision Ladder**, Constellation, **City scope**, **Roots Circles**, **Safe Zones**, k-anonymous **Heat**, real-presence **Travel** | §6 |
| Real values | **Values Compass**, **never-collect list** (caste, skin tone), private **Real Value Ledger**, **Couple Mode** ("designed to graduate"), honest ₹ pricing | §7 |
| India fit | **Discretion Mode**, vernacular-first, data-light, one-tap 112, UPI | §9 |
| Safety | **Signal Budget** (the sender pays in effort), no free text before reveal, block propagation, P0 child-safety routing | §10 |

---

## 1. Thesis

**Snapchat made talking to friends feel light, visual and temporary. OneDay makes meeting *new* people feel just as light. It is safe by design, it works at the level of where you actually are and where you are *from*, and it is measured by whether real relationships (friends, dates, partners) actually form.**

> **Position:** *The app you open for real, nearby, new human connection. You keep Snapchat and Instagram for the friends you already have.* (Kept from §42.1, sharpened.)

Why incumbents can't copy it quickly (the §25.2 test, re-run for 2026):

- Snap and Instagram maps are built on the **friend or follow graph** that their ads depend on. Opening stranger discovery on those maps brings back the overload and safety problems they already face in open DMs, and creates new legal exposure.
- Swipe apps are built on a **profile deck**. Moving to ephemeral, context-first stories would break their monetization, which depends on paying to be seen.

---

## 2. Principles (v1 principles kept, three added)

All nine principles in v1 §2 remain in force. v2 adds three:

| Principle | Meaning |
|---|---|
| **Privacy scales inversely with reach** | The further away someone can see you from, the less precisely they can see you. Radius scope shows distance bands. City scope shows only "in your city". Nothing shows coordinates. |
| **Honest capture** | Anything that can reach a stranger was captured live, in-app, now. No uploads from the gallery, no beautify filters, and no AI-generated media in Discovery Mode. |
| **Designed to graduate** | Success is when people *need the app less* for a relationship they have found: Couple Mode, friends moving to Friend Mode, and no guilt-based win-back messages. |

---

## 3. Two modes, one app (canonical; replaces §37.4 and §45)

| | **Friend Mode** | **Discovery Mode** |
|---|---|---|
| Who | Existing Connections (from a Mutual Reveal or from contacts the user imported) | People you are *not* yet connected to |
| Chat | Persistent, unrestricted, Snapchat-like | **No chat.** Signals only, until a Mutual Reveal |
| Content | Friends-only Stories, Pulse Status, Circle Live (v3), AR lenses allowed and labelled | Public shares only, live capture only, Layered Reveal |
| Location | Optional time-boxed exact sharing *only* for an agreed meet-up (§6.7) | Distance bands or city-level only, never coordinates |

**Two independent settings** (from §45.3):
- **Account privacy** (`PUBLIC` or `PRIVATE`) decides whether your *profile* can be browsed.
- **Share scope** is chosen per post (`FRIENDS_ONLY` or `PUBLIC_DISCOVERY`). A private account can still make a single public share. That share is a deliberate act of opening a door, never a side effect of having an account.

---

## 4. From stranger to connection (canonical flow)

```
Layer 0  Ambient ──► Layer 1  Signal ──► (48 h Reaction Window) ──► Layer 2  Mutual Reveal ──► Friend Mode
first name,           one tap, 1 of 4      recipient reviews in her        Connection + Conversation
activity, band,       reactions, optional  own digest, on her own          seeded with the shared
direction, optional   activity reference   schedule. No countdown,         activity context
silent low-fi         NO free text         no expiry push, sender never
preview                                    learns seen / ignored
```

Rules (these consolidate §25.3, §34, §37 and §45):

1. **Layer 0: Ambient.** Visible to nearby users. Shows first name, activity or mood, distance band, a coarse direction, *roots/language affinity* when present, a live-capture badge, and a narrative "why you're seeing this" line. It never shows a photo, the account ID or coordinates. For video, the poster may allow a 3–5 s silent low-fi preview.
2. **Layer 1: Signal.** One tap, choosing from **four specific reactions**: *this resonates · made me smile · want to know more · same here*. The sender can also add **a reference to one of the poster's activities**. **No free text.** Every free-text path before mutual consent is a harassment path. A Signal never generates a real-time notification.
3. **Signal Budget (new).** Each user can send at most *N* signals per rolling 24 h (default 20). Signals are not refunded when unanswered. Sending twice to the same moment is not possible. This implements §34.1's "shift the cost onto the sender" without any gender-specific rule.
4. **Reaction Window.** A signal stays actionable until **moment posted + 48 h**. After that it quietly archives. The sender keeps it in their private **Consent Trail**. No countdown and no "you missed it" message is shown to anyone (§37.3).
5. **Digest.** The recipient reviews signals in a **bounded batch** (the Local Pulse). The batch ends with a "you're caught up" screen. There is no individual real-time interruption.
6. **Layer 2: Mutual Reveal.** The recipient chooses to reveal back. This creates the Connection and the Conversation, seeded with the activity that connected them ("Connected over: *trek*"). Passing is silent.
7. **Soft exit.** Either person can end a Connection at any time. No notification is sent. The other person simply finds the conversation inactive.

---

## 5. Dating, done honestly (**new**; resolves R1)

The user requirement is an explicit dating feature. The blueprint's insight is that declaring romance upfront makes a product *feel like a dating app* and creates a fear of rejection. v2 keeps both by making romantic intent **private until mutual**.

### 5.1 Dating Lens (private)
- A switch in settings: *"I'm open to dating"*. **Nobody else can see it**, and no profile badge or filter exposes it.
- It changes only *your own* experience. It turns on the **Spark** control inside your Connections and adds dating-flavoured prompts to your own Questions. It also lets Discovery lean slightly toward people who are *also* open to dating, but the effect is **never visible** and never used as a filter anyone can query.
- Two optional, **private** fields make that lean relevant: *my gender* (self-described) and *who I'm interested in dating*. They are never displayed and never filterable by anyone. They are used only when **both** people have the Dating Lens on **and** each matches the other's interest. The same fields, in aggregate only, measure the gender-balance density gate.

### 5.2 Mutual Spark
- Inside a Connection (after a Mutual Reveal), a user who has the Dating Lens on can privately **Spark**: *"I'd like this to be more than friends."*
- **Only a mutual spark is revealed.** Both see *"You both sparked ✨"*. A one-sided spark is never shown, never notified and never inferable. The sparker's own view shows *"Your spark is private"*.
- Either person can withdraw their spark at any time.
- The psychology comes from Mutual Debrief Overlap (§24.2): symmetric disclosure removes the sting of asymmetric rejection.

### 5.3 Date Mode (moved to **v1.5**; ships before dating is marketed)
- Planned **only after** a Mutual Spark or a mutually agreed Plan.
- Suggests **Safety-Verified Meeting Points** (well-lit, public, partner venues) and optional **Discovery Trails** (§36.7–36.8).
- **Time-boxed exact location** between the two people only, starting shortly before the meet-up and **auto-expiring** after it (§36.5).
- A **trusted contact** can receive the meeting details and live location for the duration of the date only.
- **Scheduled check-in** ("Going OK?"). One tap escalates to a trusted contact, and there is **one-tap 112** (India's ERSS).
- **End-of-date confirmation**, followed by a **Mutual Debrief** (only overlapping positive feelings are revealed).

### 5.4 What dating will *not* include
No swipe deck. No public "looking for" label. No compatibility percentage (§20.7). No paid boosts or pay-to-be-seen features. No "who liked you" paywall. No super-likes.

---

## 6. The advanced map: **Roots & Radius** (**new / upgraded**)

### 6.1 Map surfaces

| Surface | What it shows | Precision | Release |
|---|---|---|---|
| **Constellation** (radius) | Nearby Ambient nodes as soft glowing points, loosely grouped by activity | Distance **band** + 8-point direction, computed between **cell centres** | v1 |
| **City scope** | People and moments across your city (up to about 40 km) | "In your city" only, no band and no direction | v1 |
| **Roots Circles** | People **from your home region** who now live in your city | City-level only | v1 |
| **Language lens** | People in your city who share a language with you | City-level only | v1 |
| **Heat layer** | What kinds of activity are happening around you, by category | Aggregated over ~5 km cells, **shown only when ≥ k distinct people are in a cell** | v1 |
| **Right Now** | People who have opted in to a *named* activity for the next 1–2 h | Band | **v2, behind the density gate** (§33.1) |
| **Meeting Points & Trails** | Safety-verified venues and 2–3-stop routes | Venue-level (public places) | v1.5 (with Date Mode) |

The view is always **centred on you** and uses a radius dial. **There is no panning** to browse another neighbourhood (§36.4).

### 6.2 The Precision Ladder (the most important map rule)

| Relationship to viewer | Best precision they can ever get |
|---|---|
| Stranger, radius scope | Distance band (<1 km / 1–5 km / 5–15 km) between ~0.7 km² cells, plus coarse direction |
| Stranger, city / roots scope | "In your city" |
| Stranger, while you are inside one of your **Safe Zones** | "Nearby area" only (no band, no direction) |
| Connection (Friend Mode) | Same as a stranger, unless *you* share more |
| Agreed meet-up partner in Date Mode | Exact live location, **time-boxed** and mutually consented, then auto-expired |

**Exact location is never a discovery mechanism.** It is only a short-lived coordination tool between two people who have already agreed to meet.

### 6.3 Safe Zones (**new**)
Users mark up to a few areas (home, office, hostel). While they are inside one, strangers see them only as "nearby area". This defends against the *routine-tracking* pattern criticised in Snap Map (§36.1): repeated sightings can no longer reveal where someone sleeps.

### 6.4 Roots Circles (**new**; India-specific wedge)
- Optional **home region** (state or region level, e.g. `IN-KL` for Kerala, never a village or address) plus **languages spoken**.
- In Roots scope, a Malayali in Bengaluru sees other people from Kerala who are *currently* in Bengaluru, at city-level precision, with a line like *"Also from Kerala · speaks Malayalam"*.
- Why it matters: India has somewhere between **40 and 60 crore internal migrants**. Being new in a big city is exactly when people need new friends, and exactly when Snapchat's friend graph (built back home) can't help. Roots is a strong reason for *friend* use, which feeds density for *dating* use.
- Seasonal and regional **Plans** tie into Roots: Onam sadhya, Durga Puja pandal-hopping, Pongal, Garba nights, Eid, Christmas, IPL screenings. These give the anchor events required by the cold-start playbook (§21.6).

### 6.5 Travel: real presence only
Unlike location "passport" features, **you can never set a fake location**. When you are really in another city, you appear there as *"Visiting from Pune"*. Location updates that imply impossible travel are rejected. This keeps Proof-of-Presence (§35.2) meaningful.

### 6.6 Heat layer with k-anonymity
Activity categories are aggregated per ~5 km cell and shown **only when at least *k* distinct people contribute** (default k = 10 in production). Values are shown as *low / active / busy*, never exact counts. This gives the map ambient usefulness without rendering any individual, even approximately.

### 6.7 Time and fade
Every node fades on the time-box of its underlying moment (about 24 h). The map keeps **no history of where anyone has been**. The server stores only the *current* cell per user, and deletes it when the user pauses location sharing.

---

## 7. Real values (**new**)

"Real values" means two things. People connect over **what they value**, and the product produces **real value** in people's lives rather than time spent in the app.

### 7.1 Values Compass
- Users choose **up to five** values from a curated list: *kindness, honesty, ambition, adventure, family, humour, curiosity, independence, creativity, fitness, learning, community, sustainability, faith matters*.
- Values are used to produce **Narrative Match Explanations** (§20.7), for example *"You both put kindness and adventure first · both up for a trek"*. Values are **never a score or a percentage** and **never a filter** anyone can use to exclude people.

### 7.2 The never-collect list (policy; founder sign-off required)
The product will **never collect, infer or filter on**:
- **caste** or sub-caste, gotra, or community surname signals;
- **skin tone** or "fairness", or any appearance rating;
- **income** or salary bands;
- **religion as an identity filter**. Only *"faith matters to me"* exists, as a value. It is about how important faith is to someone, not which faith.

This is a deliberate break from matrimony-style filters. It is also the safer position under DPDP purpose limitation and anti-discrimination expectations. *Open decision:* some users will ask for religion filters. Recommendation: hold this line in v1 and revisit only with explicit research and a risk review, as §25.6 requires for any reversal of a principle.

### 7.3 Real Value Ledger (private, user-facing)
A private, self-referential view (it extends §25.6 Resonance and §38.3 Weekly Recap). It shows *"This month: 3 conversations that went somewhere, 1 plan you actually went to, 2 new friends from Kerala in Bengaluru."* It never shows a rank, a leaderboard or a comparison with other users. It tells the user whether the app is worth their time.

### 7.4 Couple Mode ("designed to graduate")
When two people who have a Mutual Spark both confirm *"we're seeing each other"*, both can switch on **Couple Mode**. Their Discovery-Mode visibility pauses, their Memory Trail stays, and their Friend Mode keeps working. There are no win-back campaigns. Leaving the app because it worked counts as a **success metric**.

### 7.5 Honest monetization (₹, UPI Autopay)
| Tier | Price (to be validated in research) | Contents |
|---|---|---|
| Free | ₹0 | Full discovery, signals, reveal, chat, Date Mode safety. **Safety features are never paywalled.** |
| Plus | ~₹99–199/month | Wider radius dial (up to 15 km), more Plans per week, Roots and Language lenses in more cities while travelling, priority support |
| Local business | Self-serve | Clearly marked sponsored Stories in the local radius (§25.7), frequency-capped, mutable |

**Never sold:** visibility boosts, "see who signalled you", undoing a pass, read receipts, extra signals beyond the daily budget.

---

## 8. Camera-first and human-verified (Snapchat DNA, upgraded)

- **Opens to the camera** (the Snapchat muscle memory), but the first-ever action is a small *contribution* prompt (§29 orientation).
- **Live Capture only in Discovery Mode.** No gallery uploads, and capture time and cell are baked in (§35.1–35.2). A **"Live" badge** is the marketable identity.
- **Honest Lens rule (new).** AR lenses and beauty filters are available **only in Friend Mode** and are always labelled. Discovery-Mode shares are unfiltered. This is anti-catfishing by construction, and it aligns with India's 2026 SGI labelling rules.
- **Pulse Status** (mood + music + emoji) works as in §39. Spotify integration is limited to *Now Playing* and the *official embed* (§39.2).

---

## 9. India-first requirements (**new**)

| Requirement | Product response |
|---|---|
| **Discretion** | *Discretion Mode*: neutral notification copy ("You have an update"), optional app lock (biometric/PIN), no dating vocabulary on the lock screen or in the notification shade |
| **Vernacular** | UI and prompts in Hindi, Kannada, Tamil, Telugu, Malayalam, Marathi, Bengali and Hinglish at launch city scope. Language-aware discovery. |
| **Budget devices / data cost** | Data-light defaults (low-bitrate previews, no autoplay on mobile data), a lighter rendering path, offline send queue |
| **Payments** | UPI Autopay for Plus. No card required. |
| **Emergency** | One-tap **112** in Date Mode and in every safety sheet |
| **Stronger age assurance (optional tier)** | Optional **DigiLocker-based over-18 attestation** as an extra trust signal. Only a yes/no "is over 18" is stored, never the document. It is never mandatory. |

---

## 10. Safety system (consolidated)

| Layer | Mechanism | Release |
|---|---|---|
| Entry | Declared DOB. **Under-18 signups are refused and nothing is stored.** Signup rate limits per device/IP (§47.6). | v1 ✅ |
| Contact gate | Liveness + age-estimate cross-check required at **first contact-reaching action** (post publicly, signal, reveal, message, spark). Enforced in the security filter chain *and* re-checked in services. | v1 ✅ |
| Approach cost | Signal Budget, no free text before reveal, a daily cap on incoming signals shown in the digest | v1 ✅ |
| Exit | Soft exit, one-tap block (propagates across discovery, signals and chat) | v1 ✅ |
| Reports | Categories mapped to documented harms (§31.2). **`UNDERAGE_SUSPECTED` routes to P0**, along with non-consensual intimate imagery and threats. | v1 ✅ (queue), ops in M2 |
| Behavioural | Pacing Guardian, Empathy Mirror | v2 |
| Meet-up | Date Mode check-ins, trusted contact, 112, Meeting Points | v1.5 |
| Trust transfer | Trusted Vouch Layer | v2 |
| Legal ops | POCSO reporting process, IT Rules grievance officer, 2–3 h takedown on-call, incident response (§47.2) | Before public launch |

---

## 11. Release scope

| Release | Scope | Gate to enter |
|---|---|---|
| **v1 (Pilot core)** | Identity + progressive verification, profile (activities, values, languages, roots), privacy-safe location + Safe Zones, Stories (live capture, share scope), Constellation / City / Roots / Language discovery, Heat, Signals + Reaction Window + Digest, Mutual Reveal, Friend-Mode chat, Investment Balance, Dating Lens + Mutual Spark, block/report, Local Pulse, DPDP export/delete | Legal review; T&S on-call staffed |
| **v1.5 (Dating-ready)** | Date Mode (check-ins, trusted contact, 112, time-boxed exact location), Meeting Points, Trails, Mutual Debrief, Couple Mode, Discretion Mode notifications | **Must ship before any dating-led marketing** |
| **v2 (Depth)** | Right Now (density-gated), Plans & Rooms, Pulse Status + Spotify, Layered Video, Trusted Vouch, Pacing Guardian, Empathy Mirror, Weekly Recap, E2EE for Friend Mode chat | City density gate met |
| **v3 (Scale)** | Circle Live, AMA Corridors, Public Figure accounts, Spotlight Replies, Time Capsules, Collaborative Threads, multi-city | Second city gate met |

---

## 12. Metrics (additions to §14, §32.2 and §38.4)

- **Reveal rate**: the share of signals that lead to a Mutual Reveal within the window. This is the health of the approach economy.
- **Signal-to-report ratio**: an early warning that the Signal layer is being abused.
- **Roots connection rate**: the share of Roots-scope reveals that turn into a plan or a second conversation.
- **Graduation rate**: Couple Mode switches per 1,000 Mutual Sparks. Higher is better.
- **Women's (and all vulnerable users') inbound signal load**: the distribution of signals received per day. Keep the long tail short with budgets and caps.

---

## 13. Open decisions for the founder

1. Keep the **religion-filter ban** (recommended) or research an alternative (§7.2).
2. **Launch city.** Bengaluru is recommended (`01-market-and-feasibility.md` §5).
3. **Liveness vendor** (evaluate bias on Indian skin tones and budget cameras, pricing, DPDP terms).
4. **Mobile stack.** Android-first. Flutter is recommended for speed to both platforms (see implementation plan).
5. **Signal Budget default** (20/day proposed) and **k** for the Heat layer (10 proposed).
