# Me Dashboard Roadmap

Personal dashboard surfaced through the "me" contact. Scoped 2026-05-02.

---

## Navigation restructure

Replace the current 4-tab bottom nav:

| Before | After |
|--------|-------|
| People | People |
| Feed | Feed |
| Chat | Chat |
| Tasks | **Me** |

Tasks moves into the Me dashboard as its own tab. Net result: still 4 items, nothing lost.

---

## MeScreen layout

Separate screen and ViewModel — not a mode-flag on PersonDetailScreen. The data model is fundamentally different (aggregate cross-contact queries vs per-person records).

Visual structure mirrors PersonDetailScreen:
- **Top section** — name, nickname, photo, job/company, birthday age (from Life Events)
- **Bottom tabbed section** — dashboard tabs (see below)

No Notes, Connections, Interactions, or Gifts tabs — those exist elsewhere in the app from the user's POV already.

### Tabs

| Tab | Content |
|-----|---------|
| **Overview** | Quick stats card (total contacts, interactions this month, streak); AI-generated "your social life" blurb (similar to contact summary, but aggregate) |
| **Activity** | Cross-contact interaction + activity feed, sorted by recency — your full social log in one place |
| **Relationships** | Contacts ranked by closeness score; lapsing alerts for people drifting |
| **Tasks** | All open tasks across all contacts (replaces the standalone Tasks tab) |

---

## Implementation order

1. **MeViewModel** — aggregate DAOs (no per-person scoping); expose stats, feed, closeness ranking, cross-contact task list
2. **MeScreen** — top info section (read from `personDao.getMe()`) + `ScrollableTabRow` + tab composables
3. **Navigation** — replace `Routes.TASKS` entry in `TOP_LEVEL_DESTS` with `Routes.ME`; add `MeScreen` composable to `NavHost`; handle the case where no "me" contact is set (prompt to mark one)
4. **AI blurb (Overview tab)** — extend `LlmOrchestrator` with a `summarizeMe()` that builds an aggregate facts string across all contacts and generates a paragraph

---

## Open questions

- **No "me" contact set**: show an empty-state prompt in MeScreen ("Tap ··· on a contact and choose 'This is me'"), or auto-navigate there? Probably empty state.
- **Overview AI blurb**: run on demand (button) or auto-generate periodically like contact summaries?
- **Activity tab vs Feed tab**: once MeScreen exists, the standalone Feed tab becomes redundant. Could remove Feed from nav and keep Activity only in Me. Defer until Me is built and the overlap is visible.
