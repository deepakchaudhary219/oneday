# 01 · Market Requirements & Feasibility (September 2026)

> Companion to the *Product & Psychology Blueprint* and the *Technical Architecture & System Design Specification*.
> The original blueprint (§21.1) asks that market figures be re-verified before each planning cycle, so this document does that first. Sources are listed at the end. Where sources disagree, the range is given instead of a single number.

**Working product name:** *OneDay* (the repository name). Everything public in the product lasts about a day, so the name fits, but it is a placeholder.

---

## 1. Bottom line

| Question | Answer |
|---|---|
| Is there a real market need? | **Yes.** Swipe apps are losing users, while intentional, IRL-first products are growing. Hinge payers are up 17% YoY. Tinder DAU is down 4% and Bumble payers are down 21%. Young users are moving to in-person discovery. |
| Is the blueprint's core differentiator still open? | **Partly.** In the last 12 months Instagram Map launched in India, Snap shipped Map 2.0 with a heat map, and Snapchat Plans launched on 10 Sep 2026. All three work only with **existing friends**. None of them offers **safe discovery of new people nearby**. That part is still open. The blueprint's claim that "Instagram and TikTok have no location-based discovery at all" (§33.1) is now out of date. |
| Is liveness verification a differentiator? | **No, not anymore.** Tinder has made Face Check mandatory for new users in several markets, **India included**. Verification is now the minimum users expect. Differentiation has to come from what is built on top of it: Trusted Vouch, human-verified capture, and graded location precision. |
| Is it technically feasible? | **Yes, if v1 is scoped tightly.** Everything in v1 can be built with standard components. The hardest problems are location privacy (§4.1), moderation SLAs, and cold-start density. Language and framework are not the hard part. |
| Is the full 47-section spec feasible as a launch? | **No.** The original documents already say this (§21.7). This document narrows v1 further and moves Date Mode *earlier*, because dating is now an explicit feature. |
| Biggest risk | **Cold-start liquidity** (not enough of the right people in one place), followed by **trust-and-safety operating cost** under India's 2026 takedown timelines. |

---

## 2. What the market needs right now

### 2.1 Demand signals

1. **Swipe fatigue is showing up in the numbers.** Match Group's Q2 2026 results: total payers down 6% YoY to 13.3M and Tinder DAU down 4% YoY, which is its "best" result in 10 quarters. Hinge, the "intentional" app, grew payers 17% to 2M and direct revenue 22%. Bumble's Q1 2026 payers fell 21% to 3.2M, and it is rebuilding the product around meeting in real life (launching Q4 2026).
2. **Young users are moving toward in-person social discovery.** Attendance at singles and social events aimed at Gen Z and millennials is reported up about 49% YoY. Tinder's *Double Date* (matching together with a friend) produced 35% more messages in group chats than in 1:1 chats. Friends-first products such as Bumble BFF (relaunched Sep 2025) are being rebuilt.
3. **The large friend-graph platforms are moving toward "meet up".** Snapchat Plans (Sep 2026) turns chats into invite-only events for up to 200 friends. Instagram Map (India, Oct 2025) shares friends' last-active location. **This confirms the behaviour is real**, and it also shows where the incumbents stop: *friends only*. They cannot open stranger discovery without breaking their graph and ads model (blueprint §25.2).
4. **Authenticity is scarce.** India's IT Rules amendment (notified 10 Feb 2026, in force 20 Feb 2026) requires labelling of *synthetically generated information*. Some categories carry takedown windows as short as 2–3 hours. A feed that contains only live, human-captured content (blueprint §35.1) now gives a product both a marketing position and a compliance advantage.

### 2.2 India-specific requirements

| Requirement | Evidence / reasoning | Blueprint coverage | Enhancement |
|---|---|---|---|
| **Scale & where users already are** | Snapchat reports **250M+ MAU in India**, its largest market. The blueprint cites 200M. | §42.3 positions the product *alongside* Snapchat | Keep the co-existence positioning and update the figure |
| **Affordability** | QuackQuack (low-priced, local) reports 25–43M+ users (company-reported). Price sensitivity is high. | §21.5 tiers, no ₹ pricing | ₹-denominated tiers via **UPI Autopay** (Blueprint v2 §7.5) |
| **Internal migration** | Census 2011 counted about 45.6 crore internal migrants. Recent estimates range from about 40 to 60 crore. Top destinations are Mumbai, Bengaluru, Delhi and Hyderabad. | Not covered | **Roots Circles**: meet people from your home region who now live in your city (v2 §6.4) |
| **Many languages** | 22 scheduled languages. Hinglish and code-mixing are the norm. | §28.3 localization checklist | Language-aware discovery and vernacular prompts from v1 |
| **Discretion** | Many users hide dating activity from family, and phones are often shared. | Not covered | **Discretion Mode**: neutral notification copy, app lock, no dating wording on the lock screen |
| **Women's safety & overload** | Documented message overload and unsolicited content (blueprint §34.1) | Strong: Layered Reveal | Add a **Signal Budget** (sender-side cost) and a daily cap on incoming signals |
| **Emergency response** | India's single emergency number is **112** (ERSS) | §31 check-ins | One-tap 112 in Date Mode |
| **Budget Android & patchy data** | The large majority of Indian smartphones are Android, and many are low-RAM devices | Not covered | Data-light media defaults and a Lite rendering path |
| **Caste / colourism** | Matrimony-style filters on caste and "fairness" are common and cause harm | §12 bans *inferred* sensitive attributes | **Never collect caste, skin tone or "fairness".** Faith appears only as "faith matters to me", never as a filter (founder decision, Blueprint v2 §7.2) |

### 2.3 Competitive landscape (September 2026)

| Player | What it does now | Where it stops | Our angle |
|---|---|---|---|
| **Snapchat** (250M+ India MAU) | Camera, streaks, Snap Map (friends' locations, public Snaps, heat map), **Plans** (friends-only events, Sep 2026) | Snap does not offer a "find friends near me" feature for strangers. Discovery runs on the friend graph (Quick Add). | The **new-people layer** that sits beside Snapchat |
| **Instagram** | Map (India, Oct 2025): friends' last-active location to mutual followers, location-tagged posts | Follow graph plus a public-content map. No safe way to approach strangers. Open DMs cause overload. | Replace the "slide into DMs" behaviour with Layered Reveal |
| **Tinder** | Mandatory Face Check (India included), *Chemistry* AI (camera-roll based), Double Date | Swipe-first. DAU still declining. | Context-first discovery, no swipe deck |
| **Bumble / BFF** | Payers down 21%. Rebuilding around meeting in real life (Q4 2026). BFF relaunched. | Still matching-first. Rebuild still in progress. | Stories plus proximity instead of profile decks |
| **Hinge** | Growing: intentional, "designed to be deleted" | Weak on hyperlocal, spontaneous and friend use | Right Now, Roots and friends as equal outcomes |
| **QuackQuack / Aisle / TrulyMadly** | Local, affordable, culturally tuned | Classic matching UX. Little or no social layer. | A social product in which dating can happen |

**Whitespace:** no one combines (a) a camera-first ephemeral social product, (b) **safe** stranger discovery around nearby activities, (c) map precision that users must *earn*, and (d) a regional "roots" layer. Each incumbent is blocked from at least one of these by its own business model.

---

## 3. Feasibility assessment

### 3.1 Technical feasibility: **High**, with three hard problems

| Area | Feasibility | Notes |
|---|---|---|
| Core social backend (profiles, stories, signals, chat) | High | Well-understood CRUD plus event workloads. **Milestone 1 is built in this repository** (see `04-implementation-plan.md`). |
| **Location privacy** | Medium, **hard** | Research in 2024 (KU Leuven, Check Point) located Bumble, Hinge, Grindr and Hornet users to within **2–111 m** using oracle trilateration on distance filters. The v1 doc's "random offset within each band on every read" can be **averaged away** by repeating queries. Blueprint v2 and Tech Arch v2 replace it with cell snapping, stable per-pair jitter, and probe budgets. |
| Liveness & age estimation | Medium | Buy it, don't build it. Several vendors serve India. Evaluate cost per check, false-reject rate on Indian skin tones and budget cameras, and DPDP-compliant data handling. |
| Moderation at India SLA (2–3 h for some categories) | Medium, **costly** | Needs 24×7 human coverage plus ML triage. Layered Reveal (no open inbox) sharply reduces the moderation surface. |
| Real-time / live video | Low priority | Deferred to v3. Needs a managed service (IVS / LiveKit) and real-time ML moderation. |
| Full Cassandra + Kafka + OpenSearch + ClickHouse + K8s on day one | **Not advisable** | The v1 doc itself cites Instagram: "don't start there by default". Tech Arch v2 recommends a **modular monolith on MySQL** first, with defined extraction triggers. |

### 3.2 Legal & regulatory feasibility: **High**, with counsel before launch

| Regime | What it requires | Status in plan |
|---|---|---|
| **DPDP Act 2023 + DPDP Rules 2025** (notified 13 Nov 2025) | Consent notices, data-principal rights (access, correction, erasure, grievance), breach notice to the Board within **72 h**, integration readiness for registered **Consent Managers** (registration opens 13 Nov 2026), core obligations from **13 May 2027**. Verifiable parental consent for anyone under 18, and no profiling of minors. | Consent record at signup, self-service export and erasure, **hard refusal of under-18 signups** (implemented in M1). Breach runbook in M2. |
| **IT Rules 2021 + 2026 SGI amendment** | Grievance officer, takedown SLAs (some as short as 2–3 h), labelling of AI-generated content, and user declaration of SGI before publishing | Live-capture-only discovery feed and a Moderation SLA workstream |
| **POCSO Act** | Mandatory reporting of child sexual abuse material and exploitation, evidence preservation | Report category `UNDERAGE_SUSPECTED` routes to P0 (implemented). Legal process to be designed with counsel. |
| GDPR / UK OSA / DSA / CCPA | For expansion only | Region-configurable compliance layer (blueprint §28.4) |

### 3.3 Operational feasibility: **Medium**

- **Trust & Safety is the main operating cost.** Budget for 24×7 coverage from launch. The design (Layered Reveal, signal budgets, no free text before a mutual reveal) is what keeps this affordable, because it removes most of the paths harassment usually takes.
- **Incident response for harm at real-world meetings** (blueprint §47.2) must exist before the first public meet-up feature (Date Mode) ships.

### 3.4 Financial feasibility: **Medium**, dependent on capital

- India ARPU for dating is low. Market-size estimates vary widely: about **USD 0.8B (2024) growing to about 1.4B (2030)** in one report, with lower figures elsewhere. Plan revenue on hyperlocal ads (§25.7) plus a low-priced ₹ tier, not on Western-style subscription ARPU.
- A modular-monolith v1 on managed services keeps pilot-scale infrastructure cost low. **Most of the money goes to density: ambassadors, anchor events and paid acquisition** (blueprint §41.6, §43.5). Budget numbers have to be priced against the chosen launch city. This document does not invent them.

### 3.5 Market feasibility: **Medium**, cold start decides it

Features keep users once they arrive. They do not bring users in (blueprint §41.1). A single-city launch that passes an explicit density gate is the only credible path. `04-implementation-plan.md` sets the gates.

---

## 4. Critical findings that change the original documents

| # | Finding | Change made |
|---|---|---|
| F1 | Instagram Map (India, Oct 2025), Snap Map 2.0 heat map, Snapchat Plans (Sep 2026) | §33.1's claim that "no competitor has location discovery" is withdrawn. The differentiator is restated as **stranger-safe discovery of new people**, not "a map". |
| F2 | Tinder Face Check is mandatory in India | Liveness moves from "differentiator" to "minimum requirement". Differentiation moves to Trusted Vouch, live capture and graded location precision. |
| F3 | Oracle trilateration attacks (2024) | Random noise that changes on every read is replaced by **cell-snapped distances, stable per-pair jitter, and budgets on location updates and queries** (Tech Arch v2 §5). |
| F4 | User requirement: an explicit **dating feature** | The "no intent selector anywhere" rule (§26.1) is reconciled with dating through a **private, mutual-only Dating Lens + Mutual Spark** (Blueprint v2 §5). Date Mode moves from v2 to v1.5. |
| F5 | DPDP Rules notified; IT Rules SGI amendment | India compliance becomes concrete engineering work (Tech Arch v2 §8), not a line item. |
| F6 | Internal migration of about 40–60 crore people | A new **Roots** layer (Blueprint v2 §6.4) that neither Snapchat nor Instagram offers. |
| F7 | The v1 docs contradict each other on MySQL vs PostgreSQL, Cassandra-first vs "don't start there", and Layer-1 video unlock vs Layer-2 content unlock | Resolved in Blueprint v2 §0 and Tech Arch v2 §0. |

---

## 5. Recommendation

**Go**, for a **single-city pilot** (recommended: **Bengaluru**: the top migrant destination, a young tech workforce, dense campuses and cafés, and a multilingual population, which is ideal for Roots). Use the v1 scope in `02-product-blueprint-v2.md` §11 and the gates in `04-implementation-plan.md`. **No-go** on a multi-city or national launch until the density gate is met in the first city.

---

## Sources

- Snap Newsroom: [Snapchat Hits 250 Million Monthly Active Users in India](https://newsroom.snap.com/india-mau-250m)
- TechCrunch (10 Sep 2026): [Snapchat takes aim at Partiful with new event-planning features](https://techcrunch.com/2026/09/10/snapchat-takes-aim-at-partiful-with-new-event-planning-features/). Bloomberg: [Snap Launches Planning Feature to Make Meet-Ups With Friends](https://www.bloomberg.com/news/articles/2026-09-10/snap-launches-planning-feature-to-make-meet-ups-with-friends)
- Snap Map 2.0 / heat map (secondary reporting): [SocialSellinator: Top Snapchat Features 2026](https://socialsellinator.com/top-snapchat-features/)
- Meta Newsroom (Oct 2025): [Now in India, Instagram rolls out Map feature](https://about.fb.com/news/2025/10/now-in-india-instagram-rolls-out-map-feature-to-help-people-connect-with-friends/)
- Tinder Press Room: [Tinder to Expand Facial Verification](https://www.tinderpressroom.com/2025-10-22-Tinder-to-Expand-Facial-Verification-Feature-Across-the-U-S-,-Setting-a-New-Standard-for-Dating-Safety). Digital Watch: [Tinder expands mandatory Face Check](https://dig.watch/updates/tinder-expands-biometric-verification)
- Match Group: [Second Quarter 2026 Results](https://ir.mtch.com/investor-relations/news-events/news-events/news-details/2026/Match-Group-Announces-Second-Quarter-Results/default.aspx)
- TechCrunch (5 May 2026): [Bumble's paying users are slipping as it bets on an overhaul](https://techcrunch.com/2026/05/05/bumbles-paying-users-are-slipping-as-it-bets-on-an-overhaul-later-this-year/)
- Global Dating Insights: [Gen Z continue to shift towards in-person dating events](https://www.globaldatinginsights.com/featured/gen-z-continue-to-shift-towards-in-person-dating-events/)
- MarkNtel Advisors: [India Dating Apps Market Size](https://www.marknteladvisors.com/press-release/india-dating-apps-market-size)
- Forbes India: [Indian dating apps are swiping right on small towns](https://www.forbesindia.com/article/life/indian-dating-apps-are-swiping-right-on-small-towns/2989315/1)
- [DPDP Rules, 2025 (Wikipedia summary)](https://en.wikipedia.org/wiki/Digital_Personal_Data_Protection_Rules,_2025). EY: [DPDP Rules 2025 guide](https://www.ey.com/en_in/insights/cybersecurity/transforming-data-privacy-digital-personal-data-protection-rules-2025)
- Freshfields: [MeitY's 2026 amendments to the IT Rules (SGI / deepfakes)](https://www.freshfields.com/en/our-thinking/blogs/technology-quotient/india-targets-deepfakes-and-ai-generated-content-key-changes-under-meitys-2026-102mjwn)
- TechCrunch (Jul 2024): [Bumble and Hinge allowed stalkers to pinpoint users' locations down to 2 meters](https://techcrunch.com/2024/07/31/bumble-and-hinge-allowed-stalkers-to-pinpoint-users-locations-down-to-2-meters-researchers-say). Check Point Research: [Geolocation risks in modern dating apps](https://research.checkpoint.com/2024/the-illusion-of-privacy-geolocation-risks-in-modern-dating-apps/)
- Drishti IAS: [India's Internal Migration](https://www.drishtiias.com/daily-updates/daily-news-analysis/india-s-internal-migration)
