# 07 · v3 Features: How They Were Interpreted

The blueprint (`02-product-blueprint-v2.md` §11) names the v3 "Scale" features but doesn't specify them. This document records how each one was built, so product owners can confirm or change the intent before the apps ship them. Every feature follows the same rules as the rest of OneDay:
- no user id is ever shown to another person;
- no public metrics;
- no bait notifications;
- blockable and reportable where it is seen;
- exported and erased with the account.

| Feature | What was built | Key limits | Module |
|---|---|---|---|
| **Time Capsules** | A message, optionally with a photo or video, sealed for yourself or a Connection until a chosen day | Opens 1 day to 5 years ahead; 20 waiting per person. Content is hidden until it opens, even from yourself for a capsule to yourself. One notice and one push on the day. A block, or the connection ending, cancels it. | `capsules` |
| **Collaborative Threads** | One shared story thread among friends, e.g. for a trip, a wedding or a festival week | Up to 12 people, invited from members' own Connections; open 1–7 days, then read-only, deleted 3 days later. Members are shown by first name and a per-thread handle. Captions get the Empathy Mirror. | `threads` |
| **Spotlight Replies** | A story's owner highlights Story Relay answers to it | At most 3 per story. The answer's author must accept, and either side can withdraw. Spotlit answers lead the relay view. | `moments` |
| **Public Figure accounts** | Staff-verified creators, artists, athletes, journalists, officials and organisations, followed one way | Evidence reviewed and audited by a moderator; public `@handle` and badge. The follower count is visible to the figure only. Following opens no message channel. The feed has no location. | `figures` |
| **AMA Corridors** | A verified figure answers questions for 15–120 minutes, open to everyone or to one **Roots corridor** (people whose home region matches, e.g. `IN-KL`: Keralites wherever they live) | Questions open a day ahead; 3 per person; optional anonymity ("Someone from IN-KL"). Votes order questions, and counts go to the host only. Host and moderators can hide questions. Purged 30 days after. | `ama` |
| **Circle Live** | Live video to your Connections, or to one Collaborative Thread | One live per person, up to 60 minutes, never recorded. The SFU (LiveKit) carries the video. The API issues tokens: hosts can publish; viewers can only subscribe, for 10 minutes at a time. Announced on the socket only. The viewer count is the host's. | `live` |
| **Multi-city** | A launch registry: each city's area, stage (`PILOT` / `OPEN` / `PAUSED`) and density-gated features (`RIGHT_NOW`, `PLANS`, `CIRCLE_LIVE`, `AMA`) | A person's city is computed from their snapped cell on demand, never stored. Right Now opens per city (or globally). Staff get density per km² for each launch decision. | `cities` |

## Questions for the product owner

1. **Time Capsules:** should capsules also go to a Collaborative Thread (a group capsule for a reunion)? The model allows it, but it isn't built.
2. **Public Figures:** should figures post a separate "broadcast" story that isn't tied to where they are? Today the feed shows their normal live public stories.
3. **AMA Corridors:** the corridor is a home region. Should it also be able to target "people from X now living in city Y" (home region plus current city)?
4. **Circle Live:** live comments are deliberately missing (they'd need the Empathy Mirror and moderation at live speed). Should they be added?
5. **Multi-city:** which features should require a city gate, beyond Right Now? Plans, Live and AMA are listed for the apps to read, but the server enforces only Right Now.
